package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.function.Executable;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.History;
import com.mastercard.test.flow.assrt.History.Result;
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
	private long resourceChanges;
	private Grant serialGrant;
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
	 * Revalidates parallel ownership before invoking the factory; serial is
	 * unchanged.
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
	 * @param flows        Selected flows in canonical order
	 * @param nodes        Corresponding owned native descriptions
	 * @param requirements Resolved per-flow requirements in the same order
	 */
	void prepareParallel( List<Flow> flows, List<DynamicNode> nodes,
			List<ResourceRequirements> requirements ) {
		parallelOwner.prepare( flows, nodes, requirements );
	}

	/**
	 * Stops further parallel admission without forcing ownership release.
	 *
	 * @param failure The reason admission cannot safely continue
	 */
	void stopParallel( Throwable failure ) {
		if( parallel() ) {
			parallelOwner.stop( failure );
		}
	}

	/**
	 * Processes a selected flow after its parallel owner has validated body entry.
	 *
	 * @param index The preparation-local flow index
	 */
	void processParallel( int index ) {
		runner.processSelected( index );
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
	 * Finalizes the runner and detaches this handle after proven native drainage.
	 */
	void completeParallel() {
		runner.complete();
		release();
	}

	/** @param removeBackstop Removes this owner from its class-local store */
	FlowExecution( Runnable removeBackstop ) {
		this.removeBackstop = removeBackstop;
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
			stopParallel( failure );
			try( Stream<?> original = source ) {
				throw failure;
			}
			finally {
				if( !parallel() ) {
					release();
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
				Grant finished;
				ResourceRequirements requirements;
				synchronized( resourceWake ) {
					if( !live || configuring || Thread.currentThread() != factoryThread ) {
						throw new IllegalStateException( "Flow consumption is outside its factory" );
					}
					if( active || serialHandoff || serialHandoffFailed ) {
						throw new IllegalStateException( "Flow native handoff has not returned safely" );
					}
					// The next actual SAME_THREAD factory advance follows the entire native
					// call, including outer interceptors that reject before Flow enters.
					// action.accept returning (possibly buffering) and close do not prove this.
					finished = serialGrant;
					serialGrant = null;
					if( issued != drained ) {
						broken = true;
					}
				}
				if( finished != null ) {
					finished.close();
				}
				synchronized( resourceWake ) {
					requireConsumption();
					if( issued == descriptions.size() ) {
						exhausted = true;
						return false;
					}
					requirements = runner.requirements( issued );
				}
				Grant grant = reserveSerial( requirements );
				DynamicNode next;
				boolean emitted = false;
				try {
					synchronized( resourceWake ) {
						requireConsumption();
						next = descriptions.get( issued++ );
						serialGrant = grant;
						serialHandoff = true;
						emitted = true;
					}
					action.accept( next );
					return true;
				}
				catch( Throwable failure ) {
					synchronized( resourceWake ) {
						broken = true;
						serialHandoffFailed = emitted;
					}
					throw failure;
				}
				finally {
					synchronized( resourceWake ) {
						serialHandoff = false;
					}
					if( !emitted ) {
						grant.close();
					}
				}
			}
		};
		return StreamSupport.stream( consumption, false ).onClose( this::consumptionClosed );
	}

	private Grant reserveSerial( ResourceRequirements requirements ) {
		Request request = ResourceReservations.shared().register( serialCapacity, requirements, () -> {
			synchronized( resourceWake ) {
				resourceChanges++;
				resourceWake.notifyAll();
			}
		} );
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
				Grant grant = request.tryAcquire();
				if( grant != null ) {
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
			throw new IllegalStateException( "Flow serial resource admission interrupted", failure );
		}
		finally {
			synchronized( resourceWake ) {
				if( serialRequest == request ) {
					serialRequest = null;
				}
			}
			request.cancel();
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
			requireConsumption();
			if( active || index != drained || index >= issued ) {
				throw new IllegalStateException(
						"Flow executable is outside its owned synchronous consumption" );
			}
			active = true;
			processing = runner;
		}
		try {
			processing.processSelected( index );
		}
		finally {
			synchronized( resourceWake ) {
				active = false;
				drained++;
			}
		}
	}

	private void requireConsumption() {
		if( !live || configuring || broken || Thread.currentThread() != factoryThread ) {
			throw new IllegalStateException(
					"Flow executable is outside its owned synchronous consumption" );
		}
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
		Request pending;
		Stream<?> cleanup;
		PreparedFlocessor completing;
		boolean unsafe;
		String diagnostic;
		synchronized( resourceWake ) {
			if( released || consumption && !live ) {
				return;
			}
			unsafe = active || serialGrant != null;
			boolean complete = consumption && exhausted && !broken && !unsafe
					&& descriptions != null && drained == descriptions.size();
			broken |= !complete;
			pending = serialRequest;
			serialRequest = null;
			resourceChanges++;
			resourceWake.notifyAll();
			diagnostic = complete ? null
					: "Incomplete Flow serial consumption: prepared="
							+ (descriptions == null ? "none" : descriptions.size())
							+ ", issued=" + issued + ", drained=" + drained + ", active=" + active
							+ ", exhausted=" + exhausted + "; no successful completion or report finalization";
			cleanup = unsafe ? null : originalStream;
			completing = unsafe ? null : runner;
			if( !unsafe ) {
				// Claim disposal before invoking user cleanup, including reentrant close.
				released = true;
				live = false;
				originalStream = null;
			}
		}
		if( pending != null ) {
			pending.cancel();
		}
		if( unsafe ) {
			// Body return and stream close cannot prove native return. Keep the
			// original cleanup and backstop even when the JDK consumes its onClose.
			throw new IllegalStateException( diagnostic );
		}
		try( Stream<?> original = cleanup ) {
			if( diagnostic != null ) {
				throw new IllegalStateException( diagnostic );
			}
			// This is actual exhausted SAME_THREAD processing plus owned drainage.
			// It is not stream-return/native-factory-terminal equivalence.
			completing.complete();
		}
		finally {
			release();
		}
	}

	private void release() {
		Runnable remove;
		synchronized( resourceWake ) {
			released = true;
			parallelOwner = null;
			live = false;
			configuring = false;
			descriptions = null;
			originalStream = null;
			if( runner != null ) {
				runner.detach();
			}
			runner = null;
			factoryThread = null;
			remove = removeBackstop;
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
		synchronized( resourceWake ) {
			if( released ) {
				return;
			}
			parallel = parallelOwner;
			if( parallel != null ) {
				broken = true; // Also reject a runner still being constructed outside this lock.
			}
		}
		if( parallel != null ) {
			parallel.close();
		}
		else {
			disposeSerial( false );
		}
	}
}
