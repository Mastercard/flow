package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectUniqueId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherDiscoveryListener;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;

/**
 * Real public Launcher association and restricted independent-body integration.
 */
@SuppressWarnings("static-method")
class FlowLauncherBridgeTest {

	/** Early Launcher-construction switch, distinct from request configuration. */
	static final String HOOK = "junit.platform.launcher.interceptors.enabled";
	/** Evidence belonging to the current Launcher driver thread. */
	static final ThreadLocal<Evidence> CURRENT = new ThreadLocal<>();
	/** Request keys routing concurrent fixture instances to their own evidence. */
	static final Map<String, Evidence> CALLS = new ConcurrentHashMap<>();
	private String previous;

	/**
	 * Enables interception before Launcher construction and initializes call
	 * evidence.
	 */
	@BeforeEach
	void enableEarlyHook() {
		previous = System.getProperty( HOOK );
		System.setProperty( HOOK, "true" );
		CURRENT.set( new Evidence() );
	}

	/** Restores the early hook property and removes per-test routing references. */
	@AfterEach
	void restore() {
		if( previous == null ) {
			System.clearProperty( HOOK );
		}
		else {
			System.setProperty( HOOK, previous );
		}
		CURRENT.remove();
		CALLS.clear();
	}

	/**
	 * A service-loaded interceptor connects the actual owner through native
	 * completion.
	 */
	@Test
	void earlyServiceLoadedHookAssociatesTheRealOwnerAndCompletesNativeBodies() {
		Evidence evidence = CURRENT.get();
		LauncherFactory.create( config() ).execute( request( "true" ), evidence );
		completed( evidence );
	}

	/**
	 * @return Launcher configuration without unrelated automatic session or
	 *         execution listeners
	 */
	static LauncherConfig config() {
		return LauncherConfig.builder().enableLauncherSessionListenerAutoRegistration( false )
				.enableTestExecutionListenerAutoRegistration( false ).build();
	}

	/**
	 * Direct execution preserves discovery identity and registered plus call-local
	 * listeners.
	 */
	@Test
	void directExecutionPreservesRequestDiscoveryAndBothKindsOfListeners() {
		try( var session = LauncherFactory.openSession( config() ) ) {
			var launcher = session.getLauncher();
			var request = request( "true" );
			List<LauncherDiscoveryRequest> discoveries = new ArrayList<>();
			launcher.registerLauncherDiscoveryListeners( new LauncherDiscoveryListener() {
				@Override
				public void launcherDiscoveryStarted( LauncherDiscoveryRequest actual ) {
					discoveries.add( actual );
				}
			} );
			Evidence registered = new Evidence();
			launcher.registerTestExecutionListeners( registered );
			launcher.execute( request, CURRENT.get() );
			assertEquals( 1, discoveries.size() );
			assertSame( request, discoveries.get( 0 ) );
			assertSame( registered.plan, CURRENT.get().plan );
			assertEquals( 1, registered.plans );
			assertEquals( 1, CURRENT.get().plans );
			assertEquals( 1, CURRENT.get().receipts );
			assertEquals( 3, registered.results.size() );
			assertEquals( Set.copyOf( registered.results ), Set.copyOf( CURRENT.get().results ) );
			completed( CURRENT.get() );
		}
	}

	/**
	 * Preview approval cannot cross launcher identities or be reused after
	 * consumption.
	 */
	@Test
	void previewsBelongToTheExactLiveLauncherAndAreConsumedOnce() {
		try( var first = LauncherFactory.openSession( config() );
				var second = LauncherFactory.openSession( config() ) ) {
			var launcher = first.getLauncher();
			TestPlan owned = launcher.discover( request( "true" ) );
			TestPlan imported = second.getLauncher().discover( request( "true" ) );
			assertThrows( IllegalArgumentException.class, () -> launcher.execute( imported ) );
			assertEquals( 0, CURRENT.get().receipts );
			launcher.execute( owned, CURRENT.get() );
			assertEquals( 1, CURRENT.get().receipts );
			assertNotSame( owned, CURRENT.get().plan );
			assertThrows( IllegalArgumentException.class, () -> launcher.execute( owned ) );
			completed( CURRENT.get() );
		}
	}

	/**
	 * Expired per-operation previews and externally selected leaf plans remain
	 * unapproved.
	 */
	@Test
	void perOperationSessionPreviewsAndImportedLeafPlansAreNotApprovals() {
		var launcher = LauncherFactory.create( config() );
		var old = launcher.discover( request( "true" ) );
		assertThrows( IllegalArgumentException.class, () -> launcher.execute( old ) );
		System.setProperty( HOOK, "false" );
		var raw = LauncherFactory.create( config() ).discover( LauncherDiscoveryRequestBuilder.request()
				.selectors( selectUniqueId( "[engine:junit-jupiter]/[class:"
						+ LauncherBridgeFixture.class.getName() + "]/"
						+ "[test-factory:flows(com.mastercard.test.flow.assrt.junit5.FlowExecution)]"
						+ "/[dynamic-test:#1]" ) )
				.configurationParameter( "flow.parallel", "true" ).build() );
		System.setProperty( HOOK, "true" );
		try( var session = LauncherFactory.openSession( config() ) ) {
			var safe = session.getLauncher().discover( request( "true" ) );
			assertThrows( IllegalArgumentException.class, () -> session.getLauncher().execute( raw ) );
			session.getLauncher().execute( safe, CURRENT.get() );
			assertEquals( 1, CURRENT.get().receipts );
			completed( CURRENT.get() );
		}
	}

	/**
	 * Method selection and tag filtering fail before the original factory is
	 * invoked.
	 */
	@Test
	void unsupportedSelectionAndFiltersFailBeforeDiscoveryOrFactories() {
		try( var session = LauncherFactory.openSession( config() ) ) {
			var launcher = session.getLauncher();
			var method = LauncherDiscoveryRequestBuilder.request().selectors( selectMethod(
					LauncherBridgeFixture.class, "flows", FlowExecution.class.getName() ) )
					.configurationParameter( "flow.parallel", "true" ).build();
			var filtered = LauncherDiscoveryRequestBuilder.request()
					.selectors( selectClass( LauncherBridgeFixture.class ) )
					.filters( TagFilter.includeTags( "x" ) )
					.configurationParameter( "flow.parallel", "true" ).build();
			assertThrows( IllegalArgumentException.class, () -> launcher.execute( method ) );
			assertThrows( IllegalArgumentException.class, () -> launcher.discover( filtered ) );
			assertEquals( 0, CURRENT.get().receipts );
			assertEquals( 0, CURRENT.get().factories );
		}
	}

	/**
	 * Consuming one approved preview frees capacity in the bounded session
	 * registry.
	 */
	@Test
	void previewRegistryIsBoundedAndConsumptionRestoresCapacity() {
		try( var session = LauncherFactory.openSession( config() ) ) {
			var launcher = session.getLauncher();
			List<TestPlan> plans = new ArrayList<>();
			for( int i = 0; i < 32; i++ ) {
				plans.add( launcher.discover( request( "true" ) ) );
			}
			assertThrows( IllegalStateException.class, () -> launcher.discover( request( "true" ) ) );
			launcher.execute( plans.get( 0 ), CURRENT.get() );
			launcher.discover( request( "true" ) );
			assertEquals( 1, CURRENT.get().receipts );
		}
	}

	/**
	 * Concurrent calls on one launcher retain distinct owners despite equal native
	 * IDs.
	 *
	 * @throws Exception If concurrent execution or bounded driver shutdown fails
	 */
	@Test
	void overlappingCallsWithEqualNativeIdsKeepSeparateAttachments() throws Exception {
		var drivers = Executors.newFixedThreadPool( 2 );
		try( var session = LauncherFactory.openSession( config() ) ) {
			var barrier = new CyclicBarrier( 2 );
			var launcher = session.getLauncher();
			var first = drivers.submit( () -> overlappingCall( launcher, barrier ) );
			var second = drivers.submit( () -> overlappingCall( launcher, barrier ) );
			Evidence a = first.get( 15, TimeUnit.SECONDS );
			Evidence b = second.get( 15, TimeUnit.SECONDS );
			assertNotSame( a.handle, b.handle );
			assertNotSame( a.plan, b.plan );
			assertEquals( Set.copyOf( a.results ), Set.copyOf( b.results ) );
			completed( a );
			completed( b );
		}
		finally {
			drivers.shutdownNow();
			assertTrue( drivers.awaitTermination( 15, TimeUnit.SECONDS ) );
		}
	}

	private static Evidence overlappingCall( org.junit.platform.launcher.Launcher launcher,
			CyclicBarrier barrier ) {
		Evidence evidence = new Evidence();
		evidence.barrier = barrier;
		CURRENT.set( evidence );
		try {
			launcher.execute( request( "true" ), evidence );
			return evidence;
		}
		finally {
			CURRENT.remove();
		}
	}

	/**
	 * Serial modes use the factory thread and produce no receipt regardless of hook
	 * presence.
	 */
	@Test
	void absentAndFalseRemainRealSerialWithAndWithoutTheHook() {
		for( String hook : List.of( "true", "false" ) ) {
			System.setProperty( HOOK, hook );
			for( String parallel : new String[] { null, "false" } ) {
				Evidence evidence = new Evidence();
				CURRENT.set( evidence );
				LauncherFactory.create( config() ).execute( request( parallel ), evidence );
				assertEquals( List.of(), evidence.failures );
				assertEquals( 1, evidence.factories );
				assertEquals( 3, evidence.bodies.get() );
				assertEquals( 3, evidence.results.size() );
				assertEquals( 1, evidence.threads.size() );
				assertTrue( evidence.threads.contains( evidence.factoryThread ) );
				assertDoesNotThrow( evidence.handle::close );
				assertEquals( 0, evidence.receipts );
			}
		}
	}

	/**
	 * A request-time switch cannot retroactively install the early construction
	 * receiver.
	 */
	@Test
	void missingEarlyHookCannotBeRepairedByALateRequestParameter() {
		System.setProperty( HOOK, "false" );
		var request = request( "true" );
		LauncherFactory.create( config() ).execute( request, CURRENT.get() );
		assertEquals( 1, CURRENT.get().receipts, "real owner attempts its handshake" );
		assertEquals( 0, CURRENT.get().bodies.get() );
		assertEquals( 0, CURRENT.get().factories );
		assertTrue(
				CURRENT.get().failures.stream().anyMatch( t -> t.toString().contains( "receiver" ) ),
				CURRENT.get().failures::toString );
	}

	/**
	 * Failed setup remains incomplete while the next call gets independent routing
	 * and completion.
	 */
	@Test
	void exceptionalFactorySetupDoesNotLeakRoutingIntoTheNextCall() {
		try( var session = LauncherFactory.openSession( config() ) ) {
			Evidence failed = CURRENT.get();
			failed.throwAfterAttach = true;
			session.getLauncher().execute( request( "true" ), failed );
			assertEquals( 1, failed.receipts );
			assertEquals( 1, failed.terminals );
			assertTrue( failed.failures.stream()
					.anyMatch( t -> "fixture setup failed".equals( t.getMessage() ) ) );
			assertEquals( 0, failed.bodies.get() );
			assertThrows( IllegalStateException.class, failed.handle::close );
			Evidence next = new Evidence();
			CURRENT.set( next );
			session.getLauncher().execute( request( "true" ), next );
			assertEquals( 1, next.receipts );
			assertEquals( 1, next.terminals );
			assertNotSame( failed.handle, next.handle );
			completed( next );
		}
	}

	/**
	 * Associates current evidence with a whole-class request using a unique call
	 * key.
	 *
	 * @param parallel The Flow mode, or null to omit the setting
	 * @return The fixture request with a fixed three-worker native pool
	 */
	static LauncherDiscoveryRequest request( String parallel ) {
		String key = UUID.randomUUID().toString();
		if( CURRENT.get() != null ) {
			CALLS.put( key, CURRENT.get() );
		}
		var builder = LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( LauncherBridgeFixture.class ) )
				.configurationParameter( HOOK, "true" )
				.configurationParameter( "bridge.evidence", key )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism", "3" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
						"3" );
		if( parallel != null ) {
			builder.configurationParameter( "flow.parallel", parallel );
		}
		return builder.build();
	}

	/**
	 * Asserts exact owner receipt, native drainage and safe backstop detachment.
	 *
	 * @param evidence Observations from the completed parallel call
	 */
	static void completed( Evidence evidence ) {
		assertEquals( List.of(), evidence.failures );
		assertEquals( 1, evidence.receipts, "exactly one real-owner receipt, no second observer" );
		assertEquals( 1, evidence.terminals );
		assertEquals( 1, evidence.factories );
		assertEquals( 3, evidence.bodies.get() );
		assertEquals( 3, evidence.results.size() );
		assertTrue( evidence.results.stream().allMatch( s -> s.endsWith( ":SUCCESSFUL" ) ) );
		assertDoesNotThrow( evidence.handle::close, "real owner released after native drainage" );
	}

	/**
	 * Call-local native observations shared with the corresponding fixture
	 * instance.
	 */
	static final class Evidence implements TestExecutionListener {
		/** Native execution failures, including container failures. */
		final List<Throwable> failures = new CopyOnWriteArrayList<>();
		/** Native leaf IDs paired with their terminal statuses. */
		final List<String> results = new CopyOnWriteArrayList<>();
		/** Threads that entered real SUT callbacks. */
		final Set<Thread> threads = ConcurrentHashMap.newKeySet();
		/** Number of real SUT callbacks entered. */
		final AtomicInteger bodies = new AtomicInteger();
		/** Actual injected owner for completion and identity assertions. */
		FlowExecution handle;
		/** Thread entering the original fixture factory. */
		Thread factoryThread;
		/** Actual plan delivered by the execution callback. */
		TestPlan plan;
		/** Optional rendezvous forcing concurrent factory overlap. */
		CyclicBarrier barrier;
		/** Requests a setup failure after the injected handle has attached. */
		boolean throwAfterAttach;
		/** Number of plan-start callbacks. */
		int plans;
		/** Number of real-owner handshake report entries. */
		int receipts;
		/** Number of method-source native container terminals. */
		int terminals;
		/** Number of original fixture factory entries. */
		int factories;

		@Override
		public void testPlanExecutionStarted( TestPlan actual ) {
			plan = actual;
			plans++;
		}

		@Override
		public void reportingEntryPublished( TestIdentifier id, ReportEntry entry ) {
			if( entry.getKeyValuePairs().containsKey( "com.mastercard.test.flow/native-call" ) ) {
				receipts++;
			}
		}

		@Override
		public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
			result.getThrowable().ifPresent( failures::add );
			if( id.isTest() ) {
				results.add( id.getUniqueId() + ":" + result.getStatus() );
			}
			else if( id.getSource().filter( MethodSource.class::isInstance ).isPresent() ) {
				terminals++;
			}
		}
	}

	/** Binds each fixture instance to evidence named by its own request. */
	static final class Observe implements BeforeEachCallback {
		@Override
		public void beforeEach( ExtensionContext context ) throws Exception {
			((LauncherBridgeFixture) context.getRequiredTestInstance()).evidence = CALLS
					.get( context.getConfigurationParameter( "bridge.evidence" ).orElse( "" ) );
		}
	}
}

/** Whole-class real-flow fixture for exact public Launcher call association. */
@ExtendWith(FlowLauncherBridgeTest.Observe.class)
@FlowTest
class LauncherBridgeFixture {
	/**
	 * Evidence assigned from the call-specific request key before factory
	 * invocation.
	 */
	FlowLauncherBridgeTest.Evidence evidence;

	/**
	 * Prepares three independent real flows under the injected execution owner.
	 *
	 * @param execution The factory-local handle supplied by Flow
	 * @return Owned dynamic descriptions of the independent flows
	 * @throws Exception If the optional concurrent-call rendezvous fails
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) throws Exception {
		evidence.handle = execution;
		evidence.factoryThread = Thread.currentThread();
		evidence.factories++;
		if( evidence.throwAfterAttach ) {
			throw new IllegalStateException( "fixture setup failed" );
		}
		if( evidence.barrier != null ) {
			evidence.barrier.await( 10, TimeUnit.SECONDS );
		}
		List<Flow> flows = IntStream.range( 0, 3 ).mapToObj( n -> Creator.build( f -> f
				.meta( m -> m.description( "independent " + n ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "request" ) )
						.response( new Msg( "response" ) ) ) ) )
				.toList();
		return execution.flocessor( "launcher bridge", new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return flows.stream();
			}
		} ).system( State.FUL, Actrs.BEN )
				.independent( "fixture-owned synchronous messages and callbacks", f -> true )
				.behaviour( a -> {
					evidence.bodies.incrementAndGet();
					evidence.threads.add( Thread.currentThread() );
					a.actual().response( a.expected().response().content() );
				} ).tests();
	}
}
