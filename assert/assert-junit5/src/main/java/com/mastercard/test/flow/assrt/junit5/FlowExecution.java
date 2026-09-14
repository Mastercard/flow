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

	/** @return Whether this handle currently has a native parallel owner */
	boolean parallel() {
		return parallelOwner != null;
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
	 * @param flows Selected flows in canonical order
	 * @param nodes Corresponding owned native descriptions
	 */
	void prepareParallel( List<Flow> flows, List<DynamicNode> nodes ) {
		parallelOwner.prepare( flows, nodes );
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
		requireFactory();
		if( attached ) {
			throw new IllegalStateException( "FlowExecution already has a runner" );
		}
		attached = true;
		runner = new PreparedFlocessor( this, Objects.requireNonNull( title ),
				Objects.requireNonNull( model ) );
		return runner;
	}

	/** Begins configuration on the actual factory invocation thread. */
	void enterFactory() {
		factoryThread = Thread.currentThread();
		configuring = true;
	}

	/** Ends configuration before native consumption begins. */
	void leaveFactory() {
		configuring = false;
	}

	/** Rejects configuration outside the owning factory invocation. */
	void requireFactory() {
		if( !configuring || Thread.currentThread() != factoryThread ) {
			throw new IllegalStateException( "FlowExecution used outside its factory invocation" );
		}
	}

	/**
	 * Records pure descriptions without running bodies or finalizing output.
	 *
	 * @param nodes The prepared native descriptions
	 * @return The original, non-executing description stream
	 */
	Stream<DynamicNode> describe( List<DynamicNode> nodes ) {
		descriptions = new ArrayList<>( nodes );
		return nodes.stream();
	}

	/**
	 * Validates returned identities and transfers original cleanup to native close.
	 *
	 * @param returned The factory's supported Jupiter return value
	 * @return The live, guarded synchronous consumption stream
	 */
	Stream<DynamicNode> consume( Object returned ) {
		originalStream = stream( returned );
		try {
			if( descriptions == null ) {
				throw new IllegalStateException( "The Flow factory must prepare its runner with tests()" );
			}
			// Inspect only pure originals. Accept Jupiter's return forms, but no missing,
			// reordered, duplicated, foreign or substituted descriptions/executables.
			Iterator<?> it = originalStream.iterator();
			for( DynamicNode expected : descriptions ) {
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
			try( Stream<?> original = originalStream ) {
				throw failure;
			}
			finally {
				if( !parallel() ) {
					release();
				}
				else {
					originalStream = null;
				}
			}
		}
		if( parallel() ) {
			return parallelOwner.consume( originalStream );
		}
		live = true;
		Spliterator<DynamicNode> consumption = new Spliterators.AbstractSpliterator<DynamicNode>(
				Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL ) {
			@Override
			public Spliterator<DynamicNode> trySplit() {
				return null;
			}

			@Override
			public boolean tryAdvance( Consumer<? super DynamicNode> action ) {
				requireConsumption();
				if( active || issued != drained ) {
					broken = true;
					throw new IllegalStateException(
							"Flow consumption did not drain synchronously in native SAME_THREAD mode" );
				}
				if( issued == descriptions.size() ) {
					exhausted = true;
					return false;
				}
				DynamicNode next = descriptions.get( issued++ );
				try {
					action.accept( next );
					return true;
				}
				catch( Throwable failure ) {
					broken = true;
					throw failure;
				}
			}
		};
		return StreamSupport.stream( consumption, false ).onClose( this::consumptionClosed );
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
		requireConsumption();
		if( active || index != drained || index >= issued ) {
			throw new IllegalStateException(
					"Flow executable is outside its owned synchronous consumption" );
		}
		active = true;
		try {
			runner.processSelected( index );
		}
		finally {
			active = false;
			drained++;
		}
	}

	private void requireConsumption() {
		if( !live || configuring || broken || Thread.currentThread() != factoryThread ) {
			throw new IllegalStateException(
					"Flow executable is outside its owned synchronous consumption" );
		}
	}

	private void consumptionClosed() {
		if( !live ) {
			return;
		}
		try( Stream<?> original = originalStream ) {
			if( !exhausted || broken || active || drained != descriptions.size() ) {
				broken = true;
				throw new IllegalStateException(
						"Incomplete Flow serial consumption: close is not successful exhaustion" );
			}
			// This is actual exhausted SAME_THREAD processing plus owned drainage.
			// It is not stream-return/native-factory-terminal equivalence.
			runner.complete();
		}
		finally {
			release();
		}
	}

	private void release() {
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
		if( removeBackstop != null ) {
			removeBackstop.run();
			removeBackstop = null;
		}
	}

	/**
	 * Exceptional class-store backstop, not a consumer flush operation. Normal
	 * consumption removes this reference before class cleanup. Incompleteness must
	 * never be converted into successful exhaustion or report finalization.
	 */
	@Override
	public void close() {
		if( released ) {
			return;
		}
		if( parallel() ) {
			parallelOwner.close();
			return;
		}
		broken = true;
		String diagnostic = "Incomplete Flow serial consumption: prepared="
				+ (descriptions == null ? "none" : descriptions.size())
				+ ", issued=" + issued + ", drained=" + drained + ", active=" + active
				+ ", exhausted=" + exhausted + "; no successful completion or report finalization";
		// No report close on abandonment/early close/exception. Incomplete report
		// disposal/publication is a later run-reporting contract, not a success path.
		if( !active ) {
			try( Stream<?> original = originalStream ) {
				throw new IllegalStateException( diagnostic );
			}
			finally {
				release();
			}
		}
		throw new IllegalStateException( diagnostic );
	}
}
