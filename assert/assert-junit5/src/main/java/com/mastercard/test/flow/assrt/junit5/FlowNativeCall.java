package com.mastercard.test.flow.assrt.junit5;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.DiscoveryFilter;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Internal exact-execute-call attachment seam. This does not authorize parallel
 * processing: the early Launcher decorator and the prepared admission owner
 * must both be present before the parallel caller can use it. Only that
 * decorator may construct this listener for its actual execute argument.
 * Matching a public plan shape alone cannot establish provenance for an
 * arbitrary listener.
 * <p>
 * Uses public Platform 1.10 APIs, with no session store or JUnit 6 signatures.
 * Notification failures are latched; listener exceptions are not execution
 * vetoes. No body, History, resource grant or completion is invented here.
 */
final class FlowNativeCall implements TestExecutionListener, AutoCloseable {
	/** Report-entry key used only during the synchronous attachment handshake. */
	private static final String TOKEN = "com.mastercard.test.flow/native-call";
	/** Bounds simultaneously pending handshakes, not admitted flow bodies. */
	private static final Semaphore SLOTS = new Semaphore( 32 );
	/** Temporary token receipts removed before attachment returns or fails. */
	private static final Map<String, Receipt> PENDING = new ConcurrentHashMap<>();
	/** The exact selected factory class. */
	private final Class<?> type;
	/** The sole approved factory method, including its parameter signature. */
	private final Method method;
	/** The actual executing plan, never a discovery preview. */
	private TestPlan plan;
	/** The factory identifier bound from that actual plan. */
	private TestIdentifier factory;
	private final Set<String> ancestors = new HashSet<>();
	/** The currently attached notification owner, if any. */
	private Attachment attachment;
	/** First notification/protocol failure, retained for explicit checks. */
	private Throwable problem;
	/** Whether this exact execute-call listener has been closed. */
	private boolean closed;
	private BooleanSupplier cancellation;
	private boolean cancellationBound;

	/** Receives actual native evidence without inventing processing outcomes. */
	interface Observer {
		/** @param id The newly registered native child */
		void registered( TestIdentifier id );

		/** @param id The native child whose execution started */
		void started( TestIdentifier id );

		/**
		 * @param id     The native child whose execution finished
		 * @param result Its actual native outcome
		 */
		void finished( TestIdentifier id, TestExecutionResult result );

		/** @param result The actual terminal factory outcome */
		void factoryFinished( TestExecutionResult result );

		/**
		 * @param id     Actual ancestor from this exact call's executing plan
		 * @param result Its supplied result, not a fabricated factory result
		 */
		default void enclosingFinished( TestIdentifier id, TestExecutionResult result ) {
			// Owners without retained use need no enclosing completion action.
		}

		/**
		 * Rejects unaccounted subtree skips unless the owner explicitly handles them.
		 *
		 * @param id     The actual skipped native node
		 * @param reason The native skip diagnostic
		 */
		default void skipped( TestIdentifier id, String reason ) {
			throw new IllegalStateException( "Unaccounted native subtree skip: " + id + ": " + reason );
		}
	}

	/** Detachable notification link retained until native terminal and drainage. */
	static final class Attachment {
		/** Owning execute call, cleared on release. */
		private FlowNativeCall call;
		/** Live notification recipient, cleared on release. */
		private Observer observer;
		/** Whether terminal factory evidence has actually arrived. */
		private boolean factoryTerminal;
		/** First observer failure, preserved after detachment. */
		private Throwable problem;

		private Attachment( FlowNativeCall call, Observer observer ) {
			this.call = call;
			this.observer = observer;
		}

		/** @return The optional query for this exact execute call, never a fallback */
		BooleanSupplier cancellationQuery() {
			FlowNativeCall owner;
			synchronized( this ) {
				owner = call;
			}
			if( owner == null )
				return null;
			synchronized( owner ) {
				return owner.cancellation;
			}
		}

		/** Rejects failed notifications or use after attachment release. */
		void check() {
			FlowNativeCall owner;
			synchronized( this ) {
				if( problem != null ) {
					throw new IllegalStateException( "Flow native notification failed", problem );
				}
				owner = call;
			}
			if( owner == null ) {
				throw new IllegalStateException( "Flow native attachment was released" );
			}
			owner.check();
		}

		/**
		 * Detach notifications only after the owner has proved its own drainage. This
		 * checks native terminal evidence, not resource or remote-use safety.
		 */
		void release() {
			FlowNativeCall owner;
			synchronized( this ) {
				if( call == null ) {
					return;
				}
				if( !factoryTerminal ) {
					throw new IllegalStateException( "Native factory terminal is required before release" );
				}
				owner = call;
				call = null;
				observer = null;
			}
			owner.detach( this );
		}

		private void notify( Consumer<Observer> event, boolean terminal ) {
			Observer target;
			synchronized( this ) {
				factoryTerminal |= terminal;
				target = observer;
			}
			if( target != null ) {
				// Effects run outside receipt/call locks. This observer must keep
				// actual processing, native evidence and remaining use separate.
				try {
					event.accept( target );
				}
				catch( Throwable failure ) {
					synchronized( this ) {
						if( problem == null ) {
							problem = failure;
						}
					}
					throw failure;
				}
			}
		}
	}

	/** @param request The exact execute request approved by the native decorator */
	FlowNativeCall( LauncherDiscoveryRequest request ) {
		List<DiscoverySelector> selectors = request.getSelectorsByType( DiscoverySelector.class );
		if( selectors.size() != 1 || !(selectors.get( 0 ) instanceof ClassSelector)
				|| !request.getFiltersByType( DiscoveryFilter.class ).isEmpty()
				|| !request.getEngineFilters().isEmpty() || !request.getPostDiscoveryFilters().isEmpty() ) {
			throw new IllegalArgumentException(
					"Flow native call requires a sole full-class selector without filters" );
		}
		type = ((ClassSelector) selectors.get( 0 )).getJavaClass();
		List<Method> factories = ReflectionSupport.findMethods( type,
				m -> AnnotationSupport.isAnnotated( m, TestFactory.class ),
				HierarchyTraversalMode.TOP_DOWN );
		if( type.getEnclosingClass() != null || !AnnotationSupport.isAnnotated( type, FlowTest.class )
				|| factories.size() != 1 ) {
			throw new IllegalArgumentException(
					"Flow native call requires the ordinary top-level sole Flow factory" );
		}
		method = factories.get( 0 );
		if( java.util.Arrays.stream( method.getParameterTypes() )
				.filter( p -> p == FlowExecution.class ).count() != 1 ) {
			throw new IllegalArgumentException(
					"Flow factory requires exactly one FlowExecution parameter" );
		}
	}

	/** @param query Native token query bound once, immediately before execution */
	synchronized void cancellationQuery( BooleanSupplier query ) {
		if( cancellationBound || closed || plan != null )
			throw new IllegalStateException( "Flow cancellation query is already fixed" );
		cancellation = Objects.requireNonNull( query );
		cancellationBound = true;
	}

	/**
	 * Performs a bounded synchronous receipt handshake with one approved call.
	 *
	 * @param context  The actual factory invocation context
	 * @param observer The run-owned native notification recipient
	 * @return The exact call attachment
	 */
	static Attachment attach( ExtensionContext context, Observer observer ) {
		Objects.requireNonNull( observer );
		Receipt receipt = new Receipt( context );
		String token = UUID.randomUUID().toString();
		if( !SLOTS.tryAcquire() ) {
			throw new IllegalStateException( "Flow pending receipt capacity exhausted" );
		}
		try {
			if( PENDING.putIfAbsent( token, receipt ) != null ) {
				throw new IllegalStateException( "Flow receipt token collision" );
			}
			context.publishReportEntry( TOKEN, token );
			return receipt.seal( observer );
		}
		finally {
			PENDING.remove( token, receipt );
			receipt.clear();
			SLOTS.release();
		}
	}

	@Override
	public void testPlanExecutionStarted( TestPlan actual ) {
		try {
			synchronized( this ) {
				if( closed || plan != null ) {
					throw new IllegalStateException( "Flow native call is closed or already bound" );
				}
				if( actual.getRoots().size() != 1 ) {
					throw new IllegalStateException( "Expected only the Jupiter engine" );
				}
				TestIdentifier engine = actual.getRoots().iterator().next();
				List<TestIdentifier> classes = new ArrayList<>( actual.getChildren( engine ) );
				if( !"[engine:junit-jupiter]".equals( engine.getUniqueId() ) || classes.size() != 1 ) {
					throw new IllegalStateException( "Expected ordinary engine/class/factory shape" );
				}
				TestIdentifier clazz = classes.get( 0 );
				List<TestIdentifier> methods = new ArrayList<>( actual.getChildren( clazz ) );
				if( !clazz.getSource().filter( ClassSource.class::isInstance )
						.map( ClassSource.class::cast )
						.map( s -> s.getJavaClass() == type ).orElse( false ) || methods.size() != 1
						|| actual.countTestIdentifiers( i -> true ) != 3 ) {
					throw new IllegalStateException( "Selected class is not the sole Flow workload" );
				}
				TestIdentifier candidate = methods.get( 0 );
				if( candidate.isTest() || !candidate.isContainer()
						|| !candidate.getSource().filter( MethodSource.class::isInstance )
								.map( MethodSource.class::cast )
								.map( s -> s.getJavaMethod().equals( method ) ).orElse( false ) ) {
					throw new IllegalStateException(
							"Actual factory method/parameters differ from approval" );
				}
				plan = actual;
				factory = candidate;
				for( TestIdentifier parent = actual.getParent( candidate ).orElse( null ); parent != null;
						parent = actual.getParent( parent ).orElse( null ) )
					ancestors.add( parent.getUniqueId() );
			}
		}
		catch( Throwable failure ) {
			fail( failure );
		}
	}

	@Override
	public void reportingEntryPublished( TestIdentifier id, ReportEntry entry ) {
		String token = entry.getKeyValuePairs().get( TOKEN );
		Receipt receipt = token == null ? null : PENDING.get( token );
		if( receipt != null ) {
			receipt.offer( this, id );
		}
	}

	private synchronized boolean accepts( Receipt receipt, TestIdentifier id ) {
		return !closed && problem == null && plan != null && attachment == null
				&& factory == id && type == receipt.type && method.equals( receipt.method )
				&& factory.getUniqueId().equals( receipt.factory );
	}

	@Override
	public void dynamicTestRegistered( TestIdentifier id ) {
		deliver( id, o -> o.registered( id ), false );
	}

	@Override
	public void executionStarted( TestIdentifier id ) {
		deliver( id, o -> o.started( id ), false );
	}

	@Override
	public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
		deliver( id, o -> o.finished( id, result ), false );
		deliver( id, o -> o.factoryFinished( result ), true );
		deliverAncestor( id, o -> o.enclosingFinished( id, result ) );
	}

	@Override
	public void executionSkipped( TestIdentifier id, String reason ) {
		deliver( id, o -> o.skipped( id, reason ), false );
		// Actual subtree evidence, not invented descendant finishes.
		deliver( id, o -> o.skipped( id, reason ), true );
		deliverAncestor( id, o -> o.skipped( id, reason ) );
	}

	private void deliverAncestor( TestIdentifier id, Consumer<Observer> event ) {
		Attachment target;
		synchronized( this ) {
			target = ancestors.contains( id.getUniqueId() ) ? attachment : null;
		}
		if( target != null ) {
			try {
				target.notify( event, true );
			}
			catch( Throwable failure ) {
				fail( failure );
			}
		}
	}

	private void deliver( TestIdentifier id, Consumer<Observer> event, boolean terminal ) {
		Attachment target;
		synchronized( this ) {
			if( attachment == null || factory == null ) {
				return;
			}
			boolean factoryEvent = factory.getUniqueId().equals( id.getUniqueId() );
			if( terminal != factoryEvent ) {
				return;
			}
			if( !factoryEvent && !id.getParentId().filter( factory.getUniqueId()::equals ).isPresent() ) {
				return;
			}
			target = attachment;
		}
		try {
			target.notify( event, terminal );
		}
		catch( Throwable failure ) {
			fail( failure );
		}
	}

	/** Rejects a latched protocol failure or use after execute-call close. */
	synchronized void check() {
		if( problem != null ) {
			throw new IllegalStateException( "Flow native call failed", problem );
		}
		if( closed ) {
			throw new IllegalStateException( "Flow native call is closed" );
		}
	}

	private synchronized void fail( Throwable failure ) {
		if( problem == null ) {
			problem = failure;
		}
	}

	private synchronized void detach( Attachment value ) {
		if( attachment == value ) {
			attachment = null;
			cancellation = null;
			ancestors.clear();
			if( closed )
				factory = null;
		}
	}

	@Override
	public synchronized void close() {
		if( closed ) {
			return;
		}
		closed = true;
		plan = null;
		cancellation = null;
		if( attachment != null ) {
			// Call return cannot finalize or force-release owned use. The owner
			// retains its attachment and sees this failure at its backstop check.
			fail( new IllegalStateException( "Flow native owner did not drain and release" ) );
		}
		else
			factory = null;
		if( attachment == null )
			ancestors.clear();
	}

	private static final class Receipt {
		/** Actual factory class from the invoking extension context. */
		private final Class<?> type;
		/** Actual factory method from the invoking extension context. */
		private final Method method;
		/** Actual factory native unique identifier. */
		private final String factory;
		/** Matching receivers during the synchronous publication window only. */
		private final List<FlowNativeCall> candidates = new ArrayList<>();
		/** Prevents offers after the receipt window has closed. */
		private boolean sealed;

		/** @param context The actual factory invocation context */
		Receipt( ExtensionContext context ) {
			type = context.getRequiredTestClass();
			method = context.getRequiredTestMethod();
			factory = context.getUniqueId();
		}

		/**
		 * @param call A candidate approved execute call
		 * @param id   The actual report-entry publisher
		 */
		synchronized void offer( FlowNativeCall call, TestIdentifier id ) {
			if( !sealed && call.accepts( this, id ) && !candidates.contains( call ) ) {
				candidates.add( call );
			}
		}

		/**
		 * @param observer The run-owned notification recipient
		 * @return An attachment to the sole matching receiver
		 */
		synchronized Attachment seal( Observer observer ) {
			sealed = true;
			if( candidates.size() != 1 ) {
				throw new IllegalStateException(
						"Flow receipt requires exactly one approved execute-call receiver; found "
								+ candidates.size() );
			}
			FlowNativeCall call = candidates.get( 0 );
			synchronized( call ) {
				if( !call.accepts( this, call.factory ) ) {
					throw new IllegalStateException(
							"Flow execute-call approval expired before receipt seal" );
				}
				call.attachment = new Attachment( call, observer );
				return call.attachment;
			}
		}

		/** Closes the receipt window and drops all temporary candidate references. */
		synchronized void clear() {
			sealed = true;
			candidates.clear();
		}
	}
}
