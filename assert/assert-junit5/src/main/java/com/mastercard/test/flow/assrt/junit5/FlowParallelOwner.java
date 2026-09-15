package com.mastercard.test.flow.assrt.junit5;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ForkJoinTask;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestIdentifier;
import org.opentest4j.TestAbortedException;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.FlowAdmission;
import com.mastercard.test.flow.assrt.FlowAdmission.Outcome;
import com.mastercard.test.flow.assrt.History;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.resource.ChainPlan;

/**
 * Native identity/profile adapter over shared-core admission. Original bodies
 * stay on their actual Jupiter invocation thread. Fixture/context ownership
 * remains guarded pending its own slice.
 */
final class FlowParallelOwner implements FlowNativeCall.Observer {
	private final FlowExecution owner;
	private final FlowAdmission admission;
	private FlowNativeProfile profile;
	private final String factoryId;
	private Stream<?> originalStream;
	private FlowNativeCall.Attachment attachment;
	private boolean disposalRegistered;
	private final List<DynamicTest> descriptions = new ArrayList<>();
	private final List<ClassSource> sources = new ArrayList<>();
	private final Map<String, Integer> names = new HashMap<>();

	/**
	 * @param owner   Factory-local handle
	 * @param context Genuine factory context
	 */
	FlowParallelOwner( FlowExecution owner, ExtensionContext context ) {
		this.owner = owner;
		profile = FlowNativeProfile.enter( context );
		// Bound outstanding emissions, not idle workers: 24 at the supported 12/20.
		admission = new FlowAdmission( 2 * ForkJoinTask.getPool().getParallelism() );
		factoryId = context.getUniqueId();
	}

	/** @return The single core processing History */
	History history() {
		return admission.history();
	}

	/** @return Bounded evidence retained independently of report publication */
	com.mastercard.test.flow.assrt.ExecutionStatus status() {
		return admission.status();
	}

	/** @param context Actual factory context for the early receiver handshake */
	void attach( ExtensionContext context ) {
		attachment = FlowNativeCall.attach( context, this );
	}

	/** @param context Factory context being revalidated */
	void checkFactory( ExtensionContext context ) {
		profile.check( context );
		checkAttachment();
	}

	/**
	 * Freezes native identities and rejects surfaces not yet owned by admission.
	 * 
	 * @param flows  Selected flows in canonical order
	 * @param nodes  Original owned descriptions
	 * @param chains Frozen whole-chain ownership
	 */
	void prepare( List<Flow> flows, List<DynamicNode> nodes,
			ChainPlan chains ) {
		for( int i = 0; i < flows.size(); i++ ) {
			Flow flow = flows.get( i );
			for( Dependency dependency : flow.dependencies().toList() ) {
				Flow source = dependency.source().flow();
				if( source == null ) {
					throw unsupported( flow, "absent prerequisite" );
				}
				if( dependency.sink().flow() != null && dependency.sink().flow() != flow ) {
					throw unsupported( flow, "foreign binding destination (pending ticket 14)" );
				}
			}
			DynamicTest description = (DynamicTest) nodes.get( i );
			URI uri = description.getTestSourceUri().orElseThrow(
					() -> unsupported( flow, "missing class source URI" ) );
			if( !"class".equals( uri.getScheme() ) ) {
				throw unsupported( flow, "non-class source URI" );
			}
			descriptions.add( description );
			sources.add( ClassSource.from( uri ) );
			names.put( description.getDisplayName(), i );
		}
		admission.prepare( flows, chains );
	}

	private static IllegalStateException unsupported( Flow flow, String surface ) {
		return new IllegalStateException( "Flow parallel tracer unchecked " + surface + ": "
				+ flow.meta().id() );
	}

	/**
	 * @param original Validated original stream
	 * @return Live native admission stream
	 */
	Stream<DynamicNode> consume( Stream<?> original ) {
		originalStream = original;
		Spliterator<DynamicNode> source = new Spliterators.AbstractSpliterator<>( Long.MAX_VALUE,
				Spliterator.ORDERED | Spliterator.NONNULL ) {
			@Override
			public Spliterator<DynamicNode> trySplit() {
				return null;
			}

			@Override
			public boolean tryAdvance( Consumer<? super DynamicNode> action ) {
				int index = FlowAdmission.WAITING;
				boolean handedOff = false;
				try {
					index = admission.next( () -> {
						profile.checkAdmission();
						checkAttachment();
					} );
					if( index == FlowAdmission.EXHAUSTED ) {
						return false;
					}
					owner.admitted( index, admission.reservation( index ) );
					handedOff = true;
					action.accept( descriptions.get( index ) );
					return true;
				}
				catch( RuntimeException | Error failure ) {
					try {
						stop( failure );
					}
					catch( RuntimeException | Error cleanup ) {
						if( cleanup != failure )
							failure.addSuppressed( cleanup );
					}
					try {
						if( index >= 0 && !handedOff )
							admission.unused( index );
					}
					catch( RuntimeException | Error cleanup ) {
						if( cleanup != failure )
							failure.addSuppressed( cleanup );
					}
					throw failure;
				}
			}
		};
		return StreamSupport.stream( source, false ).onClose( () -> {
			try {
				admission.enumerationClosed();
			}
			catch( RuntimeException | Error failure ) {
				stopAfter( failure );
				throw failure;
			}
		} );
	}

	/**
	 * @param index   Owned executable index
	 * @param context Actual native leaf context
	 */
	void process( int index, ExtensionContext context ) {
		try {
			profile.checkAdmission();
			checkAttachment();
			if( context == null
					|| FlowExtension.invocationExecutable() != descriptions.get( index ).getExecutable()
					|| context
							.getExecutionMode() != org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT ) {
				throw new IllegalStateException( "Flow executable differs from its owned native binding" );
			}
		}
		catch( RuntimeException | Error failure ) {
			stopAfter( failure );
			throw failure;
		}
		if( !admission.enter( index, context.getUniqueId() ) ) {
			throw new TestAbortedException( "Flow parallel admission stopped", admission.stopCause() );
		}
		owner.processParallel( index, admission.reservation( index ) );
	}

	/**
	 * @param index   Entered flow
	 * @param result  Genuine History
	 * @param failure Primary failure
	 */
	void processed( int index, Result result, Throwable failure ) {
		admission.processed( index, result, failure );
	}

	private void checkAttachment() {
		try {
			if( attachment == null ) {
				throw new IllegalStateException( "Missing early Flow native attachment" );
			}
			attachment.check();
		}
		catch( RuntimeException | Error failure ) {
			stopAfter( failure );
			throw failure;
		}
	}

	/** @param failure The cause preventing safe further admission */
	void stop( Throwable failure ) {
		admission.stop( failure );
	}

	private int validate( TestIdentifier id, boolean registration ) {
		Integer index = registration ? names.get( id.getDisplayName() )
				: admission.index( id.getUniqueId() );
		if( index == null || !id.isTest() || id.isContainer()
				|| !id.getParentId().filter( factoryId::equals ).isPresent()
				|| !id.getSource().filter( sources.get( index )::equals ).isPresent()
				|| !id.getDisplayName().equals( descriptions.get( index ).getDisplayName() ) ) {
			throw new IllegalStateException( "Unexpected native Flow identity/source: " + id );
		}
		return index;
	}

	@Override
	public void registered( TestIdentifier id ) {
		try {
			admission.registered( validate( id, true ), id.getUniqueId() );
		}
		catch( RuntimeException | Error failure ) {
			stopAfter( failure );
			throw failure;
		}
	}

	@Override
	public void started( TestIdentifier id ) {
		try {
			validate( id, false );
			admission.started( id.getUniqueId() );
		}
		catch( RuntimeException | Error failure ) {
			stopAfter( failure );
			throw failure;
		}
	}

	@Override
	public void finished( TestIdentifier id, TestExecutionResult result ) {
		try {
			validate( id, false );
			admission.finished( id.getUniqueId(), Outcome.valueOf( result.getStatus().name() ),
					result.getThrowable().orElse( null ) );
		}
		catch( RuntimeException | Error failure ) {
			stopAfter( failure );
			throw failure;
		}
	}

	@Override
	public void skipped( TestIdentifier id, String reason ) {
		try {
			if( id.isTest() ) {
				validate( id, false );
				admission.skipped( id.getUniqueId(), reason );
			}
			else {
				terminal( () -> {
					admission.enclosingFinished(
							new IllegalStateException( "Native Flow scope skipped: " + reason ) );
					return false;
				} );
			}
		}
		catch( RuntimeException | Error failure ) {
			stopAfter( failure );
			throw failure;
		}
	}

	@Override
	public void factoryFinished( TestExecutionResult result ) {
		terminal( () -> admission.factoryFinished( Outcome.valueOf( result.getStatus().name() ),
				result.getThrowable().orElse( null ) ) );
	}

	@Override
	public void enclosingFinished( TestIdentifier id, TestExecutionResult result ) {
		terminal( () -> {
			admission.enclosingFinished( new IllegalStateException( "Native Flow enclosing scope ended: "
					+ id.getUniqueId(), result.getThrowable().orElse( null ) ) );
			return false;
		} );
	}

	private void terminal( BooleanSupplier record ) {
		boolean complete = false;
		Throwable primary = null;
		try {
			complete = record.getAsBoolean();
		}
		catch( RuntimeException | Error failure ) {
			primary = failure;
			Throwable cause = admission.stopCause();
			if( cause != null && cause != failure )
				cause.addSuppressed( failure );
			stopAfter( failure );
			throw failure;
		}
		finally {
			// Native terminal state committed even if one of its notifications failed.
			// Keep the late-drain registration, without masking the native failure.
			try {
				disposeWhenDrained( complete );
			}
			catch( RuntimeException | Error cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
		}
	}

	private void stopAfter( Throwable failure ) {
		try {
			stop( failure );
		}
		catch( RuntimeException | Error cleanup ) {
			if( cleanup != failure )
				failure.addSuppressed( cleanup );
		}
	}

	private void disposeWhenDrained( boolean complete ) {
		synchronized( this ) {
			if( disposalRegistered )
				return;
			disposalRegistered = true;
		}
		admission.whenDrained( () -> dispose( complete ) );
	}

	private void dispose( boolean complete ) {
		Throwable failure = admission.stopCause();
		try( Stream<?> cleanup = originalStream ) {
			if( complete )
				checkAttachment();
		}
		catch( Throwable cleanup ) {
			if( failure == null )
				failure = cleanup;
			else if( failure != cleanup )
				failure.addSuppressed( cleanup );
		}
		finally {
			originalStream = null;
		}
		if( failure != null ) {
			try {
				owner.stop( failure );
			}
			catch( Throwable cleanup ) {
				if( cleanup != failure )
					failure.addSuppressed( cleanup );
			}
		}
		try {
			attachment.release();
			admission.release();
			descriptions.clear();
			sources.clear();
			names.clear();
			profile = null;
			attachment = null;
			owner.disposeParallel( complete && failure == null, admission.status() );
		}
		catch( Throwable cleanup ) {
			try {
				owner.stop( cleanup );
			}
			catch( Throwable secondary ) {
				if( secondary != cleanup )
					cleanup.addSuppressed( secondary );
			}
			if( failure != null && failure != cleanup )
				failure.addSuppressed( cleanup );
			throw cleanup;
		}
	}

	/** Stops at the exceptional backstop without forcing release. */
	void close() {
		admission.close();
	}
}
