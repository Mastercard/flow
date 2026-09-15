package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.assrt.Applicator;
import com.mastercard.test.flow.assrt.Assertion;
import com.mastercard.test.flow.assrt.Checker;
import com.mastercard.test.flow.assrt.ContextDomain;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.Listener;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceRules;
import com.mastercard.test.flow.assrt.junit5.NativeResourceAdmissionTest.Run;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;

/**
 * Actual shared fixture state exercised through the existing real Launcher
 * seam.
 */
class ContextFixtureTest {
	/**
	 * Preparation can commit pending ownership before the genuine factory throws.
	 * Stop cleanup must remain secondary to that exact factory Throwable.
	 *
	 * @throws Exception If the native Launcher fails to drain
	 */
	@Test
	void originalFactoryFailureSurvivesStopCallbackFailure() throws Exception {
		var scope = ResourceReservations.shared();
		var observer = new AtomicReference<ResourceReservations.Request>();
		var calls = new AtomicInteger();
		var original = new IllegalStateException( "factory failed after preparation" );
		var callback = new IllegalArgumentException( "Stop notification failed" );
		Run run = new Run( true, List.of( flow( "pending A", null ), flow( "pending B", null ) ), r -> {
			r.independent( "empty", f -> true );
			try( var descriptions = r.tests() ) {
				observer.set( scope.register( scope.capacity( 1 ),
						new ResourceRules().resources( "observer", f -> true ).resolve( null ), () -> {
							if( calls.incrementAndGet() == 1 )
								throw callback;
						} ) );
				throw original;
			}
		}, a -> {
		} );
		Throwable primary = null;
		try {
			run.start();
			run.awaitCompletion();
			var factoryFailure = run.getSummary().getFailures().stream()
					.filter( f -> f.getTestIdentifier().getDisplayName().equals( "flows(FlowExecution)" ) )
					.findFirst().orElseThrow();
			assertSame( original, factoryFailure.getException() );
			assertEquals( List.of( callback ), List.of( original.getSuppressed() ) );
			assertSame( original, run.handle.status().cause() );
			assertEquals( "QUIESCENT", run.handle.status().state().name() );
			assertTrue( run.handle.status().incomplete() );
			assertEquals( 0, run.bodies.get() );
			assertEquals( 0, run.getSummary().getTestsStartedCount() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			calls.set( 100 );
			if( observer.get() != null )
				observer.get().cancel();
			if( primary != null )
				stop( primary, run );
			try {
				if( run.execution != null )
					run.awaitCompletion();
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh exclusive", f -> true ).resolve( null ), () -> {
					} );
			try( var grant = reuse.tryAcquire() ) {
				assertNotNull( grant );
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
			finally {
				reuse.cancel();
			}
		}
	}

	/**
	 * A real factory terminal must register cleanup despite a failing withdrawal
	 * callback; the enclosing class terminal must not be needed to repair it.
	 *
	 * @throws Exception If the native Launcher fails to drain
	 */
	@Test
	void factoryTerminalStillDisposesAfterWithdrawalCallbackFailure() throws Exception {
		var scope = ResourceReservations.shared();
		var observer = new AtomicReference<ResourceReservations.Request>();
		var callbacks = new AtomicInteger();
		var closesAtClassCleanup = new AtomicInteger( -1 );
		var original = new IllegalStateException( "original factory failure" );
		var callback = new IllegalArgumentException( "withdrawal callback failure" );
		Run run = new Run( true, List.of( flow( "pending A", null ), flow( "pending B", null ) ),
				r -> r.independent( "empty", f -> true ), a -> {
				} );
		run.returning = () -> {
			observer.set( scope.register( scope.capacity( 1 ),
					new ResourceRules().resources( "observer", f -> true ).resolve( null ), () -> {
						if( callbacks.incrementAndGet() == 1 )
							throw callback;
					} ) );
			throw original;
		};
		run.afterAll = () -> closesAtClassCleanup.set( run.closes.get() );
		Throwable primary = null;
		try {
			run.start();
			run.awaitCompletion();
			assertEquals( 1, closesAtClassCleanup.get(), "factory terminal must finish its effects" );
			assertTrue( run.failures.stream().anyMatch( f -> f == original ) );
			assertTrue( contains( run.handle.status().cause(), original ) );
			assertTrue( contains( run.handle.status().cause(), callback ),
					"callback cleanup failure remains visible from the original cause" );
			assertEquals( 0, run.bodies.get() );
			assertEquals( 0, run.getSummary().getTestsStartedCount() );
			assertEquals( 1, run.closes.get() );
			assertEquals( "QUIESCENT", run.handle.status().state().name() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			callbacks.set( 100 );
			if( observer.get() != null )
				observer.get().cancel();
			if( primary != null )
				stop( primary, run );
			try {
				if( run.execution != null )
					run.awaitCompletion();
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh exclusive", f -> true ).resolve( null ), () -> {
					} );
			try( var grant = reuse.tryAcquire() ) {
				assertNotNull( grant );
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
			finally {
				reuse.cancel();
			}
		}
	}

	/**
	 * Original cleanup is real remaining work and its failure must be observable.
	 *
	 * @param parallel Native mode
	 * @param stopped  Whether an earlier Stop must remain primary
	 * @throws Exception If the Launcher fails to drain
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void cleanupFailureIsVisibleAndQuiescenceWaitsForCleanup( boolean parallel, boolean stopped )
			throws Exception {
		var cause = new IllegalStateException( "original stop" );
		var cleanupFailure = new IllegalStateException( "original stream cleanup failed" );
		var cleanupEntered = new CountDownLatch( 1 );
		var cleanupReturn = new CountDownLatch( 1 );
		var current = new AtomicReference<Run>();
		Run run = new Run( parallel, List.of( flow( "completed body", null ) ),
				r -> r.independent( "empty", f -> true ), a -> {
					if( stopped )
						current.get().handle.stop( cause );
				} );
		current.set( run );
		run.streamCleanup = () -> {
			cleanupEntered.countDown();
			await( cleanupReturn );
			throw cleanupFailure;
		};
		Throwable primary = null;
		try {
			run.start();
			await( cleanupEntered );
			assertTrue( !run.handle.status().state().name().equals( "QUIESCENT" ),
					"cleanup has not returned" );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( primary != null )
				stop( primary, run );
			cleanupReturn.countDown();
			try {
				if( run.execution != null )
					run.awaitCompletion();
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
		}
		assertEquals( 1, run.bodies.get() );
		assertEquals( 1, run.closes.get() );
		assertTrue( run.failures.stream().anyMatch( f -> contains( f, cleanupFailure ) ),
				"a swallowed native listener exception is insufficient" );
		assertSame( stopped ? cause : cleanupFailure, run.handle.status().cause() );
		assertTrue( run.handle.status().incomplete() );
		assertEquals( "QUIESCENT", run.handle.status().state().name() );
	}

	/**
	 * Fatal synchronous exit stops admission without making a History ERROR.
	 *
	 * @param parallel Actual native mode
	 * @throws Exception If native work fails to drain
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void fatalExitPreservesTheOriginalStopCause( boolean parallel ) throws Exception {
		Fixture fixture = new Fixture();
		LinkageError cause = new LinkageError( "fatal controlled operation" );
		Run run = new Run( parallel, List.of( flow( "A fatal", null ), flow( "B not invoked", null ) ),
				r -> r.contextDomain( fixture.domain ).independent( "fixture", f -> true ), a -> {
					throw cause;
				} );
		run.cleanup = () -> run.handle.stop( new IllegalStateException( "later outer cleanup stop" ) );
		run.start();
		run.awaitCompletion();
		assertEquals( 1, run.bodies.get() );
		assertEquals( 1, run.getSummary().getTestsFailedCount() );
		assertSame( cause, run.handle.status().cause() );
		if( parallel )
			assertSame( cause,
					assertThrows( IllegalStateException.class, run.handle::close ).getCause() );
		assertEquals( "QUIESCENT", run.handle.status().state().name() );
		assertEquals( 1, run.handle.status().entered() );
		assertEquals( 1, run.handle.status().completed() );
		assertEquals( 1, run.closes.get() );
		try( var use = fixture.domain.tryAcquire() ) {
			assertNotNull( use, "fatal classification is not evidence of ongoing use" );
		}
	}

	/**
	 * @param parallel Native mode
	 * @param prepared Stop after descriptions exist rather than before tests()
	 * @throws Exception If the Launcher fails to finish
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void explicitStopBeforePreparationOrAdmission( boolean parallel, boolean prepared )
			throws Exception {
		RuntimeException cause = new IllegalStateException( "stop before native admission" );
		AtomicReference<Run> current = new AtomicReference<>();
		Run run = new Run( parallel, List.of( flow( "unsubmitted", null ) ), r -> {
			r.independent( "empty", f -> true );
			if( !prepared )
				current.get().handle.stop( cause );
		}, a -> {
			throw new AssertionError( "stopped selection must not execute" );
		} );
		current.set( run );
		if( prepared )
			run.returning = () -> run.handle.stop( cause );
		run.start();
		run.awaitCompletion();
		assertEquals( 0, run.getSummary().getTestsStartedCount() );
		assertEquals( 0, run.bodies.get() );
		assertEquals( prepared ? 1 : 0, run.closes.get() );
		assertSame( cause, run.handle.status().cause() );
		assertEquals( "QUIESCENT", run.handle.status().state().name() );
		assertTrue( run.handle.status().incomplete() );
		run.handle.stop( new IllegalStateException( "later stop" ) );
		assertSame( cause, run.handle.status().cause() );
	}

	/**
	 * T20: a cancelled future and native return leave an actual fake operation
	 * live. Only its final completion proof permits a fresh C, never continuation
	 * B.
	 *
	 * @param parallel A's native mode
	 * @param peerMode C's separate native pool mode
	 * @param chain    Whether B shares A's whole-chain reservation
	 * @throws Exception If a controlled operation or Launcher fails to drain
	 */
	@ParameterizedTest
	@CsvSource({ "false,false,false", "false,true,false", "true,false,false", "true,true,false",
			"false,false,true", "false,true,true", "true,false,true", "true,true,true" })
	void cancelledFutureRetainsOwnershipUntilActualOperationEnds( boolean parallel, boolean peerMode,
			boolean chain )
			throws Exception {
		Fixture fixture = new Fixture();
		Flow a = Creator.build( f -> f.meta( m -> m.description( "A operation" )
				.tags( t -> {
					if( chain )
						t.add( "chain:operation" );
				} ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "A" ) ).response( new Msg( "literal response" ) ) ) );
		Flow b = Creator.build( f -> f.meta( m -> m.description( "B never enters" )
				.tags( t -> {
					if( chain )
						t.add( "chain:operation" );
				} ) )
				.prerequisite( a ).call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "B" ) ).response( new Msg( "literal response" ) ) ) );
		AtomicReference<ContextDomain.Receipt> receipt = new AtomicReference<>();
		CountDownLatch background = new CountDownLatch( 1 );
		CountDownLatch nativeCleanup = new CountDownLatch( 1 );
		CountDownLatch nativeReturn = new CountDownLatch( 1 );
		CountDownLatch endOperation = new CountDownLatch( 1 );
		AtomicReference<FutureTask<Void>> future = new AtomicReference<>();
		AtomicReference<Thread> worker = new AtomicReference<>();
		AtomicReference<Throwable> operationFailure = new AtomicReference<>();
		RuntimeException cause = new IllegalStateException( "A cancellation requested" );
		Run first = new Run( parallel, List.of( a, b ), r -> r.contextDomain( fixture.domain,
				( flow, owned ) -> receipt.set( owned ) ).independent( "fixture", f -> true )
				.isolatedChains( "owned shared fixture", "operation" ),
				assertion -> {
					assertSame( a, assertion.flow(), "stopped B cannot enter" );
					var operation = receipt.get().operation();
					FutureTask<Void> task = new FutureTask<>( () -> {
						try {
							background.countDown();
							await( endOperation );
						}
						finally {
							try {
								operation.complete();
							}
							catch( Throwable failure ) {
								operationFailure.set( failure );
								throw failure;
							}
						}
						return null;
					} );
					Thread thread = new Thread( task, "actual-owned-background-operation" );
					future.set( task );
					worker.set( thread );
					thread.start();
					await( background );
				} );
		first.cleanup = () -> {
			nativeCleanup.countDown();
			await( nativeReturn );
		};
		Run waiting = new Run( peerMode, List.of( flow( "C conflicting", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true ), assertion -> {
				} );
		Throwable primary = null;
		try {
			first.start();
			await( nativeCleanup );
			waiting.start();
			await( waiting.prepared );
			assertEquals( 0, waiting.bodies.get(), "healthy live owner queues C" );
			assertTrue( future.get().cancel( false ) );
			assertTrue( future.get().isDone(), "future completion is not operation completion" );
			assertEquals( "ACTIVE", first.handle.status().state().name() );
			first.handle.stop( cause );
			var stopping = first.handle.status();
			assertEquals( "STOPPING", stopping.state().name() );
			assertSame( cause, stopping.cause() );
			assertEquals( 1, stopping.owners() );
			waiting.awaitCompletion();
			assertEquals( 0, waiting.bodies.get() );
			assertTrue( waiting.failures.stream().anyMatch( f -> contains( f, cause ) ),
					"unsafe conflict wakes C with A's cause, not ordinary busy" );
			nativeReturn.countDown();
			first.awaitCompletion();
			assertEquals( 1, first.bodies.get() );
			assertEquals( 0, first.closes.get(), "native return leaves background ownership live" );
			assertEquals( "STOPPING", first.handle.status().state().name() );
			assertTrue( first.handle.status().incomplete() );
			assertTrue( worker.get().isAlive() );
			assertNull( fixture.domain.uncertainty(), "ongoing use is not damaged fixture state" );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			// Stop every potential waiter before either holder gate is released.
			Throwable reason = primary == null ? cause : primary;
			stop( reason, waiting );
			stop( reason, first );
			nativeReturn.countDown();
			endOperation.countDown();
			for( Run run : List.of( first, waiting ) ) {
				try {
					if( run.execution != null )
						run.awaitCompletion();
				}
				catch( Throwable cleanup ) {
					if( primary == null )
						primary = cleanup;
					else if( primary != cleanup )
						primary.addSuppressed( cleanup );
				}
			}
			if( worker.get() != null ) {
				try {
					worker.get().join( 5000 );
					assertTrue( !worker.get().isAlive(), "actual operation must drain" );
					assertNull( operationFailure.get(), "cancelled FutureTask cannot hide cleanup failure" );
				}
				catch( Throwable cleanup ) {
					if( primary == null )
						primary = cleanup;
					else if( primary != cleanup )
						primary.addSuppressed( cleanup );
				}
			}
		}
		if( primary != null )
			throw new AssertionError( "controlled drainage failed", primary );
		assertEquals( 1, first.closes.get(), "late exact proof performs safe disposal once" );
		assertEquals( "QUIESCENT", first.handle.status().state().name() );
		assertSame( cause, first.handle.status().cause() );
		assertTrue( first.handle.status().incomplete(), "late proof cannot erase incompleteness" );
		assertEquals( 0, first.handle.status().owners() );
		first.handle.stop( new IllegalStateException( "after quiescence" ) );
		assertSame( cause, first.handle.status().cause() );
		Run fresh = new Run( peerMode, List.of( flow( "fresh C", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true ), assertion -> {
				} );
		fresh.start();
		fresh.finish();
	}

	/**
	 * Stop is an explicit request, not exceptional close or native completion.
	 *
	 * @param parallel Actual native mode
	 * @throws Exception If native cleanup fails to drain
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void explicitStopAbortsAnAdmittedUnenteredBody( boolean parallel ) throws Exception {
		Fixture fixture = new Fixture();
		CountDownLatch queued = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		RuntimeException cause = new IllegalStateException( "operator stop" );
		Run run = new Run( parallel, List.of( flow( "queued", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true ), a -> {
				} );
		run.beforeBody = () -> {
			queued.countDown();
			await( release );
		};
		Throwable primary = null;
		try {
			run.start();
			await( queued );
			run.handle.stop( cause );
			run.handle.stop( new IllegalStateException( "later request" ) );
			assertEquals( 0, run.bodies.get() );
			assertEquals( 0, run.closes.get(), "original cleanup cannot race native use" );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( primary != null )
				stop( primary, run );
			release.countDown();
			try {
				if( run.execution != null )
					run.awaitCompletion();
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
		}
		assertEquals( 1, run.getSummary().getTestsStartedCount() );
		assertEquals( 1, run.getSummary().getTestsAbortedCount() );
		assertEquals( 0, run.bodies.get() );
		assertEquals( 1, run.closes.get() );
		assertTrue( run.failures.stream().anyMatch( f -> contains( f, cause ) ),
				"native/class diagnosis must preserve the first explicit cause" );
		try( var use = fixture.domain.tryAcquire() ) {
			assertNotNull( use, "actual native return proved the queued use ended" );
		}
	}

	/**
	 * Receipt publication is not native handoff, including a chain continuation.
	 *
	 * @param parallel Actual native mode
	 * @param chain    Fail publication after the first member safely finishes
	 * @throws Exception If a real Launcher fails to drain
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "false,true", "true,true" })
	void receiptCallbackFailureReturnsUnemittedOwnership( boolean parallel, boolean chain )
			throws Exception {
		Fixture fixture = new Fixture();
		Flow first = Creator.build( f -> f.meta( m -> m.description( "first" )
				.tags( t -> t.add( "chain:publication" ) ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "first" ) )
						.response( new Msg( "literal response" ) ) ) );
		Flow rejected = Creator.build( f -> f.meta( m -> m.description( "rejected" )
				.tags( t -> t.add( "chain:publication" ) ) ).prerequisite( first )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "rejected" ) )
						.response( new Msg( "literal response" ) ) ) );
		RuntimeException failure = new IllegalStateException( "receipt publication failed" );
		RuntimeException cleanup = new IllegalStateException( "original stream cleanup failed" );
		AtomicInteger nativeEntries = new AtomicInteger();
		AtomicInteger nativeReturns = new AtomicInteger();
		AtomicInteger completed = new AtomicInteger();
		Run run = new Run( parallel, chain ? List.of( first, rejected ) : List.of( first ), r -> r
				.contextDomain( fixture.domain, ( flow, receipt ) -> {
					if( !chain || flow == rejected ) {
						assertEquals( chain ? 1 : 0, nativeReturns.get() );
						throw failure;
					}
				} ).independent( "fixture", f -> true ).isolatedChains( "whole fixture", "publication" )
				.listening( new Listener() {
					@Override
					public void flowComplete( Flow flow ) {
						completed.incrementAndGet();
					}
				} ), a -> assertSame( first, a.flow() ) );
		run.beforeBody = nativeEntries::incrementAndGet;
		run.cleanup = nativeReturns::incrementAndGet;
		run.streamCleanup = () -> {
			throw cleanup;
		};
		Throwable primary = null;
		try {
			run.start();
			run.awaitCompletion();
			assertTrue( run.failures.contains( failure ), run.failures::toString );
			assertEquals( chain ? List.of( first.meta().id() ) : List.of(), run.registered );
			assertEquals( chain ? 1 : 0, run.getSummary().getTestsStartedCount() );
			assertEquals( chain ? 1 : 0, run.getSummary().getTestsSucceededCount() );
			assertEquals( 0, run.getSummary().getTestsFailedCount() );
			assertEquals( chain ? 1 : 0, run.bodies.get() );
			assertEquals( chain ? 1 : 0, completed.get() );
			assertEquals( chain ? 1 : 0, nativeEntries.get() );
			assertNull( fixture.domain.uncertainty() );
			try( var use = fixture.domain.tryAcquire() ) {
				assertNotNull( use, "publication failure has no native use to retain" );
			}
			assertTrue( contains( failure, cleanup ), "original failure keeps suppressed cleanup" );
		}
		catch( Throwable problem ) {
			primary = problem;
			throw problem;
		}
		finally {
			if( primary != null )
				drain( primary, run );
		}
		Run reused = new Run( parallel, List.of( flow( "reused", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true ), a -> {
				} );
		reused.start();
		reused.finish();
	}

	private static boolean contains( Throwable root, Throwable sought ) {
		List<Throwable> causes = new ArrayList<>( List.of( root ) );
		Set<Throwable> visited = Collections.newSetFromMap( new IdentityHashMap<>() );
		for( int i = 0; i < causes.size(); i++ ) {
			Throwable cause = causes.get( i );
			if( visited.add( cause ) ) {
				if( cause.getCause() != null )
					causes.add( cause.getCause() );
				causes.addAll( List.of( cause.getSuppressed() ) );
			}
		}
		return visited.contains( sought );
	}

	/**
	 * Preserve native skips and NOT_OBSERVED prerequisite eligibility without
	 * applying contexts or entering live behaviour for unobservable flows.
	 *
	 * @param parallel Actual native mode
	 * @param scenario Existing processing path
	 * @throws Exception If the Launcher fails to finish
	 */
	@ParameterizedTest
	@CsvSource({ "false,autonomous", "true,autonomous", "false,implicit", "true,implicit",
			"false,outside", "true,outside", "false,noAssertions", "true,noAssertions" })
	void existingEligibilityAndNoAssertionSemantics( boolean parallel, String scenario )
			throws Exception {
		Fixture fixture = new Fixture();
		Flow source = Creator.build( f -> {
			f.meta( m -> m.description( "source" ) ).context( new Setting( "configured" ) )
					.call( i -> i.from( scenario.equals( "autonomous" ) ? Actrs.BEN : Actrs.AVA )
							.to( scenario.equals( "outside" ) || scenario.equals( "autonomous" ) ? Actrs.CHE
									: Actrs.BEN )
							.request( new Msg( "literal request" ) ).response( new Msg( "literal response" ) ) );
			if( scenario.equals( "implicit" ) )
				f.implicit( actors -> actors.add( Actrs.DAN ) );
		} );
		List<Flow> flows = new ArrayList<>( List.of( source ) );
		if( scenario.equals( "autonomous" ) )
			flows.add(
					Creator.build( f -> f.meta( m -> m.description( "dependent" ) ).prerequisite( source )
							.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "observe" ) )
									.response( new Msg( "observed" ) ) ) ) );
		AtomicInteger noAssertions = new AtomicInteger();
		Run run = new Run( parallel, flows, r -> {
			r.contextDomain( fixture.domain ).independent( "fixture", f -> true )
					.applicators( fixture.applicator() );
			if( scenario.equals( "autonomous" ) )
				r.autonomous( Actrs.BEN );
			if( scenario.equals( "noAssertions" ) )
				r.behaviour( a -> noAssertions.incrementAndGet() );
		}, a -> assertEquals( "default", fixture.value ) );
		run.start();
		run.awaitCompletion();
		assertEquals( scenario.equals( "autonomous" ) ? 2 : 1,
				run.getSummary().getTestsStartedCount() );
		assertEquals( 1, run.getSummary().getTestsAbortedCount() );
		assertEquals( scenario.equals( "autonomous" ) ? 1 : 0,
				run.getSummary().getTestsSucceededCount() );
		assertEquals( 0, run.getSummary().getTotalFailureCount(), run.failures::toString );
		assertEquals( scenario.equals( "autonomous" ) ? 1 : 0, run.bodies.get() );
		assertEquals( scenario.equals( "noAssertions" ) ? 1 : 0, noAssertions.get() );
		assertEquals( scenario.equals( "noAssertions" ) ? List.of( "default->configured" ) : List.of(),
				fixture.transitions );
		try( var use = fixture.domain.tryAcquire() ) {
			assertNotNull( use, "real abort is safely completed, not retained uncertainty" );
		}
	}

	/**
	 * A chain keeps literal fixture state between members. A different domain may
	 * overlap only after explicit whole-chain isolation, never by label alone.
	 *
	 * @param parallel Chain runner's actual native mode
	 * @param isolated Whether the entire chain is audited for outside overlap
	 * @throws Exception If real native work fails to drain
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void chainDoesNotResetBetweenStateDependentMembers( boolean parallel, boolean isolated )
			throws Exception {
		Fixture fixture = new Fixture();
		Flow a = Creator
				.build( f -> f.meta( m -> m.description( "A" ).tags( t -> t.add( "chain:AB" ) ) )
						.context( new Setting( "chain state" ) ).call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
								.request( new Msg( "write" ) ).response( new Msg( "written" ) ) ) );
		Flow b = Creator
				.build( f -> f.meta( m -> m.description( "B" ).tags( t -> t.add( "chain:AB" ) ) )
						.prerequisite( a ).context( new Setting( "chain state" ) )
						.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "read" ) )
								.response( new Msg( "still written" ) ) ) );
		CountDownLatch first = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		CountDownLatch outside = new CountDownLatch( 1 );
		Run chain = new Run( parallel, List.of( b, a ), r -> {
			r.contextDomain( fixture.domain ).independent( "fixture", f -> true )
					.applicators( fixture.applicator() );
			if( isolated )
				r.isolatedChains( "whole fixture audit", "AB" );
		}, assertion -> {
			assertEquals( "chain state", fixture.value );
			if( assertion.flow() == a ) {
				fixture.writes = 42;
				first.countDown();
				await( release );
			}
			else {
				assertEquals( 42, fixture.writes );
				fixture.writes = 43;
			}
		} );
		Run other = new Run( true, List.of( flow( "outside", null ) ), r -> r
				.contextDomain( new ContextDomain() ).independent( "other fixture", f -> true ),
				x -> outside.countDown() );
		Throwable primary = null;
		try {
			chain.start();
			await( first );
			other.start();
			await( other.prepared );
			if( isolated )
				await( outside );
			else
				assertEquals( 0, other.bodies.get(), "default chain excludes even different domains" );
			try( var resetting = fixture.domain.tryAcquire() ) {
				assertNull( resetting );
			}
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			drain( primary, release::countDown, chain, other );
		}
		assertEquals( 43, fixture.writes );
		assertEquals( List.of( "default->chain state", "chain state->chain state" ),
				fixture.transitions );
	}

	/**
	 * A class finishing is not permission to close the shared fixture while a
	 * different real class execution is still using it.
	 *
	 * @param parallel Second runner's mode
	 * @throws Exception If native work fails to drain
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void afterAllCannotCloseAnotherRunnersFixture( boolean parallel ) throws Exception {
		Fixture fixture = new Fixture();
		CountDownLatch firstDone = new CountDownLatch( 1 );
		CountDownLatch using = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		AtomicInteger closes = new AtomicInteger();
		Run first = new Run( true, List.of( flow( "first class", "first" ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true )
				.applicators( fixture.applicator() ), a -> {
				} );
		first.afterAll = () -> {
			firstDone.countDown();
			await( using );
			try( var closing = fixture.domain.tryAcquire() ) {
				assertNull( closing, "existing owner must defer teardown while another class uses it" );
				if( closing != null )
					closing.change( closes::incrementAndGet );
			}
		};
		Run second = new Run( parallel, List.of( flow( "second class", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true )
				.applicators( fixture.applicator() ), a -> {
					using.countDown();
					await( release );
					assertEquals( 0, closes.get() );
				} );
		Throwable primary = null;
		try {
			first.start();
			await( firstDone );
			second.start();
			await( using );
			first.finish();
			assertEquals( 0, closes.get() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			drain( primary, () -> {
				using.countDown();
				release.countDown();
			}, first, second );
		}
		try( var closing = fixture.domain.tryAcquire() ) {
			assertNotNull( closing );
			closing.change( closes::incrementAndGet );
		}
		assertEquals( 1, closes.get() );
	}

	/**
	 * Synchronous assertion failure has ended use and must not poison a fixture.
	 *
	 * @param parallel Actual native mode
	 * @throws Exception If native work fails to drain
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void safelyCompletedFailureAllowsReuse( boolean parallel ) throws Exception {
		Fixture fixture = new Fixture();
		AssertionError failure = new AssertionError( "ordinary completed assertion" );
		Run failed = new Run( parallel, List.of( flow( "failed", "configured" ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true )
				.applicators( fixture.applicator() ), a -> {
					throw failure;
				} );
		failed.start();
		failed.awaitCompletion();
		assertTrue( failed.failures.contains( failure ) );
		assertEquals( 1, failed.getSummary().getTestsFailedCount() );
		assertNull( fixture.domain.uncertainty() );
		Run reused = new Run( parallel, List.of( flow( "reused", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true )
				.applicators( fixture.applicator() ), a -> assertEquals( "default", fixture.value ) );
		reused.start();
		reused.finish();
	}

	/**
	 * Native FAILED after successful processing is not owner uncertainty. Receipt
	 * publication alone must not retain ownership or prevent the next actual user.
	 *
	 * @param parallel Actual native mode
	 * @throws Exception If either real Launcher fails to drain
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void safelyCompletedOuterCleanupFailureAllowsReuse( boolean parallel ) throws Exception {
		Fixture fixture = new Fixture();
		RuntimeException failure = new IllegalStateException( "ordinary completed outer cleanup" );
		AtomicReference<ContextDomain.Receipt> receipt = new AtomicReference<>();
		AtomicInteger completed = new AtomicInteger();
		Run failed = new Run( parallel, List.of( flow( "completed body", "configured" ) ), r -> r
				.contextDomain( fixture.domain, ( flow, owned ) -> receipt.set( owned ) )
				.independent( "fixture", f -> true ).applicators( fixture.applicator() )
				.listening( new Listener() {
					@Override
					public void flowComplete( Flow flow ) {
						completed.incrementAndGet();
					}
				} ), a -> assertEquals( "configured", fixture.value ) );
		failed.beforeBody = () -> assertNotNull( receipt.get() );
		failed.cleanup = () -> {
			assertEquals( 1, completed.get() );
			throw failure;
		};
		failed.start();
		failed.awaitCompletion();
		assertEquals( List.of( failure ), failed.failures );
		assertEquals( 1, failed.getSummary().getTestsFailedCount() );
		assertEquals( 1, failed.bodies.get() );
		assertNull( fixture.domain.uncertainty() );
		Run reused = new Run( parallel, List.of( flow( "reuse after cleanup", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true )
				.applicators( fixture.applicator() ), a -> assertEquals( "default", fixture.value ) );
		reused.start();
		reused.finish();
	}

	/**
	 * Evidence tied to an old run cannot poison the next actual user even while the
	 * same domain is actively borrowed on the delivering thread.
	 *
	 * @param parallel Actual native mode
	 * @throws Exception If either real Launcher fails to drain
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void releasedReceiptCannotPoisonANewerRun( boolean parallel ) throws Exception {
		Fixture fixture = new Fixture();
		AtomicReference<ContextDomain.Receipt> old = new AtomicReference<>();
		Run first = new Run( parallel, List.of( flow( "old owner", null ) ), r -> r
				.contextDomain( fixture.domain, ( flow, owned ) -> old.set( owned ) )
				.independent( "fixture", f -> true ), a -> {
				} );
		first.start();
		first.finish();
		RuntimeException late = new IllegalStateException( "old owner evidence" );
		Run second = new Run( parallel, List.of( flow( "new owner", "new state" ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true )
				.applicators( fixture.applicator() ), a -> {
					try( var use = fixture.domain.tryAcquire() ) {
						assertNotNull( use );
						assertSame( late, assertThrows( IllegalStateException.class,
								() -> old.get().uncertain( late ) ).getCause() );
						use.change( () -> assertEquals( "new state", fixture.value ) );
					}
					assertNull( fixture.domain.uncertainty() );
				} );
		second.start();
		second.finish();
		try( var use = fixture.domain.tryAcquire() ) {
			assertNotNull( use, "neither the newer grant nor the domain was poisoned" );
		}
	}

	/**
	 * Unsafe domains intentionally cannot be recovered. Isolate the permanent
	 * retained ownership in a bounded child JVM, never reset the shared scope.
	 *
	 * @param mode     Failed owner operation or failed prior-context removal
	 * @param parallel Native mode for the failed removal
	 * @throws Exception If the isolated probe fails
	 */
	@ParameterizedTest
	@CsvSource({ "reset,false", "cleanup,false", "removal,false", "removal,true",
			"publication,false", "publication,true", "publication-chain,false", "publication-chain,true",
			"nested,false", "nested,true", "nested-assertion,false", "nested-assertion,true",
			"before,false", "before,true", "after,false", "after,true",
			"before-cross,false", "before-cross,true", "after-cross,false", "after-cross,true",
			"after-borrowed,false", "after-borrowed,true", "after-single,false", "after-single,true",
			"after-signal,false", "after-signal,true", "operation,false", "operation,true" })
	void uncertainStateNeverPassesToTheNextUser( String mode, boolean parallel ) throws Exception {
		Path output = Files.createTempFile( "flow-context17-" + mode, ".log" );
		Process process = new ProcessBuilder(
				Path.of( System.getProperty( "java.home" ), "bin", "java" ).toString(), "-cp",
				System.getProperty( "surefire.test.class.path", System.getProperty( "java.class.path" ) ),
				ContextFixtureTest.class.getName(), mode, Boolean.toString( parallel ) )
						.redirectErrorStream( true ).redirectOutput( output.toFile() ).start();
		try {
			assertTrue( process.waitFor( 20, TimeUnit.SECONDS ),
					"unsafe fixture probe timed out: " + output );
			assertEquals( 0, process.exitValue(), () -> {
				try {
					return Files.readString( output );
				}
				catch( Exception failure ) {
					return failure.toString();
				}
			} );
		}
		finally {
			if( process.isAlive() )
				process.destroyForcibly();
			// Keep failed evidence; success needs no permanent temporary log.
			if( !process.isAlive() && process.exitValue() == 0 )
				Files.deleteIfExists( output );
		}
	}

	/**
	 * @param args Operation and actual native mode for an isolated unsafe probe
	 * @throws Exception If its native runner does not finish
	 */
	public static void main( String[] args ) throws Exception {
		Fixture fixture = new Fixture( "unsafe-account" );
		RuntimeException failure = new IllegalStateException( "uncertain fixture state" );
		if( args[0].startsWith( "nested" ) ) {
			nestedScopeFailure( fixture, Boolean.parseBoolean( args[1] ),
					args[0].endsWith( "assertion" ) );
			return;
		}
		if( args[0].startsWith( "before" ) || args[0].startsWith( "after" )
				|| args[0].startsWith( "publication" ) ) {
			outerUncertainty( fixture, failure, args[0], Boolean.parseBoolean( args[1] ) );
		}
		else if( args[0].equals( "operation" ) ) {
			AtomicReference<ContextDomain.Receipt> receipt = new AtomicReference<>();
			Run run = new Run( Boolean.parseBoolean( args[1] ), List.of( flow( "damaged", null ) ),
					r -> r.contextDomain( fixture.domain, ( f, owned ) -> receipt.set( owned ) )
							.independent( "fixture", f -> true ),
					a -> {
						var operation = receipt.get().operation();
						try {
							receipt.get().uncertain( failure );
						}
						finally {
							operation.complete();
						}
						operation.complete();
					} );
			run.start();
			run.awaitCompletion();
			assertEquals( 1, run.bodies.get() );
			assertEquals( 1, run.handle.status().owners() );
			assertEquals( "STOPPING", run.handle.status().state().name() );
			assertSame( failure, run.handle.status().cause() );
			assertEquals( 0, run.closes.get(), "operation cessation cannot repair damaged state" );
			assertThrows( IllegalStateException.class, run.handle::close );
		}
		else if( args[0].equals( "removal" ) ) {
			boolean parallel = Boolean.parseBoolean( args[1] );
			Run first = new Run( parallel, List.of( flow( "configured", "configured" ) ), r -> r
					.contextDomain( fixture.domain ).independent( "fixture", f -> true )
					.applicators( fixture.applicator() ), a -> assertEquals( "configured", fixture.value ) );
			first.start();
			first.finish();
			fixture.removalFailure = failure;
			Run removal = new Run( parallel, List.of( flow( "remove", null ), flow( "next", null ) ),
					r -> r
							.contextDomain( fixture.domain ).independent( "fixture", f -> true )
							.applicators( fixture.applicator() ),
					a -> {
						throw new AssertionError( "unsafe body" );
					} );
			removal.start();
			removal.awaitCompletion();
			assertEquals( 0, removal.bodies.get(), "failed removal must stop before SUT entry" );
			assertTrue( removal.failures.contains( failure ), removal.failures::toString );
			assertThrows( IllegalStateException.class, removal.handle::close );
		}
		else {
			try( var use = fixture.domain.tryAcquire() ) {
				assertNotNull( use );
				Runnable partial = () -> {
					fixture.value = "partially changed";
					throw failure;
				};
				assertSame( failure, assertThrows( IllegalStateException.class, () -> {
					if( args[0].equals( "reset" ) )
						use.reset( partial );
					else
						use.change( partial );
				} ) );
			}
		}
		assertSame( failure, fixture.domain.uncertainty() );
		assertThrows( IllegalStateException.class, () -> {
			try( var next = fixture.domain.tryAcquire() ) {
				assertNull( next );
			}
		} );
		var resources = ResourceReservations.shared();
		var next = resources.register( resources.capacity( 1 ),
				new ResourceRules().resources( "different wrapper", f -> true, "unsafe-account" )
						.resolve( null ),
				() -> {
				} );
		try {
			IllegalStateException retained = assertThrows( IllegalStateException.class, () -> {
				try( var grant = next.tryAcquire() ) {
					assertNull( grant );
				}
			} );
			assertSame( failure, retained.getCause() );
		}
		finally {
			next.cancel();
		}
		// An independent actual fixture remains usable after the expected failed probe.
		ContextDomain independent = new ContextDomain( "other-account" );
		try( var use = independent.tryAcquire() ) {
			assertNotNull( use );
			use.change( () -> assertEquals( null, independent.uncertainty() ) );
		}
	}

	/**
	 * Whole-domain ownership includes residue, callbacks and native outer cleanup.
	 * The second wrapper's empty context still has a removal footprint.
	 *
	 * @param parallel Second runner's native mode (each run has a separate pool)
	 * @throws Exception If a real Launcher fails to drain
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void sharedDomainCoversResidueAndNativeOuterCleanup( boolean parallel ) throws Exception {
		Fixture fixture = new Fixture();
		CountDownLatch cleanup = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		List<String> events = new CopyOnWriteArrayList<>();
		Flow firstFlow = Creator.build( f -> f.meta( m -> m.description( "first" ) )
				.context( new Setting( "configured" ) ).residue( new Table() )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "write" ) )
						.response( new Msg( "literal response" ) ) ) );
		Run first = new Run( true, List.of( firstFlow ), r -> r.contextDomain( fixture.domain )
				.independent( "audited fixture", f -> true ).applicators( fixture.applicator() )
				.checkers( new Checker<Table>( Table.class ) {
					@Override
					public Message expected( Table residue ) {
						borrow( fixture.domain );
						events.add( "before" );
						return new Msg( "whole table" );
					}

					@Override
					public byte[] actual( Table residue, List<Assertion> behaviour ) {
						borrow( fixture.domain );
						assertEquals( 1, fixture.writes );
						events.add( "after" );
						return new Msg( "whole table" ).content();
					}
				} ).listening( new Listener() {
					@Override
					public void flowComplete( Flow flow ) {
						borrow( fixture.domain );
						events.add( "callback" );
					}
				} ), a -> {
					borrow( fixture.domain );
					fixture.writes++;
					events.add( "body" );
				} );
		first.cleanup = () -> {
			events.add( "outer" );
			cleanup.countDown();
			await( release );
		};
		Run second = new Run( parallel, List.of( flow( "empty context", null ) ), r -> r
				.contextDomain( fixture.domain ).resources( "another label", f -> true, "other-label" )
				.applicators( fixture.applicator() ), a -> {
					assertEquals( "default", fixture.value );
					assertEquals( 1, fixture.writes, "no automatic reset between runs" );
				} );
		Throwable primary = null;
		try {
			try( var creating = fixture.domain.tryAcquire() ) {
				assertNotNull( creating );
				creating.reset( () -> fixture.writes = 0 );
			}
			first.start();
			await( cleanup );
			assertEquals( List.of( "before", "body", "after", "callback", "outer" ), events );
			second.start();
			await( second.prepared );
			try( var closing = fixture.domain.tryAcquire() ) {
				assertNull( closing, "owner cannot reset/close while native outer cleanup is active" );
			}
			assertEquals( 0, second.bodies.get() );
			assertNotSame( first.pool, second.pool );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			drain( primary, release::countDown, first, second );
		}
		try( var closing = fixture.domain.tryAcquire() ) {
			assertNotNull( closing, "actual native completion returns lifecycle ownership" );
			closing.change( () -> events.add( "close" ) );
		}
		assertEquals( List.of( "default->configured", "configured->default" ), fixture.transitions );
	}

	/**
	 * Two tabs of one session share a handle; separate sessions sharing an account
	 * serialize without merging their applied state. Independent sessions overlap.
	 *
	 * @param sharedSession Whether both wrappers use one physical session
	 * @param sharedAccount Whether separate sessions mutate the same account
	 * @throws Exception If native work fails to drain
	 */
	@ParameterizedTest
	@CsvSource({ "true,false", "false,true", "false,false" })
	void browserSessionsAndAccounts( boolean sharedSession, boolean sharedAccount ) throws Exception {
		Fixture firstFixture = new Fixture( sharedAccount ? "account" : "first-account" );
		Fixture secondFixture = sharedSession ? firstFixture
				: new Fixture( sharedAccount ? "account" : "second-account" );
		CountDownLatch firstEntered = new CountDownLatch( 1 );
		CountDownLatch secondEntered = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		Run first = new Run( true, List.of( flow( "tab one", "first session state" ) ), r -> r
				.contextDomain( firstFixture.domain ).independent( "session", f -> true )
				.applicators( firstFixture.applicator() ), a -> {
					firstEntered.countDown();
					await( release );
					assertEquals( "first session state", firstFixture.value );
				} );
		Run second = new Run( true, List.of( flow( "tab two", "second session state" ) ), r -> r
				.contextDomain( secondFixture.domain ).independent( "session", f -> true )
				.applicators( secondFixture.applicator() ), a -> secondEntered.countDown() );
		Throwable primary = null;
		try {
			first.start();
			await( firstEntered );
			second.start();
			await( second.prepared );
			if( sharedSession || sharedAccount ) {
				try( var use = secondFixture.domain.tryAcquire() ) {
					assertNull( use );
				}
				assertEquals( 0, second.bodies.get() );
			}
			else {
				await( secondEntered );
				second.finish();
				assertEquals( 1, release.getCount(),
						"independent session finished while first remained live" );
			}
			assertNotSame( first.pool, second.pool );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			drain( primary, release::countDown, first, second );
		}
		assertEquals( "second session state", secondFixture.value );
		if( !sharedSession ) {
			assertEquals( List.of( "default->first session state" ), firstFixture.transitions );
			assertEquals( List.of( "default->second session state" ), secondFixture.transitions );
		}
		else
			assertEquals(
					List.of( "default->first session state", "first session state->second session state" ),
					firstFixture.transitions );
	}

	/**
	 * Affinity is verified on actual create/use/reset/close, not inferred from
	 * SAME_THREAD metadata or from the thread that constructed a test object.
	 *
	 * @throws Exception If a real Launcher fails to finish
	 */
	@Test
	void serialLifecycleUsesTheActualOwner() throws Exception {
		AtomicReference<ContextDomain> domain = new AtomicReference<>();
		AtomicReference<Thread> owner = new AtomicReference<>();
		List<String> actions = new CopyOnWriteArrayList<>();
		Run run = new Run( false, List.of( flow( "owned", null ) ), r -> {
			owner.set( Thread.currentThread() );
			domain.set( new ContextDomain( owner.get() ) );
			r.contextDomain( domain.get() ).independent( "fixture", f -> true );
			try( var use = domain.get().tryAcquire() ) {
				assertNotNull( use );
				use.reset( () -> actions.add( "create" ) );
			}
		}, a -> {
			assertSame( owner.get(), Thread.currentThread() );
			try( var use = domain.get().tryAcquire() ) {
				assertNotNull( use, "borrows current native grant" );
				use.change( () -> actions.add( "use" ) );
			}
		} );
		run.afterAll = () -> {
			assertSame( owner.get(), Thread.currentThread() );
			try( var use = domain.get().tryAcquire() ) {
				assertNotNull( use );
				use.reset( () -> actions.add( "reset" ) );
				use.change( () -> actions.add( "close" ) );
			}
		};
		run.start();
		run.finish();
		assertEquals( List.of( "create", "use", "reset", "close" ), actions );
		assertThrows( IllegalStateException.class, () -> {
			try( var wrongThread = domain.get().tryAcquire() ) {
				assertNull( wrongThread );
			}
		} );
	}

	/**
	 * Reject affinity before the fixture's creation action, including serial mode
	 * when its actual factory thread is not the declared owner.
	 *
	 * @param parallel Selected native mode
	 * @throws Exception If the Launcher fails to finish
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void affinityRejectedBeforeCreation( boolean parallel ) throws Exception {
		ContextDomain domain = new ContextDomain( Thread.currentThread() );
		AtomicInteger created = new AtomicInteger();
		Run run = new Run( parallel, List.of( flow( "not entered", null ) ), r -> {
			r.contextDomain( domain );
			try( var use = domain.tryAcquire() ) {
				assertNotNull( use );
				use.reset( created::incrementAndGet );
			}
		}, a -> created.incrementAndGet() );
		run.start();
		run.awaitCompletion();
		assertEquals( 0, created.get() );
		assertEquals( 0, run.bodies.get() );
		assertEquals( 0, run.getSummary().getTestsStartedCount() );
		assertTrue( run.failures.stream().anyMatch( t -> t.getMessage().contains(
				parallel ? "physical fixture affinity" : "actual owner thread" ) ),
				run.failures::toString );
	}

	private static void borrow( ContextDomain domain ) {
		try( var use = domain.tryAcquire() ) {
			assertNotNull( use, "reuse the current whole grant instead of reacquiring resources" );
		}
	}

	private static void outerUncertainty( Fixture fixture, RuntimeException failure, String phase,
			boolean parallel ) throws Exception {
		boolean publication = phase.startsWith( "publication" );
		AtomicReference<ContextDomain.Receipt> receipt = new AtomicReference<>();
		AtomicInteger completed = new AtomicInteger();
		Flow first = Creator.build( f -> f.meta( m -> m.description( "first" )
				.tags( tags -> tags.add( "chain:owned" ) ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "first" ) )
						.response( new Msg( "literal response" ) ) ) );
		Flow next = Creator.build( f -> f.meta( m -> m.description( "next" )
				.tags( tags -> tags.add( "chain:owned" ) ) ).prerequisite( first )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Msg( "next" ) )
						.response( new Msg( "literal response" ) ) ) );
		Run run = new Run( parallel,
				phase.endsWith( "single" ) ? List.of( first ) : List.of( first, next ), r -> r
						.contextDomain( fixture.domain, ( flow, owned ) -> {
							if( flow == first )
								receipt.set( owned );
							if( publication && (flow == first) != phase.endsWith( "chain" ) ) {
								owned.uncertain( failure );
								throw failure;
							}
						} ).independent( "fixture", f -> true )
						.resources( "later member footprint", f -> phase.endsWith( "single" ) || f == next,
								"chain-extra" )
						.isolatedChains( "whole fixture", "owned" ).listening( new Listener() {
							@Override
							public void flowComplete( Flow flow ) {
								assertSame( first, flow );
								completed.incrementAndGet();
							}
						} ),
				a -> {
					assertSame( first, a.flow(),
							"uncertainty must stop the chain even without a pending competing request" );
					if( phase.endsWith( "borrowed" ) ) {
						try( var use = fixture.domain.tryAcquire() ) {
							assertNotNull( use );
							receipt.set( use.receipt() );
						}
					}
				} );
		Runnable evidence = () -> {
			assertNotNull( receipt.get(), "receipt must precede native invocation, not core entry" );
			if( phase.endsWith( "cross" ) ) {
				ContextDomain.Receipt owned = receipt.get();
				Thread nativeThread = Thread.currentThread();
				FutureTask<Void> delivery = new FutureTask<>( () -> {
					assertNotSame( nativeThread, Thread.currentThread() );
					owned.uncertain( failure );
					return null;
				} );
				Thread diagnostic = new Thread( delivery, "fixture-owner-evidence" );
				diagnostic.start();
				try {
					delivery.get( 5, TimeUnit.SECONDS );
					diagnostic.join( 1000 );
					assertTrue( !diagnostic.isAlive() );
				}
				catch( Exception problem ) {
					throw new AssertionError( problem );
				}
			}
			else
				receipt.get().uncertain( failure );
			if( !phase.endsWith( "signal" ) )
				throw failure;
		};
		if( phase.startsWith( "before" ) )
			run.beforeBody = evidence;
		else if( !publication )
			run.cleanup = evidence;
		run.start();
		run.awaitCompletion();
		assertSame( failure, fixture.domain.uncertainty(), run.failures::toString );
		if( !phase.endsWith( "signal" ) )
			assertTrue( run.failures.contains( failure ), run.failures::toString );
		int processed = phase.startsWith( "before" ) || phase.equals( "publication" ) ? 0 : 1;
		assertEquals( processed, run.bodies.get() );
		assertEquals( processed, completed.get(),
				"actual completed processing is distinct from subsequent native FAILED" );
		assertEquals( publication ? processed : 1, run.getSummary().getTestsStartedCount() );
		assertEquals( phase.endsWith( "signal" ) || publication ? 0 : 1,
				run.getSummary().getTestsFailedCount() );
		assertEquals( publication ? processed : phase.endsWith( "signal" ) ? 1 : 0,
				run.getSummary().getTestsSucceededCount() );
		assertSame( failure, assertThrows( IllegalStateException.class, run.handle::close ).getCause(),
				"owned run stops with the original uncertainty before finalization" );
		var resources = ResourceReservations.shared();
		var competing = resources.register( resources.capacity( 1 ), new ResourceRules()
				.resources( "other whole-chain user", f -> true, "chain-extra" ).resolve( null ), () -> {
				} );
		try {
			assertSame( failure, assertThrows( IllegalStateException.class, () -> {
				try( var grant = competing.tryAcquire() ) {
					assertNull( grant );
				}
			} ).getCause(), "retain the exact whole grant, not just the context footprint" );
		}
		finally {
			competing.cancel();
		}
		Run conflicting = new Run( !parallel, List.of( flow( "conflicting whole-grant reuse", null ) ),
				r -> r.contextDomain( new ContextDomain() )
						.resources( "another physical wrapper", f -> true, "chain-extra" ),
				a -> {
				} );
		conflicting.start();
		conflicting.awaitCompletion();
		assertEquals( 0, conflicting.bodies.get() );
		assertEquals( 0, conflicting.getSummary().getTestsStartedCount() );
		assertTrue( conflicting.failures.stream().anyMatch( t -> t.getCause() == failure ),
				conflicting.failures::toString );
	}

	private static void nestedScopeFailure( Fixture fixture, boolean parallel, boolean assertion )
			throws Exception {
		Flow flow = flow( "nested owner", null );
		AssertionError primary = new AssertionError( "actual completed assertion failure" );
		AtomicReference<ContextDomain.Use> nested = new AtomicReference<>();
		AtomicReference<Result> observed = new AtomicReference<>();
		AtomicInteger actions = new AtomicInteger();
		Run run = new Run( parallel, List.of( flow ), r -> r
				.contextDomain( fixture.domain ).resources( "whole fixture", f -> true, "nested-extra" ),
				a -> {
					// Native interceptor cleanup owns this token below, deliberately after
					// the prepared scope attempts to close. No assertion precedes that guard.
					nested.set( fixture.domain.tryAcquire() );
					nested.get().change( actions::incrementAndGet );
					if( assertion )
						throw primary;
				} );
		run.cleanup = () -> {
			try( var remaining = nested.get() ) {
				assertNotNull( remaining );
				// Only parallel exposes the shared processing History for observation.
				if( parallel )
					observed.set( run.handle.history().get( flow ) );
			}
		};
		run.start();
		run.awaitCompletion();
		if( parallel )
			assertEquals( assertion ? Result.UNEXPECTED : Result.SUCCESS, observed.get(),
					"borrowed-scope close is not processing and cannot fabricate History.ERROR" );
		assertEquals( 1, run.bodies.get() );
		assertEquals( 1, actions.get() );
		assertEquals( 1, run.getSummary().getTestsStartedCount() );
		assertEquals( 1, run.getSummary().getTestsFailedCount() );
		Throwable scope = fixture.domain.uncertainty();
		assertNotNull( scope, "unclosed nested fixture use must retain its exact native grant" );
		assertTrue( scope.getMessage().contains( "owning thread in order" ), scope::toString );
		assertTrue( run.failures.stream().anyMatch( t -> contains( t, scope ) ),
				run.failures::toString );
		if( assertion ) {
			assertTrue( run.failures.contains( primary ), run.failures::toString );
			assertTrue( List.of( primary.getSuppressed() ).contains( scope ) );
		}
		assertSame( scope, assertThrows( IllegalStateException.class, run.handle::close ).getCause() );
		var resources = ResourceReservations.shared();
		var competing = resources.register( resources.capacity( 1 ), new ResourceRules()
				.resources( "another wrapper", f -> true, "nested-extra" ).resolve( null ), () -> {
				} );
		try {
			assertSame( scope, assertThrows( IllegalStateException.class, () -> {
				try( var grant = competing.tryAcquire() ) {
					assertNull( grant );
				}
			} ).getCause() );
		}
		finally {
			competing.cancel();
		}
		Run conflicting = new Run( !parallel, List.of( flow( "nested conflict", null ) ), r -> r
				.resources( "same physical resource", f -> true, "nested-extra" ), a -> {
				} );
		conflicting.start();
		conflicting.awaitCompletion();
		assertEquals( 0, conflicting.getSummary().getTestsStartedCount() );
		assertEquals( 0, conflicting.bodies.get() );
		assertTrue( conflicting.failures.stream().anyMatch( t -> contains( t, scope ) ),
				conflicting.failures::toString );
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 5, TimeUnit.SECONDS ), "fixture gate timed out" );
		}
		catch( InterruptedException failure ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( failure );
		}
	}

	private static void drain( Throwable primary, Run... runs ) throws Exception {
		drain( primary, () -> {
		}, runs );
	}

	private static void drain( Throwable primary, Runnable release, Run... runs ) throws Exception {
		Throwable failure = primary;
		try {
			// Withdraw every waiter before releasing any holder, including failure
			// during setup before the test reaches its inner guarded block.
			if( primary != null ) {
				for( Run run : runs )
					stop( primary, run );
			}
		}
		finally {
			release.run();
		}
		for( Run run : runs ) {
			try {
				if( primary == null )
					run.finish();
				else if( run.execution != null ) {
					run.awaitCompletion();
					for( Throwable cleanup : run.failures )
						if( primary != cleanup )
							primary.addSuppressed( cleanup );
				}
			}
			catch( Throwable cleanup ) {
				if( failure == null )
					failure = cleanup;
				else if( failure != cleanup )
					failure.addSuppressed( cleanup );
				stop( failure, run );
				if( run.execution != null ) {
					try {
						run.awaitCompletion();
					}
					catch( Throwable remaining ) {
						if( failure != remaining )
							failure.addSuppressed( remaining );
					}
				}
			}
		}
		if( primary == null && failure != null ) {
			if( failure instanceof Exception exception )
				throw exception;
			throw (Error) failure;
		}
	}

	private static void stop( Throwable primary, Run run ) {
		try {
			run.stop( primary );
		}
		catch( Throwable cleanup ) {
			if( primary != cleanup )
				primary.addSuppressed( cleanup );
		}
	}

	/**
	 * An assertion before any admission still cancels the prepared request; the
	 * original failure survives and the same actual domain remains usable.
	 *
	 * @param parallel Waiting runner's native mode
	 * @param early    Fail during setup, before reaching the inner guarded block
	 * @throws Exception If the real Launcher fails to drain
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "false,true", "true,true" })
	void failedProbeStopsZeroEntryPreparation( boolean parallel, boolean early ) throws Exception {
		Fixture fixture = new Fixture();
		CountDownLatch entered = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		CountDownLatch waiterReturned = new CountDownLatch( 1 );
		Run holder = new Run( true, List.of( flow( "holder", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true ), a -> {
					entered.countDown();
					await( release );
				} );
		Run waiting = new Run( parallel, List.of( flow( "waiting", null ) ), r -> r
				.contextDomain( fixture.domain ).independent( "fixture", f -> true ), a -> {
				} );
		AssertionError injected = new AssertionError( "failed before admission" );
		IllegalStateException secondary = new IllegalStateException( "safe waiter cleanup failed" );
		if( early )
			waiting.streamCleanup = () -> {
				throw secondary;
			};
		// Make release-before-stop deterministic: the holder cannot finish until
		// the real waiting Launcher has reached AfterAll, not just a helper drain.
		holder.afterAll = () -> await( waiterReturned );
		waiting.afterAll = waiterReturned::countDown;
		assertSame( injected, assertThrows( AssertionError.class, () -> {
			Throwable primary = null;
			try {
				holder.start();
				await( entered );
				waiting.start();
				await( waiting.prepared );
				if( early )
					throw injected;
				try {
					throw injected;
				}
				finally {
					drain( injected, waiting );
				}
			}
			catch( Throwable failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				drain( primary, () -> {
					if( waiting.execution == null )
						waiterReturned.countDown();
					release.countDown();
				}, holder, waiting );
			}
		} ) );
		assertEquals( 0, waiting.bodies.get() );
		assertEquals( 0, waiting.getSummary().getTestsStartedCount() );
		assertEquals( 1, waiting.closes.get() );
		assertTrue( holder.execution.isDone() && waiting.execution.isDone() );
		assertTrue( !holder.launcherThread.isAlive() && !waiting.launcherThread.isAlive() );
		if( early ) {
			// Native stream cleanup may already be suppressed on the incomplete
			// consumption diagnostic. Preserve that tree rather than flattening it.
			assertTrue(
					Arrays.stream( injected.getSuppressed() )
							.anyMatch( cause -> contains( cause, secondary ) ),
					"drain preserves the original cleanup failure beside the primary probe failure" );
		}
		try( var use = fixture.domain.tryAcquire() ) {
			assertNotNull( use, "failed probe left no pending or borrowed ownership" );
		}
	}

	/**
	 * A new wrapper and runner must remove the first runner's context, not invent a
	 * fresh baseline when the next flow declares no context.
	 *
	 * @throws Exception If a real Launcher fails to finish
	 */
	@Test
	void sharedStateOutlivesRunnerAndApplicator() throws Exception {
		sharedState( false, false, false );
	}

	/**
	 * Different labels cannot erase the domain's previous-context removal
	 * footprint.
	 *
	 * @param firstParallel  First runner's native mode
	 * @param secondParallel Second runner's native mode
	 * @throws Exception If a real Launcher fails to finish
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void domainSurvivesDifferentResourceLabels( boolean firstParallel, boolean secondParallel )
			throws Exception {
		sharedState( firstParallel, secondParallel, true );
	}

	private void sharedState( boolean firstParallel, boolean secondParallel, boolean labels )
			throws Exception {
		Fixture fixture = new Fixture();
		Run first = new Run( firstParallel, List.of( flow( "first", "configured" ) ),
				r -> {
					r.contextDomain( fixture.domain ).applicators( fixture.applicator() );
					if( labels )
						r.resources( "wrapper one", f -> true, "context-first" );
				},
				a -> assertEquals( "configured", fixture.value ) );
		Run second = new Run( secondParallel, List.of( flow( "second", null ) ),
				r -> {
					r.contextDomain( fixture.domain ).applicators( fixture.applicator() );
					if( labels )
						r.resources( "wrapper two", f -> true, "context-second" );
				},
				a -> assertEquals( "default", fixture.value ) );
		first.start();
		first.finish();
		assertEquals( "configured", fixture.value, "runner completion is not a fixture reset" );
		second.start();
		second.finish();
		assertEquals( List.of( "default->configured", "configured->default" ), fixture.transitions );
	}

	private static Flow flow( String name, String context ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) ).call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
					.request( new Msg( name ) ).response( new Msg( "literal response" ) ) );
			if( context != null )
				f.context( new Setting( context ) );
		} );
	}

	private static final class Fixture {
		private final ContextDomain domain;
		private final List<String> transitions = new ArrayList<>();
		private String value = "default";
		private int writes;
		private RuntimeException removalFailure;

		private Fixture( String... keys ) {
			domain = new ContextDomain( keys );
		}

		private Applicator<Setting> applicator() {
			return new Applicator<>( Setting.class, 1 ) {
				@Override
				public Comparator<Setting> order() {
					return Comparator.comparing( Setting::name );
				}

				@Override
				public void transition( Setting from, Setting to ) {
					assertEquals( value, from == null ? "default" : from.name() );
					if( to == null && removalFailure != null ) {
						value = "partially removed";
						throw removalFailure;
					}
					String next = to == null ? "default" : to.name();
					transitions.add( value + "->" + next );
					value = next;
				}
			};
		}
	}

	private record Setting(String name) implements Context {
		@Override
		public Set<Actor> domain() {
			return Set.of( Actrs.BEN );
		}

		@Override
		public Context child() {
			return new Setting( name );
		}
	}

	private record Table() implements Residue {
		@Override
		public String name() {
			return "whole table";
		}

		@Override
		public Residue child() {
			return new Table();
		}
	}
}
