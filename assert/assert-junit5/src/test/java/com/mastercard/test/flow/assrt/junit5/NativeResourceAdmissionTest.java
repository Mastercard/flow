package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Assertion;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceRules;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.builder.Creator;

/**
 * Real factory/Launcher seam with independent native pools and fake shared SUT
 * state.
 */
class NativeResourceAdmissionTest {
	private static final Map<String, Run> RUNS = new ConcurrentHashMap<>();
	private static final AtomicInteger IDS = new AtomicInteger();
	private static final ThreadLocal<Run> FACTORY = new ThreadLocal<>();

	/**
	 * A parallel request must explain why unaudited work will run serially.
	 *
	 * @throws Exception If the native run fails to finish
	 */
	@Test
	void unclassifiedParallelFlowsReportFallbackBeforeNativeEmission() throws Exception {
		Run run = new Run( true, List.of( flow( "first unknown" ), flow( "second unknown" ) ),
				r -> {
				}, a -> {
				} );
		run.start();
		run.finish();
		assertEquals( 1, run.fallbacks.size(), "one factory diagnostic, not one per flow" );
		String diagnostic = run.fallbacks.get( 0 );
		assertTrue( diagnostic.contains( "2 of 2" ), diagnostic );
		assertTrue( diagnostic.contains( "first unknown []" ), diagnostic );
		assertTrue( diagnostic.contains( "second unknown []" ), diagnostic );
		assertTrue( diagnostic.contains( "UNKNOWN" ), diagnostic );
		assertTrue( diagnostic.contains( "global-exclusive" ), diagnostic );
		assertTrue( diagnostic.contains( "serial" ), diagnostic );
	}

	/**
	 * Explicit classifications are not fallback, and serial compatibility is quiet.
	 *
	 * @param scenario The audit and requested execution mode
	 * @throws Exception If the native run fails to finish
	 */
	@ParameterizedTest
	@ValueSource(strings = { "partial", "classified", "serial", "serial-declared" })
	void fallbackDiagnosticMatchesTheRequestedModeAndAudit( String scenario ) throws Exception {
		Run run = new Run( !scenario.startsWith( "serial" ),
				List.of( flow( "unclassified" ), flow( "empty" ), flow( "named" ), flow( "exclusive" ) ),
				r -> {
					if( !scenario.equals( "serial" ) ) {
						r.independent( "empty", f -> named( f, "empty" ) );
						r.resources( "named", f -> named( f, "named" ), "diagnostic-resource" );
						r.exclusive( "exclusive", f -> named( f, "exclusive" ) );
					}
					if( scenario.equals( "classified" ) ) {
						r.independent( "remaining audit", f -> named( f, "unclassified" ) );
					}
				}, a -> {
				} );
		run.start();
		run.finish();
		if( scenario.equals( "partial" ) ) {
			assertEquals( 1, run.fallbacks.size() );
			String diagnostic = run.fallbacks.get( 0 );
			assertTrue( diagnostic.contains( "1 of 4" ), diagnostic );
			assertTrue( diagnostic.contains( "unclassified []" ), diagnostic );
			assertTrue( diagnostic.contains( "global-exclusive" ), diagnostic );
			assertFalse( diagnostic.contains( "empty []" ), diagnostic );
			assertFalse( diagnostic.contains( "named []" ), diagnostic );
			assertFalse( diagnostic.contains( "exclusive []" ), diagnostic );
			assertFalse( diagnostic.contains( "All selected flows will run serially" ), diagnostic );
		}
		else {
			assertEquals( List.of(), run.fallbacks );
		}
		assertEquals( Set.of( "unclassified []", "empty []", "named []", "exclusive []" ),
				new HashSet<>( run.registered ) );
		run.flows.forEach( f -> assertEquals( Set.of(), f.meta().tags() ) );
	}

	/**
	 * Large unclassified models need one bounded sample, not a full identity dump.
	 *
	 * @throws Exception If the native run fails to finish
	 */
	@Test
	void fallbackDiagnosticBoundsBothFlowCountAndIdentityLength() throws Exception {
		Run run = new Run( true, IntStream.range( 0, 200 )
				.mapToObj( i -> flow( "unclassified-" + i + "-" + "x".repeat( 2000 ) ) ).toList(),
				r -> {
				}, a -> {
				} );
		run.start();
		run.finish();
		assertEquals( 1, run.fallbacks.size() );
		String diagnostic = run.fallbacks.get( 0 );
		assertTrue( diagnostic.length() <= 1024, "output must be bounded despite long identities" );
		assertTrue( diagnostic.contains( "200 of 200" ), diagnostic );
		assertTrue( diagnostic.contains( "unclassified-0-" ), diagnostic );
		assertTrue( diagnostic.contains( "..." ), diagnostic );
		assertTrue( diagnostic.contains( "195 more" ), diagnostic );
		assertFalse( diagnostic.contains( "unclassified-199-" ), diagnostic );
	}

	/**
	 * Equal resource identities exclude each other across independent Launchers.
	 * 
	 * @throws Exception If either run fails to finish
	 */
	@Test
	void equalKeysSerializeAcrossLaunchersWhileDisjointWorkPasses() throws Exception {
		crossRun( true, true, false );
	}

	/**
	 * A blocked union leaves its free key available to unrelated work.
	 * 
	 * @throws Exception If either run fails to finish
	 */
	@Test
	void blockedMultiResourceSetDoesNotHoldItsFreeKey() throws Exception {
		crossRun( true, true, true );
	}

	/**
	 * Serial and parallel factories cooperate through the same default scope.
	 * 
	 * @throws Exception If either run fails to finish
	 */
	@Test
	void preparedSerialAndParallelShareTheSameScopeInBothDirections() throws Exception {
		crossRun( false, true, false );
		crossRun( true, false, false );
	}

	private static void crossRun( boolean firstParallel, boolean secondParallel, boolean multi )
			throws Exception {
		FakeResourceSUT sut = new FakeResourceSUT();
		CountDownLatch held = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		CountDownLatch freeEntered = new CountDownLatch( 1 );
		CountDownLatch blockedEntered = new CountDownLatch( 1 );
		Run first = new Run( firstParallel, List.of( flow( "holder" ) ),
				r -> r.resources( "queue owner", f -> true, "resource-test-B" ),
				a -> sut.use( Set.of( "B" ), () -> {
					held.countDown();
					await( release );
				} ) );
		List<Flow> secondFlows = secondParallel
				? List.of( flow( "blocked" ), flow( "free" ) )
				: List.of( flow( "blocked" ) );
		Run second = new Run( secondParallel, secondFlows, r -> {
			r.resources( "queue alias", f -> named( f, "blocked" ), new String( "resource-test-B" ) );
			if( multi ) {
				r.resources( "account audit", f -> named( f, "blocked" ), "resource-test-A" );
				r.independent( "broad empty cannot erase either key", f -> true );
			}
			r.resources( "isolated A", f -> named( f, "free" ), "resource-test-A" );
		}, a -> {
			if( named( a.flow(), "blocked" ) ) {
				blockedEntered.countDown();
				sut.use( multi ? Set.of( "A", "B" ) : Set.of( "B" ), () -> {
				} );
			}
			else {
				sut.use( Set.of( "A" ), freeEntered::countDown );
			}
		} );
		try {
			first.start();
			await( held );
			second.start();
			await( second.prepared );
			if( secondParallel ) {
				await( freeEntered );
				assertEquals( 1, blockedEntered.getCount(), "busy flow must not pass disjoint work" );
				assertFalse( second.registered.contains( "blocked []" ), "reservation precedes emission" );
			}
			else {
				assertFalse( blockedEntered.await( 150, TimeUnit.MILLISECONDS ) );
			}
		}
		finally {
			release.countDown();
			first.finish();
			second.finish();
		}
		assertNotSame( first.pool, second.pool, "supported separate actual Launcher pools" );
		assertEquals( secondParallel ? 2 : 1, sut.peak );
		assertEquals( 0, blockedEntered.getCount() );
		if( multi ) {
			assertEquals( Set.of( "resource-test-A", "resource-test-B" ),
					second.requirements.get( 0 ).keys() );
		}
	}

	/**
	 * Both unclassified and explicitly exclusive work conflict with known-empty
	 * use.
	 * 
	 * @throws Exception If a run fails to finish
	 */
	@Test
	void unknownAndExplicitExclusiveConflictWithKnownEmptyInBothDirections() throws Exception {
		for( String policy : List.of( "unknown", "exclusive", "empty-first" ) ) {
			CountDownLatch entered = new CountDownLatch( 1 );
			CountDownLatch release = new CountDownLatch( 1 );
			CountDownLatch later = new CountDownLatch( 1 );
			Run first = new Run( true, List.of( flow( "holder" ) ), r -> {
				if( policy.equals( "exclusive" ) ) {
					r.exclusive( "whole fixture reset", f -> true );
					r.independent( "broad empty must not erase reset", f -> true );
				}
				if( policy.equals( "empty-first" ) )
					r.independent( "audited empty", f -> true );
			}, a -> {
				entered.countDown();
				await( release );
			} );
			Run second = new Run( true, List.of( flow( "later" ) ), r -> {
				if( !policy.equals( "empty-first" ) )
					r.independent( "audited empty", f -> true );
			}, a -> later.countDown() );
			first.state = State.LESS;
			try {
				first.start();
				await( entered );
				second.start();
				await( second.prepared );
				assertFalse( later.await( 150, TimeUnit.MILLISECONDS ), policy );
				assertEquals( List.of(), second.registered, policy );
			}
			finally {
				release.countDown();
				first.finish();
				second.finish();
			}
			assertEquals( 0, later.getCount() );
			assertEquals( policy.equals( "unknown" ), first.requirements.get( 0 ).unknown() );
		}
	}

	/**
	 * Keeps grants through native interceptor cleanup, not merely Flow body return.
	 *
	 * @param parallel Whether the holder uses concurrent native execution
	 * @throws Exception If a Launcher does not finish
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void nativeTerminalNotBodyReturnReleasesUse( boolean parallel ) throws Exception {
		for( boolean rejected : List.of( false, true ) ) {
			CountDownLatch cleaning = new CountDownLatch( 1 );
			CountDownLatch releaseCleanup = new CountDownLatch( 1 );
			CountDownLatch later = new CountDownLatch( 1 );
			Run first = new Run( parallel, List.of( flow( "holder" ) ),
					r -> r.resources( "fixture", f -> true, "cleanup-resource" ), a -> {
					} );
			first.cleanup = () -> {
				cleaning.countDown();
				await( releaseCleanup );
			};
			first.rejectBody = rejected;
			Run second = new Run( true, List.of( flow( "later" ) ),
					r -> r.resources( "same fixture", f -> true, "cleanup-resource" ),
					a -> later.countDown() );
			try {
				first.start();
				await( cleaning );
				second.start();
				await( second.prepared );
				assertFalse( later.await( 150, TimeUnit.MILLISECONDS ) );
			}
			finally {
				releaseCleanup.countDown();
				if( rejected ) {
					first.awaitCompletion();
					assertEquals( 0, first.bodies.get() );
					assertEquals( 1, first.getSummary().getTestsFailedCount() );
				}
				else {
					first.finish();
				}
				second.finish();
			}
			assertEquals( 0, later.getCount() );
		}
	}

	/**
	 * Resolves every matching declaration once, including expanded prerequisites.
	 * 
	 * @throws Exception If the native run fails to finish
	 */
	@Test
	void dependencyExpansionResolvesOnceAndKnownUnionKeepsRestrictions() throws Exception {
		Flow prerequisite = flow( "prerequisite" );
		Flow selected = Creator.build( f -> f.meta( m -> m.description( "selected" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) )
				.prerequisite( prerequisite ) );
		AtomicInteger calls = new AtomicInteger();
		Run run = new Run( true, List.of( prerequisite, selected ), r -> {
			r.exercising( f -> f == selected, message -> {
			} );
			r.resources( "A", f -> {
				calls.incrementAndGet();
				return true;
			}, "audit-A" );
			r.resources( "B", f -> {
				calls.incrementAndGet();
				return true;
			}, "audit-B" );
			r.independent( "empty", f -> {
				calls.incrementAndGet();
				return true;
			} );
		}, a -> {
		} );
		run.start();
		run.finish();
		assertEquals( 6, calls.get() );
		assertEquals( 2, run.requirements.size() );
		for( ResourceRequirements requirement : run.requirements ) {
			assertEquals( Set.of( "audit-A", "audit-B" ), requirement.keys() );
			assertEquals( List.of( "A", "B", "empty" ), requirement.rules().stream().toList() );
			assertFalse( requirement.exclusive() );
		}
		assertEquals( Set.of(), prerequisite.meta().tags() );
		assertEquals( Set.of(), selected.meta().tags() );
	}

	/**
	 * Native rejection remains incomplete, but must not strand unused global or
	 * named ownership.
	 *
	 * @param parallel Whether the native factory uses concurrent execution
	 * @throws Exception If a Launcher does not finish
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void terminalBeforeFlowEntryReturnsOnlyTheProvenUnusedGrant( boolean parallel ) throws Exception {
		for( boolean unknown : List.of( false, true ) ) {
			for( String outcome : List.of( "reject", "skip", "cleanup-throw" ) ) {
				Consumer<PreparedFlocessor> audit = r -> {
					if( !unknown )
						r.resources( "fixture", f -> true, "unused-resource" );
				};
				Run rejected = new Run( parallel, List.of( flow( "rejected" ) ), audit,
						a -> assertEquals( "cleanup-throw", outcome, "no SUT entry" ) );
				rejected.rejectBody = outcome.equals( "reject" );
				rejected.skipBody = outcome.equals( "skip" );
				AtomicInteger cleanups = new AtomicInteger();
				rejected.cleanup = () -> {
					cleanups.incrementAndGet();
					if( outcome.equals( "cleanup-throw" ) )
						throw new IllegalStateException( "native cleanup failure" );
				};
				rejected.start();
				rejected.awaitCompletion();
				assertEquals( 1, rejected.getSummary().getTestsStartedCount() );
				assertEquals( outcome.equals( "skip" ) ? 0 : 1,
						rejected.getSummary().getTestsFailedCount() );
				assertEquals( outcome.equals( "cleanup-throw" ) ? 1 : 0, rejected.bodies.get() );
				assertEquals( 1, cleanups.get() );
				assertFalse( rejected.failures.isEmpty(),
						"native failure/omitted processing stays visible" );
				assertAvailable( unknown ? new String[0] : new String[] { "unused-resource" } );
				Run next = new Run( parallel, List.of( flow( "next" ) ), r -> {
					if( unknown )
						r.independent( "empty after UNKNOWN", f -> true );
					else
						audit.accept( r );
				}, a -> {
				} );
				next.start();
				next.finish();
			}
		}
	}

	/**
	 * Closing a published serial owner before grant prevents later admission and
	 * preserves cleanup failure.
	 *
	 * @param phase Factory return, held native advance, or a race with admission
	 * @throws Exception If a Launcher does not finish
	 */
	@ParameterizedTest
	@ValueSource(strings = { "factory", "advance", "admitting" })
	void disposingSerialAdmissionCannotAcquireAfterTheHolderReleases( String phase )
			throws Exception {
		for( boolean liveClose : List.of( false, true ) ) {
			disposeBeforeGrant( phase, liveClose );
		}
	}

	/**
	 * Coordinates close at a real native consumption edge, never a guessed stack
	 * state.
	 *
	 * @param phase     The native edge to hold or race
	 * @param liveClose Whether to close the live stream instead of the handle
	 * @throws Exception If either Launcher exceeds its deadline
	 */
	private static void disposeBeforeGrant( String phase, boolean liveClose )
			throws Exception {
		CountDownLatch held = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		CountDownLatch returning = new CountDownLatch( 1 );
		CountDownLatch proceed = new CountDownLatch( 1 );
		Run holder = new Run( true, List.of( flow( "holder" ) ),
				r -> r.resources( "held", f -> true, "dispose-resource" ), a -> {
					held.countDown();
					await( release );
				} );
		Run waiting = new Run( false, List.of( flow( "waiting" ) ),
				r -> r.resources( "waiting", f -> true, "dispose-resource" ),
				a -> fail( "disposed body" ) );
		Runnable gate = () -> {
			returning.countDown();
			if( !phase.equals( "admitting" ) )
				await( proceed );
		};
		if( phase.equals( "factory" ) )
			waiting.returning = gate;
		else
			waiting.beforeAdvance = gate;
		IllegalStateException cleanup = new IllegalStateException( "original cleanup failure" );
		waiting.streamCleanup = () -> {
			throw cleanup;
		};
		try {
			holder.start();
			await( held );
			waiting.start();
			await( returning );
			// "admitting" races actual tryAdvance; the public seam cannot prove the
			// exact Object.wait transition. The held phases prove predispatch stop.
			assertNotSame( waiting.original, waiting.liveStream );
			IllegalStateException failure = assertThrows( IllegalStateException.class,
					() -> {
						if( liveClose )
							waiting.liveStream.close();
						else
							waiting.handle.close();
					} );
			assertTrue( failure.getMessage().contains( "Incomplete Flow serial consumption" ) );
			assertEquals( List.of( cleanup ), List.of( failure.getSuppressed() ) );
			proceed.countDown();
			waiting.awaitCompletion();
			assertEquals( 1, release.getCount(),
					"stopped factory must finish without waiting for resource release" );
			assertReservation( false, "dispose-resource" );
		}
		finally {
			proceed.countDown();
			release.countDown();
			holder.finish();
			waiting.awaitCompletion();
		}
		assertEquals( 0, waiting.bodies.get() );
		assertEquals( List.of(), waiting.registered );
		assertEquals( 1, waiting.closes.get() );
		waiting.handle.close();
		waiting.liveStream.close();
		assertEquals( 1, waiting.closes.get() );
		assertFalse( waiting.failures.stream().anyMatch( NullPointerException.class::isInstance ),
				waiting.failures::toString );
		assertAvailable( "dispose-resource" );
		Run next = new Run( false, List.of( flow( "next" ) ),
				r -> r.resources( "reuse", f -> true, "dispose-resource" ), a -> {
				} );
		next.start();
		next.finish();
	}

	private static void assertAvailable( String... keys ) {
		assertReservation( true, keys );
	}

	private static void assertReservation( boolean available, String... keys ) {
		ResourceReservations scope = ResourceReservations.shared();
		Request request = scope.register( scope.capacity( 1 ),
				new ResourceRules().resources( "reuse probe", f -> true, keys ).resolve( null ), () -> {
				} );
		try( Grant grant = request.tryAcquire() ) {
			if( available )
				assertNotNull( grant, "previous run stranded ownership" );
			else
				assertNull( grant, "native cleanup still owns this resource" );
		}
		finally {
			request.cancel();
		}
	}

	/**
	 * Neither close route can release a serial grant or fixture during native
	 * cleanup.
	 * 
	 * @param liveClose Whether to close the retained live stream rather than its
	 *                  owner
	 * @throws Exception If the native run fails to return
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void closingSerialDuringNativeCleanupRetainsOwnershipUntilNativeReturn( boolean liveClose )
			throws Exception {
		CountDownLatch cleaning = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		Run run = new Run( false, List.of( flow( "cleaning" ) ),
				r -> r.resources( "cleanup owner", f -> true, "close-cleanup" ), a -> {
				} );
		run.cleanup = () -> {
			cleaning.countDown();
			await( release );
		};
		try {
			run.start();
			await( cleaning );
			assertEquals( 1, run.bodies.get() );
			assertNotSame( run.original, run.liveStream,
					"close the live native stream, not descriptions" );
			assertThrows( IllegalStateException.class,
					() -> {
						if( liveClose )
							run.liveStream.close();
						else
							run.handle.close();
					} );
			assertEquals( 0, run.closes.get(), "fixture cleanup must not race native use" );
			assertSame( run.requirements.get( 0 ), run.runner.requirements( run.flows.get( 0 ) ),
					"runner must remain attached through native cleanup" );
			assertReservation( false, "close-cleanup" );
			if( liveClose ) {
				run.liveStream.close(); // JDK close handlers are one-shot, even when they throw.
				assertEquals( 0, run.closes.get() );
				assertThrows( IllegalStateException.class, run.handle::close );
				assertReservation( false, "close-cleanup" );
			}
		}
		finally {
			release.countDown();
			run.awaitCompletion();
		}
		assertEquals( 1, run.closes.get() );
		assertFalse( run.failures.isEmpty(), "stop cannot finalize the run as successful" );
		assertAvailable( "close-cleanup" );
		run.handle.close();
		assertEquals( 1, run.closes.get() );
	}

	/**
	 * Resource declarations must not authorize unsupported fixture/report
	 * lifetimes.
	 * 
	 * @throws Exception If a rejected run fails to finish
	 */
	@Test
	void explicitSerialResourcesDoNotAuthorizeUnauditedChainsOrReports() throws Exception {
		Flow chain = Creator.build( f -> f.meta( m -> m.description( "chain" )
				.tags( t -> t.add( "chain:unowned" ) ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) ) );
		for( boolean reporting : List.of( false, true ) ) {
			Run run = new Run( false, List.of( reporting ? flow( "report" ) : chain ), r -> {
				r.resources( "fixture", f -> true, "serial-guard" );
				if( reporting )
					r.reporting( com.mastercard.test.flow.assrt.Reporting.QUIETLY );
			}, a -> fail( "unsupported serial ownership" ) );
			run.start();
			run.awaitCompletion();
			assertEquals( 0, run.bodies.get() );
			assertEquals( 0, run.getSummary().getTestsStartedCount() );
			assertTrue( run.failures.stream().anyMatch( f -> f.toString().contains(
					reporting ? "reporting NEVER" : "context, residue or chain" ) ), run.failures::toString );
		}
	}

	private static Flow flow( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) ) );
	}

	private static boolean named( Flow flow, String name ) {
		return flow.meta().description().equals( name );
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 5, TimeUnit.SECONDS ), "resource fixture coordination timed out" );
		}
		catch( InterruptedException failure ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( failure );
		}
	}

	/**
	 * Fake mutable state detects actual overlapping conflicts, independently of
	 * Flow.
	 */
	static final class FakeResourceSUT {
		private final Set<String> active = new HashSet<>();
		private int users;
		int peak;

		/**
		 * Checks conflicts while executing a fake SUT operation.
		 * 
		 * @param keys   Mutable state used by this operation
		 * @param action Operation, possibly held by a test barrier
		 */
		void use( Set<String> keys, Runnable action ) {
			synchronized( this ) {
				assertTrue( keys.stream().noneMatch( active::contains ), "conflicting SUT use" );
				active.addAll( keys );
				peak = Math.max( peak, ++users );
			}
			try {
				action.run();
			}
			finally {
				synchronized( this ) {
					active.removeAll( keys );
					users--;
				}
			}
		}
	}

	/**
	 * Each task calls a real Launcher; no test body is submitted to this thread.
	 */
	static final class Run extends SummaryGeneratingListener {
		private boolean observed;
		final String id = "resource-run-" + IDS.incrementAndGet();
		final boolean parallel;
		final List<Flow> flows;
		final Consumer<PreparedFlocessor> configure;
		final Consumer<Assertion> body;
		final CountDownLatch prepared = new CountDownLatch( 1 );
		final List<String> registered = new CopyOnWriteArrayList<>();
		final List<Throwable> failures = new CopyOnWriteArrayList<>();
		final List<String> fallbacks = new CopyOnWriteArrayList<>();
		final List<ResourceRequirements> requirements = new ArrayList<>();
		final AtomicInteger bodies = new AtomicInteger();
		FutureTask<Void> execution;
		Thread launcherThread;
		Thread factoryThread;
		ForkJoinPool pool;
		Runnable cleanup = () -> {
		};
		boolean rejectBody;
		boolean skipBody;
		FlowExecution handle;
		private PreparedFlocessor runner;
		private Stream<?> original;
		private Stream<?> liveStream;
		private Runnable beforeAdvance;
		final AtomicInteger closes = new AtomicInteger();
		Runnable returning = () -> {
		};
		Runnable streamCleanup = () -> {
		};
		State state = State.FUL;

		/**
		 * Defines one independently launched, genuinely native run.
		 * 
		 * @param parallel  Whether to enable the optional Launcher bridge
		 * @param flows     Selected model contents
		 * @param configure Resource declarations and runner options
		 * @param body      Fake SUT behavior on the native invocation thread
		 */
		Run( boolean parallel, List<Flow> flows, Consumer<PreparedFlocessor> configure,
				Consumer<Assertion> body ) {
			this.parallel = parallel;
			this.flows = flows;
			this.configure = configure;
			this.body = body;
		}

		/** Starts only the Launcher caller; Jupiter owns every body invocation. */
		void start() {
			String hook = "junit.platform.launcher.interceptors.enabled";
			String previous = System.getProperty( hook );
			System.setProperty( hook, Boolean.toString( parallel ) );
			var session = LauncherFactory.openSession( LauncherConfig.builder()
					.enableTestExecutionListenerAutoRegistration( false )
					.enableLauncherSessionListenerAutoRegistration( false ).build() );
			if( previous == null )
				System.clearProperty( hook );
			else
				System.setProperty( hook, previous );
			RUNS.put( id, this );
			var request = LauncherDiscoveryRequestBuilder.request()
					.selectors( selectClass( NativeResourceFixture.class ) )
					.configurationParameter( "resource.run", id )
					.configurationParameter( "flow.parallel", Boolean.toString( parallel ) )
					.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
					.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism",
							"12" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
							"20" )
					.build();
			execution = new FutureTask<>( () -> {
				try( session ) {
					session.getLauncher().execute( request, this );
				}
				finally {
					RUNS.remove( id );
				}
				return null;
			} );
			launcherThread = new Thread( execution, id );
			launcherThread.start();
		}

		/**
		 * Joins the Launcher and checks all expected native and Flow outcomes.
		 * 
		 * @throws Exception If the Launcher fails or exceeds its deadline
		 */
		void finish() throws Exception {
			if( execution == null )
				return;
			awaitCompletion();
			assertEquals( List.of(), failures );
			assertEquals( flows.size(), getSummary().getTestsStartedCount() );
			assertEquals( flows.size(), getSummary().getTestsSucceededCount() );
			assertEquals( flows.size(), bodies.get() );
			assertEquals( 12, pool.getParallelism() );
		}

		/**
		 * Waits for the actual Launcher result, including intentionally failed runs.
		 * 
		 * @throws Exception If the Launcher fails or exceeds its deadline
		 */
		void awaitCompletion() throws Exception {
			execution.get( 10, TimeUnit.SECONDS );
			launcherThread.join( 1000 );
			assertFalse( launcherThread.isAlive(), "Launcher caller did not exit" );
			if( !observed ) {
				observed = true;
				System.out.printf(
						"Resource native %s parallel=%s started=%d succeeded=%d failed=%d aborted=%d bodies=%d%n",
						id, parallel, getSummary().getTestsStartedCount(),
						getSummary().getTestsSucceededCount(),
						getSummary().getTestsFailedCount(), getSummary().getTestsAbortedCount(), bodies.get() );
			}
		}

		@Override
		public void reportingEntryPublished( TestIdentifier id, ReportEntry entry ) {
			super.reportingEntryPublished( id, entry );
			String fallback = entry.getKeyValuePairs().get( "flow.resources.fallback" );
			if( fallback != null ) {
				fallbacks.add( fallback );
				// Launcher listeners can swallow assertion failures: record and check
				// these observations after execute returns instead.
				if( !id.isContainer() || !id.getDisplayName().equals( "flows(FlowExecution)" )
						|| !registered.isEmpty() || bodies.get() != 0 ) {
					failures.add( new AssertionError( "Fallback must precede native emission and SUT use: "
							+ id + ", registered=" + registered + ", bodies=" + bodies.get() ) );
				}
			}
		}

		@Override
		public void dynamicTestRegistered( TestIdentifier testIdentifier ) {
			super.dynamicTestRegistered( testIdentifier );
			registered.add( testIdentifier.getDisplayName() );
		}

		@Override
		public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
			super.executionFinished( id, result );
			result.getThrowable().ifPresent( failures::add );
		}
	}

	/** Uses public native contexts to select the run and hold post-body cleanup. */
	public static final class FixtureContext implements InvocationInterceptor {
		@Override
		@SuppressWarnings("unchecked")
		public <T> T interceptTestFactoryMethod( Invocation<T> invocation,
				ReflectiveInvocationContext<Method> method, ExtensionContext context ) throws Throwable {
			Run run = RUNS.get( context.getConfigurationParameter( "resource.run" ).orElseThrow() );
			FACTORY.set( run );
			try {
				T returned = invocation.proceed();
				run.liveStream = (Stream<?>) returned;
				assertNotSame( run.original, run.liveStream,
						"outer interceptor receives live consumption" );
				run.returning.run();
				if( run.beforeAdvance != null ) {
					Spliterator<DynamicNode> nativeSource = ((Stream<DynamicNode>) returned).spliterator();
					return (T) StreamSupport.stream(
							new Spliterators.AbstractSpliterator<DynamicNode>( Long.MAX_VALUE,
									Spliterator.ORDERED | Spliterator.NONNULL ) {
								@Override
								public boolean tryAdvance( Consumer<? super DynamicNode> action ) {
									assertSame( run.factoryThread, Thread.currentThread() );
									run.beforeAdvance.run();
									return nativeSource.tryAdvance( action );
								}
							}, false ).onClose( run.liveStream::close );
				}
				return returned;
			}
			finally {
				FACTORY.remove();
			}
		}

		@Override
		public void interceptDynamicTest( Invocation<Void> invocation,
				DynamicTestInvocationContext dynamic, ExtensionContext context ) throws Throwable {
			Run run = RUNS.get( context.getConfigurationParameter( "resource.run" ).orElseThrow() );
			assertEquals( run.parallel ? ExecutionMode.CONCURRENT : ExecutionMode.SAME_THREAD,
					context.getExecutionMode() );
			try {
				if( run.rejectBody )
					throw new IllegalStateException( "native rejection before Flow entry" );
				if( run.skipBody )
					invocation.skip();
				else
					invocation.proceed();
			}
			finally {
				run.cleanup.run();
			}
		}
	}

	/**
	 * Configures the real prepared runner for this factory's independent launch.
	 * 
	 * @param execution Public handle published for lifecycle tests
	 * @return Original descriptions with counted fixture cleanup
	 */
	static Stream<DynamicNode> prepare( FlowExecution execution ) {
		Run run = FACTORY.get();
		run.handle = execution;
		run.factoryThread = Thread.currentThread();
		run.pool = ForkJoinTask.getPool();
		PreparedFlocessor runner = execution.flocessor( "resource admission", new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return run.flows.stream();
			}
		} ).system( run.state, Actrs.BEN ).behaviour( a -> {
			assertSame( run.pool, ForkJoinTask.getPool(), "no second body executor" );
			if( !run.parallel )
				assertSame( run.factoryThread, Thread.currentThread() );
			run.bodies.incrementAndGet();
			run.body.accept( a );
			a.actual().response( a.expected().response().content() );
		} );
		run.runner = runner;
		run.configure.accept( runner );
		Stream<DynamicNode> tests = runner.tests();
		assertThrows( IllegalStateException.class, runner::tests );
		run.flows.forEach( f -> run.requirements.add( runner.requirements( f ) ) );
		assertThrows( IllegalStateException.class, () -> runner.resources( "late", f -> true, "x" ) );
		assertThrows( IllegalStateException.class, () -> runner.exclusive( "late", f -> true ) );
		run.prepared.countDown();
		Stream<DynamicNode> original = tests.onClose( () -> {
			run.closes.incrementAndGet();
			run.streamCleanup.run();
		} );
		run.original = original;
		return original;
	}
}

/**
 * Sole top-level factory, reused only through distinct supported Launcher
 * pools.
 */
@ExtendWith(NativeResourceAdmissionTest.FixtureContext.class)
@FlowTest
class NativeResourceFixture {
	/**
	 * Supplies the sole native Flow factory selected by the fixture Launcher.
	 * 
	 * @param execution Injected model-free owner
	 * @return Prepared native descriptions
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		return NativeResourceAdmissionTest.prepare( execution );
	}
}
