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
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.resource.ChainPlan;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;
import com.mastercard.test.flow.util.Flows;

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
	private final Runnable resourceChanged = this::resourcesChanged;
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
		prepare( flows, new ChainPlan( flows, requirements ) );
	}

	/**
	 * @param flows  Selected flows in validated serial order
	 * @param chains Frozen whole-chain plan shared with serial consumption
	 */
	public void prepare( List<Flow> flows, ChainPlan chains ) {
		List<Node> planned = new ArrayList<>();
		Map<Flow, Integer> indices = new IdentityHashMap<>();
		for( int i = 0; i < flows.size(); i++ ) {
			if( indices.put( flows.get( i ), i ) != null ) {
				throw new IllegalArgumentException( "Duplicate selected Flow reference" );
			}
			planned.add( new Node( flows.get( i ), chains.requirements( i ) ) );
		}
		for( int i = 0; i < planned.size(); i++ ) {
			planned.get( i ).owner = planned.get( chains.first( i ) );
			planned.get( i ).last = chains.last( i ) == i;
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
		publicationPrecedence( flows, indices, planned );
		chainPrecedence( chains, planned );
		List<Integer> roots = new ArrayList<>();
		for( int i = 0; i < planned.size(); i++ )
			if( planned.get( i ).remaining == 0 )
				roots.add( i );
		synchronized( history ) {
			if( prepared || released || stopped != null ) {
				throw new IllegalStateException( "Flow admission cannot be prepared", stopped );
			}
			nodes = planned;
			prepared = true;
		}
		publishReady( planned, roots );
	}

	private void publishReady( List<Node> planned, List<Integer> cohort ) {
		if( cohort.isEmpty() )
			return;
		cohort.sort( Integer::compareTo );
		List<Node> owners = cohort.stream().map( planned::get ).filter( n -> n.owner == n ).toList();
		List<Request> requests = ResourceReservations.shared().register( capacity,
				owners.stream().map( n -> n.requirements ).toList(), resourceChanged );
		boolean keep;
		synchronized( history ) {
			keep = stopped == null && !released;
			if( keep ) {
				for( int i = 0; i < owners.size(); i++ )
					owners.get( i ).request = requests.get( i );
				ready.addAll( cohort );
				wake();
			}
		}
		if( !keep )
			requests.forEach( Request::cancel );
	}

	private static void precedence( List<Node> planned, int before, int after ) {
		if( planned.get( before ).successors.add( after ) )
			planned.get( after ).remaining++;
	}

	private static void chainPrecedence( ChainPlan chains, List<Node> planned ) {
		List<Set<Integer>> original = planned.stream().map( n -> Set.copyOf( n.successors ) ).toList();
		for( int i = 0; i < planned.size(); i++ ) {
			for( int successor : original.get( i ) ) {
				if( chains.first( i ) != chains.first( successor ) )
					precedence( planned, chains.last( i ), chains.first( successor ) );
			}
			if( chains.next( i ) >= 0 )
				precedence( planned, i, chains.next( i ) );
		}
		int[] remaining = planned.stream().mapToInt( n -> n.remaining ).toArray();
		Deque<Integer> ready = new ArrayDeque<>();
		for( int i = 0; i < remaining.length; i++ )
			if( remaining[i] == 0 )
				ready.add( i );
		int visited = 0;
		while( !ready.isEmpty() ) {
			visited++;
			for( int successor : planned.get( ready.removeFirst() ).successors )
				if( --remaining[successor] == 0 )
					ready.add( successor );
		}
		if( visited != planned.size() )
			throw new IllegalArgumentException( "Contradictory contracted chain precedence" );
	}

	private static void publicationPrecedence( List<Flow> flows, Map<Flow, Integer> indices,
			List<Node> planned ) {
		Map<Flow, NavigableSet<Integer>> destinations = new IdentityHashMap<>();
		Map<Message, NavigableSet<Integer>> participants = new IdentityHashMap<>();
		for( Flow flow : flows ) {
			int owner = indices.get( flow );
			Interaction root = flow.root();
			Stream<Interaction> interactions = root == null ? Stream.empty()
					: Stream.concat( Stream.of( root ), Flows.descendents( root ) );
			interactions.forEach( interaction -> {
				for( Message message : new Message[] { interaction.request(), interaction.response() } ) {
					if( message != null )
						participants.computeIfAbsent( message, m -> new TreeSet<>() ).add( owner );
				}
			} );
			flow.dependencies().filter( d -> d.source().isComplete() && d.sink().isComplete() )
					.forEach( dependency -> {
						int publisher = indices.get( dependency.source().flow() );
						destinations.computeIfAbsent( dependency.sink().flow(), f -> new TreeSet<>() )
								.add( publisher );
						dependency.source().getMessage().ifPresent( message -> participants
								.computeIfAbsent( message, m -> new TreeSet<>() ).add( publisher ) );
						dependency.sink().getMessage().ifPresent( message -> participants
								.computeIfAbsent( message, m -> new TreeSet<>() ).add( publisher ) );
					} );
		}
		// Whole-flow destination groups do not protect aliases read by another flow.
		// Include actual message users as well as publishers, in the same serial order.
		orderGroups( destinations.values(), planned );
		orderGroups( participants.values(), planned );
	}

	private static void orderGroups( Iterable<NavigableSet<Integer>> groups, List<Node> planned ) {
		for( NavigableSet<Integer> ranks : groups ) {
			Integer previous = null;
			for( int rank : ranks ) {
				if( previous != null )
					precedence( planned, previous, rank );
				previous = rank;
			}
		}
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
				if( node.owner.grant != null ) {
					admit( node, index );
					return index;
				}
				request = node.request;
			}
			Grant grant = request.tryAcquire();
			if( grant != null ) {
				synchronized( history ) {
					checkFixture();
					if( stopped == null ) {
						node.owner.grant = grant;
						node.request = null;
						admit( node, index );
						return index;
					}
				}
				grant.close(); // Never emitted, hence proven unused.
			}
		}
	}

	private void admit( Node node, int index ) {
		node.admitted = true;
		node.owner.uses++;
		ready.remove( index );
		issued++;
	}

	/**
	 * Returns a committed admission that the adapter proves never reached native
	 * handoff. Stop first; this is neither a native terminal nor processing
	 * evidence. Other live uses and explicitly retained uncertainty still own the
	 * whole grant.
	 *
	 * @param index Proven un-emitted admission
	 */
	public void unused( int index ) {
		Grant unused = null;
		synchronized( history ) {
			Node node = nodes.get( index );
			if( stopped == null || !node.admitted || node.retired || node.id != null
					|| node.started || node.entered || node.outcome != null )
				throw fault( "Flow admission is not proven unused" );
			node.retired = true;
			if( --node.owner.uses == 0 ) {
				unused = node.owner.grant;
				node.owner.grant = null;
			}
		}
		if( unused != null )
			unused.close();
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
			checkFixture();
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
	 * Retrieves existing ownership from admission through native retirement,
	 * including pre-body owner receipt delivery and post-body outer cleanup.
	 *
	 * @param index Admitted invocation index
	 * @return Whole-unit grant, still retained by native completion accounting
	 */
	public Grant reservation( int index ) {
		synchronized( history ) {
			Node node = nodes.get( index );
			if( !node.admitted || node.retired || node.owner.grant == null )
				throw fault( "Flow has no current invocation reservation" );
			return node.owner.grant;
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
				checkFixture();
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
		List<Integer> cohort = new ArrayList<>();
		List<Node> planned;
		try {
			synchronized( history ) {
				checkFixture();
				planned = nodes;
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
							cohort.add( successor );
						}
					}
				}
				finished = finishedGrant( node );
				wake();
			}
			// Publish before returning the completed parent's grant. Factories cannot
			// see a half-published cohort, and continuations keep their existing grant.
			publishReady( planned, cohort );
		}
		finally {
			if( finished != null ) {
				finished.close();
			}
			cancelPending();
		}
	}

	private Grant finishedGrant( Node node ) {
		if( !node.retired && node.outcome != null && (!node.entered || node.safe) ) {
			node.retired = true;
			node.owner.uses--;
			if( node.owner.uses == 0 && (node.last || stopped != null) ) {
				Grant grant = node.owner.grant;
				node.owner.grant = null;
				return grant;
			}
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
				checkFixture();
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
			checkFixture();
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
		checkFixture();
		if( !prepared || released || stopped != null ) {
			throw new IllegalStateException( "Flow admission is not active", stopped );
		}
	}

	private void checkFixture() {
		if( capacity.uncertainty() != null )
			stopLocked( capacity.uncertainty() );
	}

	private void resourcesChanged() {
		// Reservation callbacks arrive outside all bookkeeping locks. The capacity
		// latch also protects continuation if native completion races this callback.
		if( capacity.uncertainty() != null )
			stop( capacity.uncertainty() );
		else
			wake();
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
		List<Grant> unused = new ArrayList<>();
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
				if( node.owner == node && node.grant != null && node.uses == 0 ) {
					unused.add( node.grant );
					node.grant = null;
				}
			}
		}
		cancelled.forEach( Request::cancel );
		unused.forEach( Grant::close );
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
		private Node owner;
		private int uses;
		private boolean last;
		private boolean retired;
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
