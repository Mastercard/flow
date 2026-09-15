package com.mastercard.test.flow.assrt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;

/**
 * Internal prepared-run admission shared by native adapters. The existing
 * History is also the short-held run monitor; model calls, resource operations
 * and native emission always occur outside it. Only the preparing thread may
 * await work. This is not a body executor or an alternative processing
 * implementation.
 */
public final class FlowAdmission {
	/** No currently admissible work, but enumeration is not complete. */
	public static final int WAITING = -1;
	/** Every selected identity has already been admitted. */
	public static final int EXHAUSTED = -2;

	/** Actual native evidence, never a replacement for Flow History. */
	public enum Outcome {
		/** Native successful completion. */
		SUCCESSFUL,
		/** Native failed completion. */
		FAILED,
		/** Native aborted completion. */
		ABORTED
	}

	private final History history = new History();
	private final ResourceReservations.Capacity capacity;
	private final Runnable resourceChanged = this::wake;
	private Thread factoryThread = Thread.currentThread();
	private List<Node> nodes = List.of();
	private final Map<String, Integer> nativeIds = new HashMap<>();
	private final NavigableSet<Integer> ready = new TreeSet<>();
	private boolean prepared;
	private boolean streamClosed;
	private boolean released;
	private boolean pendingCancelled;
	private Outcome factoryOutcome;
	private Throwable factoryFailure;
	private Throwable stopped;
	private int issued;
	private int terminal;
	private int active;
	private long changes;
	private long successorVisits;

	/** @param limit Maximum outstanding whole-set grants, not idle workers */
	public FlowAdmission( int limit ) {
		capacity = ResourceReservations.shared().capacity( limit );
	}

	/** @return The same History used by actual processing and admission */
	public History history() {
		return history;
	}

	/**
	 * Builds direct precedence once from validated canonical order. Comparable
	 * selected basis ancestors/descendants keep that order, even across absent
	 * ancestors or inverted ranks. This neither selects more flows nor changes
	 * History eligibility.
	 *
	 * @param flows        Selected flows in validated serial order
	 * @param requirements Stored whole-set requirements in the same order
	 */
	public void prepare( List<Flow> flows, List<ResourceRequirements> requirements ) {
		List<Node> planned = new ArrayList<>();
		Map<Flow, Integer> indices = new IdentityHashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			if( indices.put( flows.get( i ), i ) != null ) {
				throw new IllegalArgumentException( "Duplicate selected Flow reference" );
			}
			planned.add( new Node( flows.get( i ), requirements.get( i ) ) );
		}
		for( int i = 0; i < flows.size(); i++ ) {
			Flow flow = flows.get( i );
			int index = i;
			flow.dependencies().map( d -> d.source().flow() )
					.filter( f -> f != null && f != flow ).forEach( source -> {
						Integer before = indices.get( source );
						if( before == null || before >= index ) {
							throw new IllegalArgumentException( "Absent or noncanonical Flow prerequisite" );
						}
						precedence( planned, before, index );
					} );
		}
		basisPrecedence( flows, indices, planned );
		synchronized( history ) {
			if( prepared || released || stopped != null ) {
				throw new IllegalStateException( "Flow admission cannot be prepared", stopped );
			}
			nodes = planned;
			for( int i = 0; i < nodes.size(); i++ ) {
				if( nodes.get( i ).remaining == 0 ) {
					ready.add( i );
				}
			}
			prepared = true;
		}
	}

	private static void precedence( List<Node> planned, int before, int after ) {
		if( planned.get( before ).successors.add( after ) )
			planned.get( after ).remaining++;
	}

	private static void basisPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Node> planned ) {
		List<List<Integer>> children = new ArrayList<>();
		List<Integer> roots = new ArrayList<>();
		for( int i = 0; i < flows.size(); i++ )
			children.add( new ArrayList<>() );
		// Cache the nearest selected ancestor across shared unselected paths. Selected
		// identities are stopping points, but each one's own basis is still read once.
		Map<Flow, Integer> nearest = new IdentityHashMap<>( indices );
		for( int i = 0; i < flows.size(); i++ ) {
			Flow ancestor = flows.get( i ).basis();
			List<Flow> path = new ArrayList<>();
			while( ancestor != null && !nearest.containsKey( ancestor ) ) {
				nearest.put( ancestor, -2 ); // Unresolved on this path: a repeat is a cycle.
				path.add( ancestor );
				ancestor = ancestor.basis();
			}
			int parent = ancestor == null ? -1 : nearest.get( ancestor );
			if( parent == -2 )
				throw new IllegalArgumentException( "Cyclic Flow basis" );
			for( Flow absent : path )
				nearest.put( absent, parent );
			if( parent < 0 )
				roots.add( i );
			else
				children.get( parent ).add( i );
		}
		NavigableSet<Integer> ancestry = new TreeSet<>();
		Deque<Integer> traversal = new ArrayDeque<>( roots );
		int visited = 0;
		while( !traversal.isEmpty() ) {
			int index = traversal.removeLast();
			if( index < 0 ) {
				ancestry.remove( ~index );
				continue;
			}
			visited++;
			Integer before = ancestry.lower( index );
			Integer after = ancestry.higher( index );
			// Inserting a canonical rank into the ordered ancestral path needs at most
			// two forward edges. Older redundant edges may stay: still at most 2V.
			if( before != null )
				precedence( planned, before, index );
			if( after != null )
				precedence( planned, index, after );
			ancestry.add( index );
			traversal.addLast( ~index ); // Exit marker removes the rank before a sibling.
			traversal.addAll( children.get( index ) );
		}
		if( visited != flows.size() )
			throw new IllegalArgumentException( "Cyclic Flow basis" );
	}

	/**
	 * Waits only on the preparing factory thread, never on a flow worker.
	 *
	 * @param validate Adapter's actual pool/attachment check, outside all locks
	 * @return Admitted index or {@link #EXHAUSTED}
	 */
	public int next( Runnable validate ) {
		if( Thread.currentThread() != factoryThread ) {
			throw new IllegalStateException( "Only the actual Flow factory may await readiness" );
		}
		for( ;; ) {
			validate.run();
			long observed;
			synchronized( history ) {
				checkActive();
				observed = changes;
			}
			int next = poll();
			if( next != WAITING ) {
				return next;
			}
			synchronized( history ) {
				checkActive();
				try {
					if( changes == observed ) {
						history.wait();
					}
				}
				catch( InterruptedException failure ) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException( "Flow readiness interrupted", failure );
				}
			}
		}
	}

	/**
	 * Nonblocking whole-set admission for the preparing thread. The returned index
	 * is committed before the caller emits anything, including buffered/inline use.
	 * Resource retry work is separate from direct-successor bookkeeping.
	 *
	 * @return Admitted index, {@link #WAITING}, or {@link #EXHAUSTED}
	 */
	public int poll() {
		if( Thread.currentThread() != factoryThread ) {
			throw new IllegalStateException( "Flow admission is outside its factory" );
		}
		for( int previous = -1;; ) {
			Integer index;
			Node node;
			Request request;
			synchronized( history ) {
				checkActive();
				if( issued == nodes.size() ) {
					return EXHAUSTED;
				}
				index = ready.higher( previous );
				if( index == null ) {
					return WAITING;
				}
				previous = index;
				node = nodes.get( index );
				request = node.request;
			}
			if( request == null ) {
				request = ResourceReservations.shared().register( capacity, node.requirements,
						resourceChanged );
				boolean keep;
				synchronized( history ) {
					keep = stopped == null;
					if( keep ) {
						node.request = request;
					}
				}
				if( !keep ) {
					request.cancel();
					continue;
				}
			}
			Grant grant = request.tryAcquire();
			if( grant != null ) {
				synchronized( history ) {
					if( stopped == null ) {
						node.grant = grant;
						node.request = null;
						node.admitted = true;
						ready.remove( index );
						issued++;
						return index;
					}
				}
				grant.close(); // Never emitted, hence proven unused.
			}
		}
	}

	/**
	 * @param index Admitted index
	 * @param id    Validated native unique identity
	 */
	public void registered( int index, String id ) {
		synchronized( history ) {
			Node node = nodes.get( index );
			Integer existing = nativeIds.get( id );
			if( !node.admitted || id == null || node.id != null && !node.id.equals( id )
					|| existing != null && existing != index ) {
				throw fault( "Conflicting native Flow registration" );
			}
			node.id = id;
			nativeIds.put( id, index );
		}
	}

	/**
	 * @param id Native identity
	 * @return Its admitted preparation index
	 */
	public int index( String id ) {
		synchronized( history ) {
			Integer index = nativeIds.get( id );
			if( index == null ) {
				throw fault( "Unbound native Flow" );
			}
			return index;
		}
	}

	/** @param id The native identity whose start was observed */
	public void started( String id ) {
		synchronized( history ) {
			Node node = nodes.get( index( id ) );
			if( node.outcome != null && !node.started ) {
				throw fault( "Native Flow start after terminal" );
			}
			node.started = true;
		}
	}

	/**
	 * Commits the pre-use gate, separately from native start.
	 * 
	 * @param index Owned executable index
	 * @param id    Validated native invocation identity
	 * @return False when stopped; the adapter must take its genuine abort path
	 */
	public boolean enter( int index, String id ) {
		synchronized( history ) {
			if( stopped != null ) {
				return false;
			}
			checkActive();
			Node node = nodes.get( index );
			if( index( id ) != index || !node.started || node.entered || node.outcome != null ) {
				throw fault( "Flow executable differs from its owned native binding" );
			}
			node.entered = true;
			active++;
			return true;
		}
	}

	/**
	 * Publishes actual processing after synchronous bindings/capture/callbacks end.
	 * Null classification is fatal/incomplete evidence, never SUCCESS.
	 * 
	 * @param index   Entered invocation
	 * @param result  Genuine Flow classification
	 * @param failure Its primary failure, if any
	 */
	public void processed( int index, Result result, Throwable failure ) {
		Grant finished = null;
		try {
			synchronized( history ) {
				Node node = nodes.get( index );
				if( !node.entered || node.safe && (node.processing != result || node.failure != failure) ) {
					throw fault( "Conflicting Flow processing evidence" );
				}
				if( node.safe ) {
					return;
				}
				node.processing = result;
				node.failure = failure;
				if( result != null ) {
					history.recordResult( node.flow, result );
				}
				else {
					stopLocked(
							failure == null ? new IllegalStateException( "Missing Flow processing outcome" )
									: failure );
				}
				node.safe = true;
				active--;
				finished = finishedGrant( node );
				wake();
			}
		}
		finally {
			if( finished != null ) {
				finished.close();
			}
			cancelPending();
		}
	}

	/**
	 * @param id      Native identity
	 * @param outcome Actual outcome
	 * @param failure Actual failure
	 */
	public void finished( String id, Outcome outcome, Throwable failure ) {
		Grant finished = null;
		try {
			synchronized( history ) {
				Node node = nodes.get( index( id ) );
				if( node.outcome != null ) {
					if( node.outcome != outcome || node.nativeFailure != failure ) {
						throw fault( "Conflicting native Flow terminal" );
					}
					return;
				}
				node.outcome = outcome;
				node.nativeFailure = failure;
				terminal++;
				if( !node.entered || !node.safe ) {
					stopLocked(
							new IllegalStateException( "Native terminal without drained Flow processing" ) );
				}
				else if( stopped == null ) {
					for( int successor : node.successors ) {
						successorVisits++;
						if( --nodes.get( successor ).remaining == 0 ) {
							ready.add( successor );
						}
					}
				}
				finished = finishedGrant( node );
				wake();
			}
		}
		finally {
			if( finished != null ) {
				finished.close();
			}
			cancelPending();
		}
	}

	private static Grant finishedGrant( Node node ) {
		if( node.outcome != null && (!node.entered || node.safe) ) {
			Grant grant = node.grant;
			node.grant = null;
			return grant;
		}
		return null;
	}

	/** Records enumeration close, which is not native completion. */
	public void enumerationClosed() {
		synchronized( history ) {
			streamClosed = true;
			if( !prepared || issued != nodes.size() || stopped != null ) {
				throw fault( "Incomplete Flow parallel enumeration" );
			}
		}
	}

	/**
	 * Claims normal finalization once; the adapter performs cleanup outside locks.
	 * 
	 * @param outcome Actual factory outcome
	 * @param failure Actual factory failure
	 * @return Whether normal finalization is now owned by this caller
	 */
	public boolean factoryFinished( Outcome outcome, Throwable failure ) {
		try {
			synchronized( history ) {
				if( factoryOutcome != null ) {
					if( factoryOutcome != outcome || factoryFailure != failure ) {
						throw fault( "Conflicting native Flow factory terminal" );
					}
					return false;
				}
				factoryOutcome = outcome;
				factoryFailure = failure;
				boolean complete = prepared && streamClosed && issued == nodes.size() && terminal == issued
						&& active == 0 && stopped == null && outcome == Outcome.SUCCESSFUL;
				if( !complete ) {
					stopLocked( new IllegalStateException( "Incomplete native Flow factory", failure ) );
				}
				return complete;
			}
		}
		finally {
			cancelPending();
		}
	}

	/** Detaches only after successful owned finalization. */
	public void release() {
		synchronized( history ) {
			if( factoryOutcome != Outcome.SUCCESSFUL || stopped != null || active != 0 ) {
				throw fault( "Flow admission is not safely complete" );
			}
			released = true;
			nodes = List.of();
			nativeIds.clear();
			history.clear();
			factoryThread = null;
		}
	}

	/** @param failure First cause that irreversibly closes admission */
	public void stop( Throwable failure ) {
		synchronized( history ) {
			stopLocked( failure );
		}
		cancelPending();
	}

	/** Exceptional backstop; never forces live grants to be released. */
	public void close() {
		synchronized( history ) {
			if( released && stopped == null ) {
				return;
			}
		}
		stop( new IllegalStateException( "Flow parallel class backstop reached" ) );
		synchronized( history ) {
			throw new IllegalStateException( "Incomplete Flow parallel admission: prepared=" + prepared
					+ ", issued=" + issued + ", terminal=" + terminal + ", active=" + active
					+ "; no forced release or finalization", stopped );
		}
	}

	/**
	 * @return Direct-successor visits only, excluding planning and resource retries
	 */
	public long successorVisits() {
		synchronized( history ) {
			return successorVisits;
		}
	}

	private void checkActive() {
		if( !prepared || released || stopped != null ) {
			throw new IllegalStateException( "Flow admission is not active", stopped );
		}
	}

	private IllegalStateException fault( String message ) {
		IllegalStateException failure = new IllegalStateException( message, stopped );
		stopLocked( failure );
		return failure;
	}

	private void stopLocked( Throwable failure ) {
		if( stopped == null ) {
			stopped = failure;
		}
		ready.clear();
		wake();
	}

	private void cancelPending() {
		List<Request> cancelled = new ArrayList<>();
		synchronized( history ) {
			if( stopped == null || pendingCancelled ) {
				return;
			}
			// Stop prevents retaining new requests, so one sweep also covers later
			// drainage without scanning every node again on each completion.
			pendingCancelled = true;
			for( Node node : nodes ) {
				if( node.request != null ) {
					cancelled.add( node.request );
					node.request = null;
				}
			}
		}
		cancelled.forEach( Request::cancel );
	}

	private void wake() {
		synchronized( history ) {
			changes++;
			history.notifyAll();
		}
	}

	private static final class Node {
		private final Flow flow;
		private final ResourceRequirements requirements;
		private final Set<Integer> successors = new HashSet<>();
		private int remaining;
		private Request request;
		private Grant grant;
		private String id;
		private boolean admitted;
		private boolean started;
		private boolean entered;
		private boolean safe;
		private Result processing;
		private Throwable failure;
		private Outcome outcome;
		private Throwable nativeFailure;

		private Node( Flow flow, ResourceRequirements requirements ) {
			this.flow = flow;
			this.requirements = requirements;
		}
	}
}
