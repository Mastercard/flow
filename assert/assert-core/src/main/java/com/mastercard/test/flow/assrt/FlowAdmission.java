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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.time.Duration;
import java.util.Optional;
import java.util.LinkedHashSet;
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
	private final StopBudget budget = new StopBudget();
	private final LongSupplier nanoTime;
	private ExecutionStatus.StopBudgetMiss budgetMiss;
	private final Set<Node> retainedOwners = new LinkedHashSet<>();
	private Thread cancellationThread;
	private Thread cleanupThread;
	private Thread receiptThread;
	private final ResourceReservations.Capacity capacity;
	private final long cancellationRecheckMillis;
	private final Runnable resourceChanged = this::resourcesChanged;
	private Thread factoryThread = Thread.currentThread();
	private List<Node> nodes = List.of();
	private final Map<String, Integer> nativeIds = new HashMap<>();
	private final NavigableSet<Integer> ready = new TreeSet<>();
	private boolean prepared;
	private boolean streamClosed;
	private boolean released;
	private boolean pendingCancelled;
	private boolean cancelling;
	private Outcome factoryOutcome;
	private Throwable factoryFailure;
	private boolean factoryEnded;
	private Throwable stopped;
	private int issued;
	private int terminal;
	private int active;
	private int retired;
	private Runnable drained;
	private int selected;
	private int entered;
	private int completed;
	private boolean incomplete;
	private List<String> affected = List.of();
	private long changes;
	private long successorVisits;
	private volatile BooleanSupplier cancellation;
	private boolean cancellationBound;
	/** Readiness work counters, guarded by History; not processing status. */
	long resourceAttempts;
	/** Wait returns without a readiness change, guarded by History. */
	long unchangedWakes;
	/** Wait returns that observe a readiness change, guarded by History. */
	long eventWakes;

	/** @param limit Maximum outstanding whole-set grants, not idle workers */
	public FlowAdmission( int limit ) {
		this( limit, 250 );
	}

	/** Controls only the existing wait interval, never an execution deadline. */
	FlowAdmission( int limit, long cancellationRecheckMillis ) {
		this( limit, cancellationRecheckMillis, System::nanoTime );
	}

	/** Test clock at the existing admission seam, not a public clock API. */
	FlowAdmission( int limit, LongSupplier nanoTime ) {
		this( limit, 250, nanoTime );
	}

	private FlowAdmission( int limit, long cancellationRecheckMillis, LongSupplier nanoTime ) {
		this.nanoTime = Objects.requireNonNull( nanoTime );
		if( cancellationRecheckMillis <= 0 )
			throw new IllegalArgumentException( "Cancellation recheck must be positive" );
		this.cancellationRecheckMillis = cancellationRecheckMillis;
		capacity = ResourceReservations.shared().capacity( limit );
	}

	/** @param duration Stop budget configured before preparation or Stop */
	public void stopBudget( Duration duration ) {
		synchronized( history ) {
			if( prepared || released || stopped != null )
				throw new IllegalStateException( "Flow stop budget is fixed" );
			budget.configure( duration );
		}
	}

	private void observeBudget() {
		if( budget.open() && budgetMiss == null && budget.observe( nanoTime.getAsLong() ) ) {
			incomplete = true;
			budgetMiss = new ExecutionStatus.StopBudgetMiss( budget.duration(), budget.elapsed(), stopped,
					active, (int) nodes.stream().filter( n -> n.admitted && !n.retired
							&& n.outcome == null && n.skipped == null && !n.covered ).count(),
					0, released ? 0 : 1, capacity.owned(), capacity.operations(), capacity.callbacks(),
					cancelling, affectedIdentities() );
		}
	}

	private List<String> affectedIdentities() {
		return Stream
				.concat( retainedOwners.stream(), nodes.stream().filter( n -> !n.safe || !n.retired ) )
				.distinct()
				.limit( 5 ).map( n -> n.label ).toList();
	}

	/**
	 * Binds the optional native execute-call query before preparation. An absent
	 * channel stays absent and does not introduce timed readiness wakes.
	 *
	 * @param query Native cancellation query, or null on baseline runtimes
	 */
	public void cancellationQuery( BooleanSupplier query ) {
		synchronized( history ) {
			if( cancellationBound || prepared || released || stopped != null )
				throw new IllegalStateException( "Flow cancellation query is already fixed" );
			cancellationBound = true;
			cancellation = query;
		}
	}

	private void observeCancellation() {
		BooleanSupplier query = cancellation;
		if( query == null )
			return;
		synchronized( history ) {
			if( stopped != null || released )
				return;
		}
		if( query.getAsBoolean() )
			stop( new IllegalStateException( "Native Flow cancellation requested" ) );
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
			selected = planned.size();
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
			effects( null, requests.stream().<Runnable>map( r -> r::cancel ).toArray( Runnable[]::new ) );
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
		return next( validate, () -> {
		} );
	}

	/**
	 * Waits only after giving the native adapter one opportunity to execute work
	 * already emitted by this factory.
	 *
	 * @param validate Adapter's actual pool/attachment check, outside all locks
	 * @param progress Adapter's cooperative native progress action
	 * @return Admitted index or {@link #EXHAUSTED}
	 */
	public int next( Runnable validate, Runnable progress ) {
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
			progress.run();
			synchronized( history ) {
				checkActive();
				if( changes != observed )
					continue;
			}
			// Unchanged timer/spurious wakes never revalidate or retry the ready set.
			for( ;; ) {
				observeCancellation();
				synchronized( history ) {
					if( stopped != null )
						throw new IllegalStateException( "Flow admission is not active", stopped );
					if( changes != observed )
						break;
					try {
						if( cancellation == null )
							history.wait();
						else
							history.wait( cancellationRecheckMillis );
						if( changes == observed )
							unchangedWakes++;
						else
							eventWakes++;
					}
					catch( InterruptedException failure ) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException( "Flow readiness interrupted", failure );
					}
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
		observeCancellation();
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
					if( node.owner.grant.pending() )
						continue;
					admit( node, index );
					return index;
				}
				request = node.request;
				resourceAttempts++;
			}
			Grant grant = request.tryAcquire();
			if( grant != null ) {
				synchronized( history ) {
					checkFixture();
					if( stopped == null ) {
						node.owner.grant = grant;
						retainedOwners.add( node.owner );
						node.request = null;
						admit( node, index );
						return index;
					}
				}
				settle( null, grant ); // Never emitted, hence proven unused.
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
			observeBudget();
			Node node = nodes.get( index );
			if( stopped == null || !node.admitted || node.retired || node.id != null
					|| node.started || node.entered || node.outcome != null )
				throw fault( "Flow admission is not proven unused" );
			node.retired = true;
			retired++;
			if( --node.owner.uses == 0 ) {
				unused = node.owner.grant;
			}
		}
		settle( null, unused );
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
			if( node.skipped != null || node.covered || node.outcome != null && !node.started ) {
				throw fault( "Native Flow start after terminal" );
			}
			node.started = true;
			node.nativeThread = Thread.currentThread();
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
		observeCancellation();
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
			node.bodyThread = Thread.currentThread();
			entered++;
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
	 * Delivers pre-handoff ownership on the factory stack, outside bookkeeping
	 * locks. A nested close cannot await native proof that requires this callback
	 * to return. Delivery itself supplies no body, native or unused proof.
	 *
	 * @param index    Admitted invocation, not yet handed to native execution
	 * @param delivery Short nonthrowing ownership receipt callback
	 */
	public void receipt( int index, Consumer<Grant> delivery ) {
		Grant grant;
		synchronized( history ) {
			if( Thread.currentThread() != factoryThread || receiptThread != null )
				throw new IllegalStateException( "Flow receipt delivery is outside its factory" );
			grant = reservation( index );
			receiptThread = Thread.currentThread();
		}
		try {
			delivery.accept( grant );
		}
		finally {
			synchronized( history ) {
				receiptThread = null;
				wake();
			}
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
		Throwable primary = null;
		try {
			synchronized( history ) {
				observeBudget();
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
				node.bodyThread = null;
				completed++;
				active--;
				finished = finishedGrant( node );
				wake();
			}
		}
		catch( RuntimeException | Error thrown ) {
			primary = thrown;
			throw thrown;
		}
		finally {
			settle( primary, finished );
		}
	}

	/**
	 * @param id      Native identity
	 * @param outcome Actual outcome
	 * @param failure Actual failure
	 */
	public void finished( String id, Outcome outcome, Throwable failure ) {
		Objects.requireNonNull( outcome );
		Grant finished = null;
		Throwable primary = null;
		List<Integer> cohort = new ArrayList<>();
		List<Node> planned;
		try {
			synchronized( history ) {
				observeBudget();
				checkFixture();
				planned = nodes;
				Node node = nodes.get( index( id ) );
				if( node.skipped != null )
					throw fault( "Native finish conflicts with skip" );
				if( node.outcome != null ) {
					if( node.outcome != outcome || node.nativeFailure != failure ) {
						throw fault( "Conflicting native Flow terminal" );
					}
					return;
				}
				node.outcome = outcome;
				node.nativeThread = null;
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
		catch( RuntimeException | Error thrown ) {
			primary = thrown;
			throw thrown;
		}
		finally {
			settle( primary, finished );
		}
	}

	private Grant finishedGrant( Node node ) {
		if( !node.retired && (node.outcome != null || node.skipped != null || node.covered)
				&& (!node.entered || node.safe) ) {
			node.retired = true;
			retired++;
			node.owner.uses--;
			if( node.owner.uses == 0 && (node.last || stopped != null) ) {
				Grant grant = node.owner.grant;
				return grant;
			}
		}
		return null;
	}

	/**
	 * Records an actual skipped leaf without inventing a native outcome or History.
	 *
	 * @param id     Registered native identity
	 * @param reason Actual native skip reason
	 */
	public void skipped( String id, String reason ) {
		Objects.requireNonNull( reason );
		Grant unused = null;
		Throwable primary = null;
		try {
			synchronized( history ) {
				observeBudget();
				Node node = nodes.get( index( id ) );
				if( node.started || node.entered || node.outcome != null
						|| node.skipped != null && !node.skipped.equals( reason ) )
					throw fault( "Conflicting native Flow skip" );
				if( node.skipped != null )
					return;
				node.skipped = reason;
				stopLocked( new IllegalStateException( "Native Flow skipped: " + reason ) );
				unused = finishedGrant( node );
			}
		}
		catch( RuntimeException | Error thrown ) {
			primary = thrown;
			throw thrown;
		}
		finally {
			settle( primary, unused );
		}
	}

	/**
	 * Actual enclosing terminal/skip proves the native scope ended, not individual
	 * descendant statuses or processing. Entered work and operations still drain.
	 *
	 * @param cause Diagnostic from the exact enclosing native scope
	 */
	public void enclosingFinished( Throwable cause ) {
		Objects.requireNonNull( cause );
		List<Grant> unused = new ArrayList<>();
		synchronized( history ) {
			observeBudget();
			if( factoryEnded )
				return;
			factoryEnded = true;
			incomplete = true;
			stopLocked( cause );
			for( Node node : nodes ) {
				if( node.admitted && !node.retired ) {
					node.covered = true;
					node.nativeThread = null;
					Grant grant = finishedGrant( node );
					if( grant != null )
						unused.add( grant );
				}
			}
		}
		effects( null, this::cancelPending,
				() -> closeGrants( unused ), this::drain );
	}

	/** Records enumeration close, which is not native completion. */
	public void enumerationClosed() {
		synchronized( history ) {
			streamClosed = true;
			if( !prepared || issued != nodes.size() || stopped != null ) {
				incomplete = true;
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
		Objects.requireNonNull( outcome );
		Throwable primary = null;
		try {
			synchronized( history ) {
				observeBudget();
				checkFixture();
				if( factoryOutcome != null ) {
					if( factoryOutcome != outcome || factoryFailure != failure ) {
						throw fault( "Conflicting native Flow factory terminal" );
					}
					return false;
				}
				factoryOutcome = outcome;
				factoryFailure = failure;
				factoryEnded = true;
				// A factory can finish with filtered dynamic descriptions and no child
				// callback. Cover only native scope lifetime, never missing outcomes.
				for( Node node : nodes )
					if( node.admitted && !node.retired && node.outcome == null && node.skipped == null ) {
						node.covered = true;
						node.nativeThread = null;
					}
				boolean complete = prepared && streamClosed && issued == nodes.size() && terminal == issued
						&& active == 0 && capacity.owned() == 0 && stopped == null
						&& outcome == Outcome.SUCCESSFUL;
				if( !complete ) {
					incomplete = true;
					stopLocked( new IllegalStateException( "Incomplete native Flow factory", failure ) );
				}
				return complete;
			}
		}
		catch( RuntimeException | Error thrown ) {
			primary = thrown;
			throw thrown;
		}
		finally {
			effects( primary, this::cancelPending, this::retireFinished, this::drain );
		}
	}

	private void retireFinished() {
		List<Grant> unused = new ArrayList<>();
		synchronized( history ) {
			for( Node node : nodes ) {
				Grant grant = finishedGrant( node );
				if( grant != null )
					unused.add( grant );
			}
		}
		closeGrants( unused );
	}

	private static void closeGrants( List<Grant> grants ) {
		effects( null, grants.stream().<Runnable>map( g -> g::close ).toArray( Runnable[]::new ) );
	}

	private void settle( Throwable primary, Grant finished ) {
		effects( primary, () -> {
			if( finished != null )
				finished.close();
		}, this::cancelPending, this::drain );
	}

	// State changes already committed under History cannot be rolled back when a
	// notification fails. Attempt every outside-lock effect, preserving its caller.
	private static void effects( Throwable primary, Runnable... actions ) {
		Throwable first = primary;
		for( Runnable action : actions ) {
			try {
				action.run();
			}
			catch( RuntimeException | Error failure ) {
				if( first == null )
					first = failure;
				else if( first != failure )
					first.addSuppressed( failure );
			}
		}
		if( primary == null ) {
			if( first instanceof RuntimeException failure )
				throw failure;
			if( first instanceof Error failure )
				throw failure;
		}
	}

	/**
	 * @return Whether actual evidence permits disposal, not successful reporting
	 */
	public boolean disposable() {
		synchronized( history ) {
			return !cancelling && active == 0 && capacity.owned() == 0 && issued == retired;
		}
	}

	/**
	 * Registers the adapter's terminal disposal once. Late exact operation proof
	 * may invoke it after the Launcher returns; no native result is replayed.
	 *
	 * @param action Cleanup performed outside admission and reservation locks
	 */
	public void whenDrained( Runnable action ) {
		synchronized( history ) {
			if( drained != null || released )
				throw new IllegalStateException( "Flow disposal already registered" );
			drained = Objects.requireNonNull( action );
		}
		drain();
	}

	private void drain() {
		Runnable action = null;
		synchronized( history ) {
			observeBudget();
			if( factoryEnded && disposable() ) {
				action = drained;
				drained = null;
				if( action != null )
					cleanupThread = Thread.currentThread();
			}
		}
		if( action != null ) {
			try {
				action.run();
			}
			finally {
				synchronized( history ) {
					observeBudget();
					cleanupThread = null;
					wake();
				}
			}
		}
	}

	/** @return The first stop cause, independently of native outcomes */
	public Throwable stopCause() {
		synchronized( history ) {
			return stopped;
		}
	}

	/** @return A bounded snapshot, retaining no Flow, native context or Writer */
	public ExecutionStatus status() {
		synchronized( history ) {
			observeBudget();
			return new ExecutionStatus( released ? ExecutionStatus.State.QUIESCENT
					: stopped == null ? ExecutionStatus.State.ACTIVE : ExecutionStatus.State.STOPPING,
					stopped, incomplete, selected, issued, entered, completed, terminal, capacity.owned(),
					released ? affected : affectedIdentities(), Optional.ofNullable( budgetMiss ) );
		}
	}

	/** Detaches only safely disposable references, also after a stopped run. */
	public void release() {
		Throwable primary = null;
		try {
			synchronized( history ) {
				checkFixture();
				// No issued leaves is not terminal evidence: prepared roots can still
				// own pending priority gates. Only a completed Stop permits unused disposal.
				if( !disposable() || stopped != null && !pendingCancelled
						|| !factoryEnded && (stopped == null || issued != 0) ) {
					throw fault( "Flow admission is not safely complete" );
				}
				affected = status().affected();
				budget.finish();
				released = true;
				nodes = List.of();
				retainedOwners.clear();
				drained = null;
				nativeIds.clear();
				ready.clear();
				history.clear();
				factoryThread = null;
				cancellation = null;
			}
		}
		catch( RuntimeException | Error thrown ) {
			primary = thrown;
			throw thrown;
		}
		finally {
			settle( primary, null );
		}
	}

	/** @param failure First cause that irreversibly closes admission */
	public void stop( Throwable failure ) {
		Objects.requireNonNull( failure );
		synchronized( history ) {
			if( released )
				return;
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
		InterruptedException interrupted = null;
		synchronized( history ) {
			for( ;; ) {
				observeBudget();
				// Native enclosing terminal can follow this close. Only independently
				// drainable use/cleanup warrants waiting; never await our own stack.
				// Final capacity proof can precede the notification claiming registered
				// cleanup. That cleanup is still required work, even before it has a thread.
				if( released || disposable() && cleanupThread == null && (drained == null || !factoryEnded)
						|| budgetMiss != null
						|| cancellationThread == Thread.currentThread()
						|| cleanupThread == Thread.currentThread()
						|| receiptThread == Thread.currentThread()
						|| nodes.stream().anyMatch( n -> n.bodyThread == Thread.currentThread()
								|| n.nativeThread == Thread.currentThread() ) )
					break;
				long remaining = budget.remaining();
				try {
					history.wait( remaining / 1_000_000, (int) (remaining % 1_000_000) );
				}
				catch( InterruptedException failure ) {
					Thread.currentThread().interrupt();
					interrupted = failure;
					break;
				}
			}
			incomplete = true;
		}
		ExecutionStatus snapshot = status();
		var failure = new IllegalStateException( "Incomplete Flow parallel admission: "
				+ (interrupted == null ? "" : "drain wait interrupted; ")
				+ snapshot.stopBudgetMiss().map( miss -> "budget missed; evidence observed after "
						+ miss.elapsed() + ": " + miss + "; " ).orElse( "" )
				+ "issued=" + snapshot.admitted() + ", terminal=" + snapshot.nativeTerminals()
				+ "; no forced release or finalization", snapshot.cause() );
		if( interrupted != null )
			failure.addSuppressed( interrupted );
		throw failure;
	}

	/**
	 * @return Direct-successor visits only, excluding planning and resource retries
	 */
	long successorVisits() {
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
		synchronized( history ) {
			// Reservation proof already changed capacity. Observe the still-open
			// budget before accepting that proof or discarding its identity.
			observeBudget();
			retainedOwners.removeIf( node -> {
				if( node.grant.released() ) {
					node.grant = null;
					return true;
				}
				return false;
			} );
		}
		// Reservation callbacks arrive outside all bookkeeping locks. The capacity
		// latch also protects continuation if native completion races this callback.
		effects( null, () -> {
			if( capacity.uncertainty() != null )
				stop( capacity.uncertainty() );
			else
				wake();
		}, this::drain );
	}

	private IllegalStateException fault( String message ) {
		IllegalStateException failure = new IllegalStateException( message, stopped );
		stopLocked( failure );
		return failure;
	}

	private void stopLocked( Throwable failure ) {
		observeBudget();
		if( stopped == null ) {
			stopped = failure;
			budget.start( nanoTime.getAsLong() );
		}
		cancellation = null;
		incomplete = true;
		ready.clear();
		wake();
	}

	private void cancelPending() {
		List<Request> cancelled = new ArrayList<>();
		List<Grant> unused = new ArrayList<>();
		Throwable cause;
		synchronized( history ) {
			if( stopped == null || pendingCancelled ) {
				return;
			}
			// Stop prevents retaining new requests, so one sweep also covers later
			// drainage without scanning every node again on each completion.
			pendingCancelled = true;
			cancelling = true;
			cancellationThread = Thread.currentThread();
			cause = stopped;
			for( Node node : nodes ) {
				if( node.request != null ) {
					cancelled.add( node.request );
					node.request = null;
				}
				if( node.owner == node && node.grant != null ) {
					if( node.uses == 0 ) {
						unused.add( node.grant );
					}
				}
			}
		}
		effects( null,
				() -> effects( null,
						cancelled.stream().<Runnable>map( r -> r::cancel ).toArray( Runnable[]::new ) ),
				() -> capacity.stopping( cause ), () -> closeGrants( unused ), () -> {
					synchronized( history ) {
						observeBudget();
						cancelling = false;
						cancellationThread = null;
						wake();
					}
					// Proof can precede an extracted grant's close. Registering terminal
					// disposal checks the other order; this check covers completed effects.
					drain();
				} );
	}

	private void wake() {
		synchronized( history ) {
			changes++;
			history.notifyAll();
		}
	}

	private static final class Node {
		private final Flow flow;
		private final String label;
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
		private Thread bodyThread;
		private Thread nativeThread;
		private boolean safe;
		private Result processing;
		private Throwable failure;
		private Outcome outcome;
		private Throwable nativeFailure;
		private String skipped;
		private boolean covered;

		private Node( Flow flow, ResourceRequirements requirements ) {
			this.flow = flow;
			String id = flow.meta().id();
			label = id.length() > 120 ? id.substring( 0, 117 ) + "..." : id;
			this.requirements = requirements;
		}
	}
}
