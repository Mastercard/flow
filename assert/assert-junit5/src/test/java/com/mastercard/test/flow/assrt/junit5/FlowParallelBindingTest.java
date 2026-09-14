package com.mastercard.test.flow.assrt.junit5;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;

/** Public caller/Launcher regression seam for the restricted native tracer. */
class FlowParallelBindingTest {
	/**
	 * Missing early provenance rejects parameter resolution before factory entry.
	 */
	@Test
	void missingEarlyHookRejectsBeforeTheOriginalFactory() {
		Evidence e = execute( "nohook", "true", 3 );
		assertNull( e.factory );
		assertTrue( e.failures.stream().anyMatch( t -> t.toString().contains( "receiver" ) ),
				e.failures::toString );
		assertTrue( e.events.isEmpty() );
	}

	/**
	 * Exceptional backstop closure prevents dependent body entry and reports
	 * incompleteness.
	 */
	@Test
	void exceptionalStopClosesBodyEntryWithoutInventingCompletion() {
		Evidence e = execute( "stop", "true", 3 );
		assertFalse( e.events.contains( "body:A" ) );
		assertFalse( e.events.contains( "body:B" ) );
		assertTrue( e.failures.stream().anyMatch( t -> t.toString().contains( "Incomplete" ) ),
				e.failures::toString );
	}

	/**
	 * Native predecessor completion publishes binding and releases B without
	 * waiting for C.
	 */
	@Test
	void bindingAndSuccessorFinishWhileIndependentCRemainsActive() {
		Evidence e = execute( "overlap", "true", 4 );
		assertEquals( List.of(), e.failures );
		assertEquals( List.of( "A []:SUCCESSFUL", "B []:SUCCESSFUL", "C []:SUCCESSFUL" ), e.results );
		assertTrue( e.events.indexOf( "finish:A []" ) < e.events.indexOf( "body:B" ) );
		assertTrue( e.events.indexOf( "finish:B []" ) < e.events.indexOf( "end:C" ) );
		assertEquals( "published", e.bound );
		assertEquals( 3, e.durations.size() );
		assertTrue( e.durations.values().stream().allMatch( n -> n > 0 ) );
		assertEquals( 3, e.restored.get() );
		assertEquals( 3, e.sources.get() );
		assertDoesNotThrow( e.handle::close, "normal native completion detached the class backstop" );
	}

	/**
	 * The supplied larger pool preserves binding, independent progress and context
	 * restoration.
	 */
	@Test
	void suppliedTwelveTargetTwentyCapPreservesRealFlowOwnership() {
		Evidence evidence = execute( "overlap", "true", 12 );
		assertEquals( List.of(), evidence.failures );
		assertEquals( 3, evidence.results.size() );
		assertEquals( "published", evidence.bound );
		assertTrue( evidence.events.indexOf( "finish:B []" ) < evidence.events.indexOf( "end:C" ) );
		assertEquals( 3, evidence.restored.get() );
	}

	/**
	 * A processor error leaves the real dependent aborted before its SUT callback.
	 */
	@Test
	void processingErrorAbortsRealDependentWithoutCallingItsSut() {
		Evidence e = execute( "error", "true", 3 );
		assertTrue( e.results.contains( "A []:FAILED" ), e.results::toString );
		assertTrue( e.results.contains( "B []:ABORTED" ), e.results::toString );
		assertFalse( e.events.contains( "body:B" ) );
		assertTrue(
				e.failures.stream().anyMatch( f -> f.getMessage().contains( "Missing dependency" ) ) );
	}

	/**
	 * A post-processing interceptor failure does not invalidate successful
	 * dependency publication.
	 */
	@Test
	void externalNativeFailureDoesNotRewriteSuccessfulHistory() {
		Evidence e = execute( "external", "true", 3 );
		assertTrue( e.results.contains( "A []:FAILED" ), e.results::toString );
		assertTrue( e.results.contains( "B []:SUCCESSFUL" ), e.results::toString );
		assertEquals( "published", e.bound );
	}

	/** Comparison failure still publishes the actual response to the dependent. */
	@Test
	void ordinaryComparisonFailureIsNotHistoryError() {
		Evidence e = execute( "comparison", "true", 3 );
		assertTrue( e.results.contains( "A []:FAILED" ), e.results::toString );
		assertTrue( e.results.contains( "B []:SUCCESSFUL" ), e.results::toString );
		assertEquals( "unexpected", e.bound );
	}

	/**
	 * Unsupported model surfaces and transformed descriptions fail before any SUT
	 * callback.
	 */
	@Test
	void allPreparationMustBeAuditedBeforeAnySutUse() {
		for( String mode : List.of( "unknown", "partial", "report", "capture", "chain", "fanin",
				"transform" ) ) {
			Evidence e = execute( mode, "true", 3 );
			assertFalse( e.failures.isEmpty(), mode );
			assertFalse( e.events.stream().anyMatch( s -> s.startsWith( "body:" ) ), mode );
		}
	}

	/**
	 * Default and explicit serial modes preserve binding on one thread without the
	 * early hook.
	 */
	@Test
	void sameModelAndCallerRemainProviderFreeForAbsentAndFalse() {
		for( String mode : List.of( "absent", "false" ) ) {
			Evidence e = execute( "serial", mode, 2 );
			assertEquals( List.of(), e.failures );
			assertEquals( "published", e.bound );
			assertEquals( 1, e.threads.size() );
		}
	}

	/**
	 * A saturated two-worker pool makes inline progress without leaking invocation
	 * context.
	 */
	@Test
	void busyTargetTwoMakesRealInlineProgressAndRestoresContextOnReuse() {
		Evidence e = execute( "busy", "true", 2 );
		assertEquals( List.of(), e.failures );
		assertEquals( 80, e.results.size() );
		assertTrue( e.inline.get() > 0, "real native body on the still-enumerating factory worker" );
		assertEquals( 80, e.restored.get() );
		assertEquals( 2, e.threads.size() );
	}

	/**
	 * Runs the shared fixture through a real Launcher with controlled native
	 * settings.
	 *
	 * @param scenario The fixture behavior to exercise
	 * @param parallel The Flow mode, or {@code absent} to omit its setting
	 * @param target   The fixed native parallelism
	 * @return Observations collected during the completed Launcher call
	 */
	static Evidence execute( String scenario, String parallel, int target ) {
		Evidence e = new Evidence( scenario, "true".equals( parallel ) );
		ParallelBindingFixture.evidence = e;
		String hook = "junit.platform.launcher.interceptors.enabled";
		String previous = System.getProperty( hook );
		System.setProperty( hook, Boolean.toString( e.parallel && !scenario.equals( "nohook" ) ) );
		try {
			var request = LauncherDiscoveryRequestBuilder.request()
					.selectors( selectClass( ParallelBindingFixture.class ) )
					.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
					.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism",
							"" + target )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
							"" + (target == 12 ? 20 : target) );
			if( !"absent".equals( parallel ) ) {
				request.configurationParameter( "flow.parallel", parallel );
			}
			LauncherFactory.create( LauncherConfig.builder()
					.enableTestExecutionListenerAutoRegistration( false )
					.enableLauncherSessionListenerAutoRegistration( false ).build() )
					.execute( request.build(), e );
		}
		finally {
			e.release.countDown();
			if( previous == null )
				System.clearProperty( hook );
			else
				System.setProperty( hook, previous );
		}
		return e;
	}

	/**
	 * Cross-worker observations and bounded coordination for one fixture execution.
	 */
	static class Evidence implements TestExecutionListener {
		/** Behavior selected by the enclosing test. */
		final String scenario;
		/** Whether guarded native parallel execution is requested. */
		final boolean parallel;
		/** Signals entry of C, or the deliberately blocking busy-case body. */
		final CountDownLatch cEntered = new CountDownLatch( 1 );
		/** Releases the deliberately held independent body. */
		final CountDownLatch release = new CountDownLatch( 1 );
		/** Ordered body and native-terminal observations. */
		final List<String> events = new CopyOnWriteArrayList<>();
		/** Native leaf display names paired with terminal statuses. */
		final List<String> results = new CopyOnWriteArrayList<>();
		/** Failures reported by native execution callbacks. */
		final List<Throwable> failures = new CopyOnWriteArrayList<>();
		/** Monotonic start times keyed by native leaf ID. */
		final java.util.Map<String, Long> starts = new ConcurrentHashMap<>();
		/** Measured native leaf durations in nanoseconds. */
		final java.util.Map<String, Long> durations = new ConcurrentHashMap<>();
		/** Workers that actually enter SUT callbacks. */
		final Set<Thread> threads = ConcurrentHashMap.newKeySet();
		/** Invocations whose worker context was verified restored. */
		final AtomicInteger restored = new AtomicInteger();
		/** Bodies executed inline on the factory worker in the busy scenario. */
		final AtomicInteger inline = new AtomicInteger();
		/** Completed leaves retaining class-source navigation metadata. */
		final AtomicInteger sources = new AtomicInteger();
		/** Thread that entered the original factory. */
		Thread factory;
		/** Actual injected owner retained for post-run lifecycle assertions. */
		FlowExecution handle;
		/** Request value observed after dependency publication into B. */
		volatile String bound;

		/**
		 * Initializes per-execution scenario controls.
		 *
		 * @param scenario The behavior selected by the test
		 * @param parallel Whether parallel ownership is expected
		 */
		Evidence( String scenario, boolean parallel ) {
			this.scenario = scenario;
			this.parallel = parallel;
		}

		@Override
		public void executionStarted( TestIdentifier id ) {
			if( id.isTest() )
				starts.put( id.getUniqueId(), System.nanoTime() );
		}

		@Override
		public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
			result.getThrowable().ifPresent( failures::add );
			if( id.isTest() ) {
				durations.put( id.getUniqueId(), System.nanoTime() - starts.get( id.getUniqueId() ) );
				results.add( id.getDisplayName() + ":" + result.getStatus() );
				events.add( "finish:" + id.getDisplayName() );
				if( id.getSource()
						.filter( org.junit.platform.engine.support.descriptor.ClassSource.class::isInstance )
						.isPresent() )
					sources.incrementAndGet();
				if( id.getDisplayName().equals( "B []" ) )
					release.countDown();
			}
		}
	}

	/**
	 * Verifies worker-local context restoration around original native invocations.
	 */
	public static class InvocationContext implements InvocationInterceptor {
		/** Native leaf identity visible only during the fixture invocation. */
		static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

		@Override
		public void interceptDynamicTest( Invocation<Void> invocation,
				DynamicTestInvocationContext dynamic,
				ExtensionContext context ) throws Throwable {
			Evidence e = ParallelBindingFixture.evidence;
			String previous = TOKEN.get();
			assertNull( previous, "worker context leaked from a previous invocation" );
			assertNull( FlowExtension.invocationContext(), "library context leaked" );
			TOKEN.set( context.getUniqueId() );
			try {
				if( e.scenario.equals( "stop" ) && context.getDisplayName().equals( "A []" ) ) {
					e.handle.close();
				}
				invocation.proceed();
				if( e.scenario.equals( "external" ) && context.getDisplayName().equals( "A []" ) ) {
					throw new IllegalStateException( "external native failure after real processing" );
				}
			}
			finally {
				TOKEN.remove();
				assertNull( FlowExtension.invocationContext(), "library context must be restored" );
				e.restored.incrementAndGet();
			}
		}
	}
}

/**
 * Real dependency-binding fixture with independent work and explicit negative
 * controls.
 */
@ExtendWith(FlowParallelBindingTest.InvocationContext.class)
@FlowTest
class ParallelBindingFixture {
	/** Minimal mutable fixture message; parsing/binding uses the real publisher. */
	static class Text extends Msg {
		private String value;

		/**
		 * Creates an independently mutable message.
		 *
		 * @param value The complete text payload
		 */
		Text( String value ) {
			super( value );
			this.value = value;
		}

		@Override
		public Message child() {
			return new Text( value );
		}

		@Override
		public Message peer( byte[] bytes ) {
			return new Text( new String( bytes, UTF_8 ) );
		}

		@Override
		public byte[] content() {
			return value.getBytes( UTF_8 );
		}

		@Override
		public String assertable( Unpredictable... masks ) {
			return value;
		}

		@Override
		public Set<String> fields() {
			return Set.of( ".+" );
		}

		@Override
		public Object get( String field ) {
			return value;
		}

		@Override
		public Message set( String field, Object replacement ) {
			value = (String) replacement;
			return this;
		}
	}

	/** Controls and observations supplied before each Launcher execution. */
	static FlowParallelBindingTest.Evidence evidence;

	/**
	 * Prepares real mutable messages, dependencies and scenario-specific body
	 * behavior.
	 *
	 * @param execution The actual factory-local owner injected by Flow
	 * @return Owned native descriptions, or a deliberate transformed negative
	 *         control
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		var e = evidence;
		e.factory = Thread.currentThread();
		e.handle = execution;
		Flow a = create( "A", "published" );
		Flow c = create( "C", "response" );
		Flow b = Creator.build( f -> {
			f.meta( m -> m.description( "B" ) );
			if( e.scenario.equals( "chain" ) )
				f.meta( m -> m.tags( tags -> tags.add( "chain:unchecked" ) ) );
			f.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
					.request( new Text( "pending" ) ).response( new Text( "response" ) ) )
					.dependency( a, d -> d.from( i -> true, RESPONSE, ".+" )
							.to( i -> true, REQUEST, ".+" ) );
			if( e.scenario.equals( "fanin" ) )
				f.prerequisite( c );
		} );
		List<Flow> flows = e.scenario.equals( "busy" )
				? IntStream.range( 0, 80 ).mapToObj( n -> create( String.format( "%03d", n ), "response" ) )
						.toList()
				: List.of( a, b, c );
		PreparedFlocessor runner = execution.flocessor( "real binding", new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return flows.stream();
			}
		} ).system( State.FUL, Actrs.BEN );
		if( !e.scenario.equals( "unknown" ) ) {
			runner.independent( "owned synchronous resources and callbacks; no affinity",
					f -> !e.scenario.equals( "partial" ) || f == a );
		}
		if( e.scenario.equals( "report" ) )
			runner.reporting( Reporting.QUIETLY );
		if( e.scenario.equals( "capture" ) )
			runner.logs( new LogCapture() {
				@Override
				public void start( Flow flow ) {
					fail( "capture must not start" );
				}

				@Override
				public Stream<com.mastercard.test.flow.report.data.LogEvent> end( Flow flow ) {
					return Stream.empty();
				}
			} );
		runner.behaviour( assertion -> {
			String name = assertion.flow().meta().description();
			String token = FlowParallelBindingTest.InvocationContext.TOKEN.get();
			assertNotNull( token );
			if( e.parallel )
				assertEquals( token, FlowExtension.invocationContext().getUniqueId() );
			e.threads.add( Thread.currentThread() );
			e.events.add( "body:" + name );
			if( e.scenario.equals( "busy" ) ) {
				if( name.equals( "000" ) ) {
					e.cEntered.countDown();
					await( e.release );
				}
				else if( Thread.currentThread() == e.factory ) {
					e.inline.incrementAndGet();
					e.release.countDown();
				}
			}
			if( e.scenario.equals( "overlap" ) ) {
				if( name.equals( "C" ) ) {
					e.cEntered.countDown();
					await( e.release );
					e.events.add( "end:C" );
				}
				if( name.equals( "A" ) )
					await( e.cEntered );
			}
			if( name.equals( "A" ) && e.scenario.equals( "error" ) )
				throw new IllegalArgumentException( "SUT error" );
			if( name.equals( "B" ) ) {
				e.bound = assertion.expected().request().assertable();
				assertEquals( e.scenario.equals( "comparison" ) ? "unexpected" : "published", e.bound );
			}
			assertion.actual().response( name.equals( "A" ) && e.scenario.equals( "comparison" )
					? "unexpected".getBytes( UTF_8 )
					: assertion.expected().response().content() );
		} );
		Stream<DynamicNode> tests = runner.tests();
		assertThrows( IllegalStateException.class, () -> runner.independent( "late", f -> true ) );
		if( e.scenario.equals( "transform" ) )
			return tests.skip( 1 );
		if( e.scenario.equals( "busy" ) )
			return tests.onClose( e.release::countDown );
		return tests;
	}

	/**
	 * Builds a flow with separately owned request and response messages.
	 *
	 * @param name     The flow description
	 * @param response The expected response payload
	 * @return The independent fixture flow
	 */
	static Flow create( String name, String response ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Text( "request" ) ).response( new Text( response ) ) ) );
	}

	/**
	 * Bounds fixture coordination and preserves interruption when it fails.
	 *
	 * @param latch The body-entry or release signal to await
	 */
	static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 5, TimeUnit.SECONDS ), "fixture coordination timed out" );
		}
		catch( InterruptedException ex ) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException( ex );
		}
	}
}
