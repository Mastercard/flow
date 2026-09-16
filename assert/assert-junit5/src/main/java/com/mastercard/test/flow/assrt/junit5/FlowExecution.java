package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.TestAbortedException;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.History;
import com.mastercard.test.flow.assrt.ExecutionStatus;
import com.mastercard.test.flow.assrt.StopBudget;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.resource.ChainPlan;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;

/** Model-free factory-local handle supplied by {@link FlowTest}. */
@SuppressWarnings("deprecation") // Stable ordinary store lifecycle, including Jupiter 5.10.
public final class FlowExecution implements CloseableResource, AutoCloseable {
	private Thread factoryThread;
	private boolean configuring;
	private boolean attached;
	private PreparedFlocessor runner;
	private List<DynamicNode> descriptions;
	private Stream<?> originalStream;
	private boolean live;
	private boolean active;
	private boolean exhausted;
	private boolean broken;
	private int issued;
	private int drained;
	private boolean released;
	private Runnable removeBackstop;
	private FlowParallelOwner parallelOwner;
	private final Object resourceWake = new Object();
	private final StopBudget budget = new StopBudget();
	private final LongSupplier nanoTime;
	private ExecutionStatus.StopBudgetMiss budgetMiss;
	private boolean preparing;
	private boolean cancellationStarted;
	private boolean cancelling;
	private Thread cancellationThread;
	private Thread disposalThread;
	private String serialIdentity;
	private long resourceChanges;
	private Grant serialGrant;
	private Throwable fixtureFailure;
	private Throwable stopCause;
	private boolean backstopReported;
	private boolean failureReported;
	private boolean disposing;
	private boolean serialClosed;
	private boolean incomplete;
	private int selected;
	private int entered;
	private int completed;
	private ExecutionStatus finalStatus;
	private Request serialRequest;
	private boolean serialHandoff;
	private boolean serialHandoffFailed;
	private final ResourceReservations.Capacity serialCapacity = ResourceReservations.shared()
			.capacity( 1 );

	/** @return Whether this handle currently has a native parallel owner */
	boolean parallel() {
		synchronized( resourceWake ) {
			return parallelOwner != null;
		}
	}

	/**
	 * Establishes the checked parallel owner and its early Launcher attachment.
	 *
	 * @param context The owning factory context
	 */
	void attachParallel( ExtensionContext context ) {
		parallelOwner = new FlowParallelOwner( this, context );
		parallelOwner.attach( context );
	}

	/**
	 * Checks the actual factory owner before configuration or consumption.
	 *
	 * @param context The factory about to execute
	 */
	void checkFactory( ExtensionContext context ) {
		if( parallel() ) {
			parallelOwner.checkFactory( context );
		}
	}

	/**
	 * Rechecks the parallel factory owner before entering a native leaf.
	 *
	 * @param context The stored factory context, not the leaf context
	 */
	void checkNativeOwner( ExtensionContext context ) {
		parallelOwner.checkFactory( context );
	}

	/**
	 * Audits selected flows and prepares their native admission graph.
	 *
	 * @param flows  Selected flows in canonical order
	 * @param nodes  Corresponding owned native descriptions
	 * @param chains Frozen selected-chain ownership
	 */
	void prepareParallel( List<Flow> flows, List<DynamicNode> nodes,
			ChainPlan chains ) {
		parallelOwner.prepare( flows, nodes, chains );
	}

	/**
	 * Irreversibly closes this run's admission and wakes its factory. This is a
	 * request, not proof of body, native, background-operation or cleanup drainage.
	 * No pooled thread is interrupted and no peer run is cancelled.
	 *
	 * @param cause Non-null first cause, preserved across repeated requests
	 */
	public void stop( Throwable cause ) {
		Objects.requireNonNull( cause );
		FlowParallelOwner parallel;
		synchronized( resourceWake ) {
			if( released )
				return;
			parallel = parallelOwner;
		}
		// Core processing/native evidence may already have stopped parallel
		// admission. Preserve that authoritative first cause in the handle too.
		effects( null, () -> {
			if( parallel != null )
				parallel.stop( cause );
		}, () -> stopLocal( parallel == null ? cause : parallel.status().cause() ) );
	}

	private void stopLocal( Throwable cause ) {
		if( cause == null )
			return; // Native disposal won the race with this Stop request.
		Request pending;
		synchronized( resourceWake ) {
			if( released )
				return;
			latchStop( cause );
			if( cancellationStarted )
				return;
			cancellationStarted = true;
			cancelling = true;
			cancellationThread = Thread.currentThread();
			cause = stopCause;
			broken = true;
			incomplete = true;
			pending = serialRequest;
			serialRequest = null;
			resourceChanges++;
			resourceWake.notifyAll();
		}
		Throwable stopped = cause;
		effects( null, () -> {
			if( pending != null )
				pending.cancel();
		}, () -> serialCapacity.stopping( stopped ), () -> {
			synchronized( resourceWake ) {
				observeBudget();
				cancelling = false;
				cancellationThread = null;
				resourceWake.notifyAll();
			}
			serialResourcesChanged();
		} );
	}

	private void latchStop( Throwable cause ) {
		observeBudget();
		if( stopCause == null ) {
			stopCause = cause;
			if( parallelOwner == null )
				budget.start( nanoTime.getAsLong() );
		}
		broken = true;
		incomplete = true;
		resourceWake.notifyAll();
	}

	private void observeBudget() {
		if( budget.open() && budgetMiss == null && budget.observe( nanoTime.getAsLong() ) ) {
			incomplete = true;
			budgetMiss = new ExecutionStatus.StopBudgetMiss( budget.duration(), budget.elapsed(),
					stopCause,
					entered - completed, -1,
					serialGrant != null || serialHandoff || serialHandoffFailed ? 1 : 0,
					released ? 0 : 1, serialCapacity.owned(), serialCapacity.operations(),
					serialCapacity.callbacks(), cancelling, serialAffected() );
		}
	}

	private List<String> serialAffected() {
		return Stream.concat( serialIdentity == null ? Stream.empty() : Stream.of( serialIdentity ),
				descriptions == null ? Stream.empty()
						: descriptions.stream().skip( completed )
								.map( DynamicNode::getDisplayName ) )
				.distinct().limit( 5 ).toList();
	}

	// Complete committed ownership effects outside the handle monitor. A failing
	// wakeup must not prevent Stop latching or safe disposal, nor mask its caller.
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
	 * @return Immutable bounded evidence; QUIESCENT means owned use ended, never
	 *         that the selection passed. Serial has no invented native-status
	 *         mirror.
	 */
	public ExecutionStatus status() {
		FlowParallelOwner parallel;
		synchronized( resourceWake ) {
			if( finalStatus != null )
				return finalStatus;
			parallel = parallelOwner;
			if( parallel == null )
				return serialStatus();
		}
		return parallel.status();
	}

	private ExecutionStatus serialStatus() {
		observeBudget();
		return new ExecutionStatus( released ? ExecutionStatus.State.QUIESCENT
				: broken ? ExecutionStatus.State.STOPPING : ExecutionStatus.State.ACTIVE,
				stopCause, incomplete, selected, issued, entered, completed, -1, serialCapacity.owned(),
				serialAffected(), Optional.ofNullable( budgetMiss ) );
	}

	/**
	 * Processes a selected flow after its parallel owner has validated body entry.
	 *
	 * @param index The preparation-local flow index
	 * @param grant The already admitted complete reservation
	 */
	void processParallel( int index, Grant grant ) {
		runner.processSelected( index, grant );
	}

	/**
	 * @param index Admitted member
	 * @param grant Existing whole reservation, delivered outside bookkeeping locks
	 */
	void admitted( int index, Grant grant ) {
		runner.admitted( index, grant );
	}

	/** @return Shared admission History in parallel, ordinary History in serial */
	History history() {
		return parallelOwner == null ? new History() : parallelOwner.history();
	}

	/**
	 * Publishes real processing, never an inferred native classification.
	 * 
	 * @param index   Entered selected flow
	 * @param result  Genuine processing classification
	 * @param failure Its primary failure
	 */
	void processedParallel( int index, Result result, Throwable failure ) {
		parallelOwner.processed( index, result, failure );
	}

	/**
	 * @param cause Fatal/incomplete processing evidence, never a fabricated result
	 */
	void incompleteSerial( Throwable cause ) {
		stop( cause == null ? new IllegalStateException( "Missing Flow processing outcome" ) : cause );
	}

	/** Completes reporting while admission still owns the required cleanup tail. */
	void completeParallel() {
		runner.complete();
	}

	/**
	 * @param status Final admission evidence, sealed after cleanup and reporting
	 */
	void disposeParallel( ExecutionStatus status ) {
		synchronized( resourceWake ) {
			finalStatus = status;
			stopCause = status.cause();
		}
		// Listener exceptions can be swallowed. Retain the small failing backstop.
		release();
	}

	/** @param removeBackstop Removes this owner from its class-local store */
	FlowExecution( Runnable removeBackstop ) {
		this( removeBackstop, System::nanoTime );
	}

	/** Package-private test clock for the existing provider-free serial seam. */
	FlowExecution( Runnable removeBackstop, LongSupplier nanoTime ) {
		this.removeBackstop = removeBackstop;
		this.nanoTime = Objects.requireNonNull( nanoTime );
	}

	/**
	 * Configures one finite owner stop/drain waiting budget (default 30 seconds),
	 * before tests() begins preparation. Time starts at first owner-observed Stop,
	 * not factory entry, token mutation or an IDE click; repeated Stop never resets
	 * it. Positive nanoseconds are preserved and overflow is rejected. This bounds
	 * reachable close waits, not blocked inline work, synchronous cleanup, native
	 * joins, Launcher/JVM return or remote cessation. Expiry never releases unsafe
	 * ownership. Late proof is timed when observed by the owner.
	 *
	 * @param duration Non-null positive duration representable in nanoseconds
	 * @return This model-free handle
	 */
	public FlowExecution stopBudget( Duration duration ) {
		FlowParallelOwner parallel;
		synchronized( resourceWake ) {
			requireFactory();
			if( preparing )
				throw new IllegalStateException( "Flow stop budget is frozen at tests() entry" );
			parallel = parallelOwner;
			if( parallel == null )
				budget.configure( duration );
		}
		if( parallel != null )
			parallel.stopBudget( duration );
		return this;
	}

	/**
	 * Freezes handle configuration before any model preparation or resource rule.
	 */
	void preparing() {
		synchronized( resourceWake ) {
			requireFactory();
			preparing = true;
		}
	}

	/**
	 * Attaches the factory's model and title.
	 *
	 * @param title Test title
	 * @param model The existing system model
	 * @return The self-typed sibling adapter
	 */
	public PreparedFlocessor flocessor( String title, Model model ) {
		synchronized( resourceWake ) {
			requireFactory();
			if( attached ) {
				throw new IllegalStateException( "FlowExecution already has a runner" );
			}
			attached = true;
		}
		// Replay construction can read and parse an index. Claim once, but never
		// hold the owner's admission/disposal monitor across that I/O.
		PreparedFlocessor candidate = new PreparedFlocessor( this, Objects.requireNonNull( title ),
				Objects.requireNonNull( model ) );
		try {
			synchronized( resourceWake ) {
				requireFactory();
				runner = candidate;
				return candidate;
			}
		}
		catch( RuntimeException | Error failure ) {
			candidate.detach();
			throw failure;
		}
	}

	/** Begins configuration on the actual factory invocation thread. */
	void enterFactory() {
		synchronized( resourceWake ) {
			if( released || broken ) {
				throw new IllegalStateException( "FlowExecution is closed" );
			}
			factoryThread = Thread.currentThread();
			configuring = true;
		}
	}

	/** Ends configuration before native consumption begins. */
	void leaveFactory() {
		synchronized( resourceWake ) {
			configuring = false;
		}
	}

	/** Rejects configuration outside the owning factory invocation. */
	void requireFactory() {
		synchronized( resourceWake ) {
			if( released || broken || !configuring || Thread.currentThread() != factoryThread ) {
				throw new IllegalStateException( "FlowExecution used outside its factory invocation" );
			}
		}
	}

	/**
	 * Records pure descriptions without running bodies or finalizing output.
	 *
	 * @param nodes The prepared native descriptions
	 * @return The original, non-executing description stream
	 */
	Stream<DynamicNode> describe( List<DynamicNode> nodes ) {
		synchronized( resourceWake ) {
			requireFactory();
			descriptions = new ArrayList<>( nodes );
			selected = nodes.size();
		}
		return nodes.stream();
	}

	/**
	 * Publishes preparation fallback before native consumption, without retaining
	 * the context or invoking listeners under a bookkeeping lock.
	 *
	 * @param context The actual factory context
	 */
	void reportResourceFallback( ExtensionContext context ) {
		List<DynamicNode> nodes;
		PreparedFlocessor preparedRunner;
		synchronized( resourceWake ) {
			if( parallelOwner == null || descriptions == null ) {
				return;
			}
			nodes = descriptions;
			preparedRunner = runner;
		}
		int unknown = 0;
		List<String> sample = new ArrayList<>();
		for( int i = 0; i < nodes.size(); i++ ) {
			if( preparedRunner.requirements( i ).unknown() ) {
				unknown++;
				if( sample.size() < 5 ) {
					String id = nodes.get( i ).getDisplayName();
					sample.add( id.length() > 120 ? id.substring( 0, 120 ) + "..." : id );
				}
			}
		}
		if( unknown != 0 ) {
			context.publishReportEntry( "flow.resources.fallback",
					unknown + " of " + nodes.size() + " selected flows have UNKNOWN resources; "
							+ "global-exclusive fallback excludes all cooperating work (including known-empty flows). "
							+ (unknown == nodes.size() ? "All selected flows will run serially. " : "")
							+ "Affected flows: " + sample
							+ (unknown > sample.size() ? "; " + (unknown - sample.size()) + " more" : "") );
		}
	}

	/**
	 * Validates returned identities and transfers original cleanup to native close.
	 *
	 * @param returned The factory's supported Jupiter return value
	 * @return The live, guarded synchronous consumption stream
	 */
	Stream<DynamicNode> consume( Object returned ) {
		Stream<?> source = stream( returned );
		List<DynamicNode> expectedNodes;
		synchronized( resourceWake ) {
			if( !released ) {
				originalStream = source;
			}
			expectedNodes = descriptions;
		}
		try {
			synchronized( resourceWake ) {
				if( released || broken ) {
					throw new IllegalStateException( "FlowExecution is closed" );
				}
			}
			if( expectedNodes == null ) {
				throw new IllegalStateException( "The Flow factory must prepare its runner with tests()" );
			}
			// Inspect only pure originals. Accept Jupiter's return forms, but no missing,
			// reordered, duplicated, foreign or substituted descriptions/executables.
			Iterator<?> it = source.iterator();
			for( DynamicNode expected : expectedNodes ) {
				if( !it.hasNext() || it.next() != expected ) {
					throw new IllegalStateException(
							"Return exactly the owned Flow descriptions from tests()" );
				}
			}
			if( it.hasNext() ) {
				throw new IllegalStateException(
						"Return exactly the owned Flow descriptions from tests()" );
			}
		}
		catch( RuntimeException | Error failure ) {
			effects( failure, () -> stop( failure ) );
			if( !parallel() ) {
				synchronized( resourceWake ) {
					backstopReported = true; // The live factory is already failing with this cause.
					failureReported = true;
				}
			}
			try( Stream<?> original = source ) {
				throw failure;
			}
			finally {
				if( !parallel() ) {
					effects( failure, this::release );
				}
				else {
					synchronized( resourceWake ) {
						originalStream = null;
					}
				}
			}
		}
		if( parallel() ) {
			return parallelOwner.consume( source );
		}
		synchronized( resourceWake ) {
			if( released || broken ) {
				throw new IllegalStateException( "FlowExecution is closed" );
			}
			live = true;
		}
		Spliterator<DynamicNode> consumption = new Spliterators.AbstractSpliterator<DynamicNode>(
				Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL ) {
			@Override
			public Spliterator<DynamicNode> trySplit() {
				return null;
			}

			@Override
			public boolean tryAdvance( Consumer<? super DynamicNode> action ) {
				Grant finished = null;
				Grant retained;
				ResourceRequirements requirements;
				synchronized( resourceWake ) {
					observeBudget();
					checkFixture();
					if( !live || configuring || Thread.currentThread() != factoryThread ) {
						throw new IllegalStateException( "Flow consumption is outside its factory" );
					}
					if( active || serialHandoff || serialHandoffFailed ) {
						throw new IllegalStateException( "Flow native handoff has not returned safely" );
					}
					// The next actual SAME_THREAD factory advance follows the entire native
					// call, including outer interceptors that reject before Flow enters.
					// action.accept returning (possibly buffering) and close do not prove this.
					if( issued != drained ) {
						broken = true;
					}
					if( broken || issued == descriptions.size() || !runner.continuesChain( issued ) ) {
						finished = serialGrant;
						serialGrant = null;
					}
					retained = serialGrant;
					// This advance proved prior native return. Until the next emission the
					// retained grant is tentative, so a concurrent stop may dispose safely.
					serialGrant = null;
				}
				Grant grant = retained;
				DynamicNode next;
				int index = -1;
				boolean emitted = false;
				Throwable primary = null;
				try {
					if( finished != null )
						finished.close();
					synchronized( resourceWake ) {
						requireConsumption();
						if( issued == descriptions.size() ) {
							exhausted = true;
							return false;
						}
						requirements = runner.reservation( issued );
					}
					grant = reserveSerial( requirements, grant );
					synchronized( resourceWake ) {
						requireConsumption();
						index = issued++;
						next = descriptions.get( index );
						serialGrant = grant;
						serialIdentity = next.getDisplayName();
						serialHandoff = true;
					}
					admitted( index, grant );
					emitted = true;
					action.accept( next );
					return true;
				}
				catch( Throwable failure ) {
					primary = failure;
					synchronized( resourceWake ) {
						broken = true;
						serialHandoffFailed = emitted;
						if( !emitted && index >= 0 ) {
							issued--;
							serialGrant = null;
						}
					}
					effects( failure, () -> stop( failure ) );
					throw failure;
				}
				finally {
					synchronized( resourceWake ) {
						observeBudget();
						serialHandoff = false;
						resourceWake.notifyAll();
					}
					if( !emitted && grant != null ) {
						try {
							grant.close();
						}
						catch( Throwable cleanup ) {
							if( primary == null )
								throw cleanup;
							if( primary != cleanup )
								primary.addSuppressed( cleanup );
						}
					}
				}
			}
		};
		return StreamSupport.stream( consumption, false ).onClose( this::consumptionClosed );
	}

	private Grant reserveSerial( ResourceRequirements requirements, Grant retained ) {
		Request request = retained == null ? ResourceReservations.shared().register( serialCapacity,
				requirements, this::serialResourcesChanged ) : null;
		Throwable primary = null;
		try {
			synchronized( resourceWake ) {
				requireConsumption();
				serialRequest = request;
			}
			for( ;; ) {
				long observed;
				synchronized( resourceWake ) {
					requireConsumption();
					observed = resourceChanges;
				}
				Grant grant = retained == null ? request.tryAcquire() : retained;
				if( grant != null && !grant.pending() ) {
					return grant;
				}
				synchronized( resourceWake ) {
					requireConsumption();
					if( observed == resourceChanges ) {
						resourceWake.wait();
					}
				}
			}
		}
		catch( InterruptedException failure ) {
			Thread.currentThread().interrupt();
			var interrupted = new IllegalStateException( "Flow serial resource admission interrupted",
					failure );
			primary = interrupted;
			throw interrupted;
		}
		catch( RuntimeException | Error failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			synchronized( resourceWake ) {
				if( serialRequest == request ) {
					serialRequest = null;
				}
			}
			effects( primary, () -> {
				if( request != null )
					request.cancel();
			} );
		}
	}

	private static Stream<?> stream( Object value ) {
		if( value instanceof Stream<?> ) {
			return (Stream<?>) value;
		}
		if( value instanceof Iterable<?> ) {
			return StreamSupport.stream( ((Iterable<?>) value).spliterator(), false );
		}
		if( value instanceof Iterator<?> ) {
			return StreamSupport.stream( Spliterators.spliteratorUnknownSize( (Iterator<?>) value, 0 ),
					false );
		}
		if( value instanceof DynamicNode[] ) {
			return Stream.of( (DynamicNode[]) value );
		}
		if( value instanceof DynamicNode ) {
			return Stream.of( value );
		}
		throw new IllegalStateException(
				"Return the owned Flow descriptions using a Jupiter dynamic factory return type" );
	}

	/**
	 * Creates an invocation retaining only this detachable owner and an index.
	 *
	 * @param index The preparation-local flow index
	 * @return The guarded native executable
	 */
	Executable invocation( int index ) {
		return () -> process( index );
	}

	private void process( int index ) {
		if( parallel() ) {
			parallelOwner.process( index, FlowExtension.invocationContext() );
			return;
		}
		PreparedFlocessor processing;
		synchronized( resourceWake ) {
			if( stopCause != null && live && index == drained && index < issued ) {
				drained++;
				throw new TestAbortedException( "Flow serial admission stopped", stopCause );
			}
			requireConsumption();
			if( active || index != drained || index >= issued ) {
				throw new IllegalStateException(
						"Flow executable is outside its owned synchronous consumption" );
			}
			active = true;
			entered++;
			processing = runner;
		}
		try {
			processing.processSelected( index, serialGrant );
		}
		finally {
			synchronized( resourceWake ) {
				observeBudget();
				active = false;
				completed++;
				drained++;
				resourceWake.notifyAll();
			}
		}
	}

	private void requireConsumption() {
		checkFixture();
		if( !live || configuring || broken || Thread.currentThread() != factoryThread ) {
			throw new IllegalStateException(
					"Flow executable is outside its owned synchronous consumption", stopCause );
		}
	}

	private void checkFixture() {
		if( serialCapacity.uncertainty() != null ) {
			broken = true;
			if( fixtureFailure == null )
				fixtureFailure = serialCapacity.uncertainty();
			if( stopCause == null )
				latchStop( fixtureFailure );
		}
	}

	private void serialResourcesChanged() {
		boolean dispose;
		synchronized( resourceWake ) {
			observeBudget();
			checkFixture();
			if( serialCapacity.owned() == 0 && serialGrant == null )
				serialIdentity = null;
			resourceChanges++;
			resourceWake.notifyAll();
			dispose = serialClosed && !released && !cancelling && !active && serialGrant == null
					&& serialCapacity.owned() == 0;
		}
		if( dispose )
			disposeSerial( false, true );
	}

	private void consumptionClosed() {
		disposeSerial( true );
	}

	/**
	 * Shares stop bookkeeping and cleanup ownership between live close and the
	 * public backstop. Only exhausted native consumption may finalize reporting.
	 *
	 * @param consumption Whether this is the live stream's one-shot close callback
	 */
	private void disposeSerial( boolean consumption ) {
		disposeSerial( consumption, false );
		boolean elsewhere;
		synchronized( resourceWake ) {
			elsewhere = disposing && !released && disposalThread != Thread.currentThread()
					|| released && stopCause != null && !failureReported;
		}
		if( elsewhere ) {
			stopLocal( new IllegalStateException( "Flow close during pending owner cleanup" ) );
			synchronized( resourceWake ) {
				backstopReported = true;
				failureReported = true;
			}
			throw serialIncomplete( "Incomplete Flow serial cleanup", awaitSerialDrain(),
					status().cause() );
		}
	}

	private InterruptedException awaitSerialDrain() {
		synchronized( resourceWake ) {
			for( ;; ) {
				observeBudget();
				if( released || budgetMiss != null || disposalThread == Thread.currentThread()
						|| cancellationThread == Thread.currentThread()
						|| Thread.currentThread() == factoryThread
								&& (active || serialHandoff || serialGrant != null)
						|| !disposing && !cancelling && !active && serialGrant == null
								&& serialCapacity.owned() == 0 )
					return null;
				long remaining = budget.remaining();
				try {
					resourceWake.wait( remaining / 1_000_000, (int) (remaining % 1_000_000) );
				}
				catch( InterruptedException failure ) {
					Thread.currentThread().interrupt();
					return failure;
				}
			}
		}
	}

	private IllegalStateException serialIncomplete( String diagnostic,
			InterruptedException interrupted, Throwable cause ) {
		ExecutionStatus snapshot = status();
		var failure = new IllegalStateException( diagnostic + "; "
				+ (interrupted == null ? "" : "drain wait interrupted; ")
				+ snapshot.stopBudgetMiss().map( miss -> "budget missed; evidence observed after "
						+ miss.elapsed() + ": " + miss ).orElse( "no forced release" ),
				cause );
		if( interrupted != null )
			failure.addSuppressed( interrupted );
		return failure;
	}

	private void disposeSerial( boolean consumption, boolean late ) {
		Request pending;
		Stream<?> cleanup;
		PreparedFlocessor completing;
		boolean unsafe;
		Throwable fixtureCause;
		String diagnostic;
		synchronized( resourceWake ) {
			checkFixture();
			if( released || disposing || consumption && !live ) {
				return;
			}
			serialClosed = true;
			fixtureCause = fixtureFailure;
			unsafe = cancelling || active || serialHandoff || serialGrant != null
					|| serialCapacity.owned() != 0;
			boolean complete = consumption && exhausted && !broken && !unsafe
					&& descriptions != null && drained == descriptions.size();
			broken |= !complete;
			incomplete |= !complete;
			pending = serialRequest;
			serialRequest = null;
			resourceChanges++;
			resourceWake.notifyAll();
			diagnostic = complete ? null
					: "Incomplete Flow serial consumption: prepared="
							+ (descriptions == null ? "none" : descriptions.size())
							+ ", issued=" + issued + ", drained=" + drained + ", active=" + active
							+ ", exhausted=" + exhausted + "; no successful completion or report finalization";
			if( !complete ) {
				latchStop( new IllegalStateException( diagnostic ) );
				// This close will report incompleteness even if a final proof wake
				// claims late disposal before the waiting caller resumes.
				backstopReported |= !late;
				failureReported |= !late;
			}
			cleanup = unsafe ? null : originalStream;
			completing = unsafe ? null : runner;
			if( !unsafe ) {
				// Claim disposal before invoking user cleanup, including reentrant close.
				disposing = true;
				disposalThread = Thread.currentThread();
				live = false;
				originalStream = null;
			}
		}
		effects( null, () -> {
			if( pending != null )
				pending.cancel();
		}, () -> {
			if( unsafe ) {
				Throwable cancellationFailure = null;
				try {
					stopLocal( stopCause );
				}
				catch( RuntimeException | Error failure ) {
					cancellationFailure = failure;
				}
				// Body return and stream close cannot prove native return. Keep the
				// original cleanup and backstop even when the JDK consumes its onClose.
				InterruptedException interrupted = late ? null : awaitSerialDrain();
				// Preserve the existing unsafe-close failure channel. The independent
				// snapshot retains the first Stop (which may already be the live
				// factory's primary failure), not a rewritten native result.
				var reported = serialIncomplete( diagnostic, interrupted, fixtureCause );
				if( cancellationFailure != null )
					reported.addSuppressed( cancellationFailure );
				throw reported;
			}
			Throwable primary = null;
			try( Stream<?> original = cleanup ) {
				if( diagnostic != null && !late ) {
					throw new IllegalStateException( diagnostic, stopCause );
				}
				// This is actual exhausted SAME_THREAD processing plus owned drainage.
				// It is not stream-return/native-factory-terminal equivalence.
				if( diagnostic == null )
					completing.complete();
			}
			catch( Throwable failure ) {
				primary = failure;
				synchronized( resourceWake ) {
					latchStop( failure );
					if( late && stopCause != failure )
						stopCause.addSuppressed( failure );
					incomplete = true;
					backstopReported |= !late;
					failureReported |= !late;
				}
				throw failure;
			}
			finally {
				effects( primary, this::release );
			}
		} );
	}

	private void release() {
		PreparedFlocessor detached;
		synchronized( resourceWake ) {
			detached = runner;
		}
		if( detached != null )
			detached.detach();
		Runnable remove;
		synchronized( resourceWake ) {
			observeBudget();
			budget.finish();
			released = true;
			if( finalStatus == null )
				finalStatus = serialStatus();
			parallelOwner = null;
			live = false;
			configuring = false;
			descriptions = null;
			originalStream = null;
			runner = null;
			serialIdentity = null;
			disposalThread = null;
			factoryThread = null;
			resourceWake.notifyAll();
			remove = stopCause == null || backstopReported ? removeBackstop : null;
			if( remove != null )
				removeBackstop = null;
		}
		if( remove != null ) {
			remove.run();
		}
	}

	/**
	 * Exceptional class-store backstop, not a consumer flush operation. Normal
	 * consumption removes this reference before class cleanup. Incompleteness must
	 * never be converted into successful exhaustion or report finalization.
	 */
	@Override
	public void close() {
		FlowParallelOwner parallel;
		Runnable remove = null;
		Throwable stopped = null;
		synchronized( resourceWake ) {
			backstopReported = true;
			if( released ) {
				if( stopCause == null || failureReported )
					return;
				stopped = stopCause;
				remove = removeBackstop;
				removeBackstop = null;
			}
			parallel = parallelOwner;
			if( parallel != null ) {
				broken = true; // Also reject a runner still being constructed outside this lock.
			}
		}
		if( stopped != null ) {
			try {
				throw new IllegalStateException( "Incomplete stopped Flow execution", stopped );
			}
			finally {
				if( remove != null )
					remove.run();
			}
		}
		if( parallel != null ) {
			parallel.close();
		}
		else {
			disposeSerial( false );
		}
	}

	/**
	 * Store teardown is real evidence that its removal callback is no longer
	 * usable.
	 */
	void backstop() {
		synchronized( resourceWake ) {
			removeBackstop = null;
		}
		close();
	}
}
