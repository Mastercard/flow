package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.NativeResourceAdmissionTest.Run;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.ContextDomain;
import com.mastercard.test.flow.assrt.ExecutionStatus;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Operation;
import org.junit.jupiter.api.DynamicTest;

/**
 * Stop configuration and required cleanup through serial and native execution.
 */
class FlowExecutionStopBudgetTest {
	/** Receipt delivery cannot wait for native handoff on its own stack. */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void ownershipCallbackCloseRejectsWithoutWaitingForHandoff( boolean parallel ) throws Exception {
		var owner = new AtomicReference<Run>();
		var rejected = new AtomicReference<Throwable>();
		var observed = new AtomicReference<ExecutionStatus>();
		ContextDomain domain = new ContextDomain();
		Run run = new Run( parallel, List.of( new Mdl().flows().findFirst().orElseThrow() ),
				r -> r.independent( "empty", f -> true ).contextDomain( domain, ( f, receipt ) -> {
					FlowExecution handle = owner.get().handle;
					var failure = assertThrows( IllegalStateException.class, handle::close );
					rejected.set( failure );
					observed.set( handle.status() );
					throw failure;
				} ), a -> {
				} );
		owner.set( run );
		run.stopBudget = Duration.ofMillis( 500 );
		Throwable primary = null;
		try {
			run.start();
			run.awaitCompletion();
			assertNotNull( rejected.get() );
			var callback = observed.get();
			assertTrue( callback.stopBudgetMiss().isEmpty(),
					"receipt close must not spend its own budget" );
			assertEquals( 1, callback.owners(),
					"close cannot invent unused proof before callback return" );
			assertEquals( 0, callback.entered() );
			assertEquals( 0, callback.completed() );
			assertEquals( parallel ? 0 : -1, callback.nativeTerminals() );
			assertEquals( 0, run.getSummary().getTestsStartedCount() );
			assertEquals( 0, run.bodies.get() );
			assertEquals( 0, run.handle.status().owners() );
			assertEquals( "QUIESCENT", run.handle.status().state().name() );
			assertEquals( 1, run.closes.get() );
		}
		catch( Exception | Error failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( run.execution != null )
				cleanup( primary, run::awaitCompletion );
		}
		try( var reuse = domain.tryAcquire() ) {
			assertNotNull( reuse, "proven-unused admission releases the fixture" );
		}
	}

	/** Serial capacity can already be zero when late proof reaches the owner. */
	@ParameterizedTest
	@CsvSource({ "10,9,false", "10,10,true", "30000000000,30000000000,true" })
	void serialLastOperationProofPrecedesLateStatus( long nanos, long elapsed, boolean missed ) {
		AtomicLong clock = new AtomicLong( -20 );
		var proof = new java.util.concurrent.atomic.AtomicReference<Operation>();
		FlowExecution execution = new FlowExecution( () -> {
		}, clock::get );
		execution.enterFactory();
		if( nanos != 30_000_000_000L )
			execution.stopBudget( Duration.ofNanos( nanos ) );
		var flow = new Mdl().flows().findFirst().orElseThrow();
		Stream<DynamicNode> original = execution
				.flocessor( "serial proof", PreparedFlowLifecycleTest.model( flow ) )
				.system( State.LESS, Actrs.BEN )
				.contextDomain( new ContextDomain(), ( f, receipt ) -> proof.set( receipt.operation() ) )
				.independent( "fixture", f -> true )
				.behaviour( a -> a.actual().response( a.expected().response().content() ) ).tests();
		execution.leaveFactory();
		Stream<DynamicNode> consumed = execution.consume( original );
		Throwable primary = null;
		try {
			consumed.forEach( node -> {
				try {
					((DynamicTest) node).getExecutable().execute();
				}
				catch( Throwable failure ) {
					throw new AssertionError( failure );
				}
			} );
			execution.stop( new IllegalStateException( "first" ) );
			clock.addAndGet( elapsed );
			proof.get().complete(); // No status observation between Stop and this proof.
			assertThrows( IllegalStateException.class, consumed::close );
			clock.addAndGet( nanos * 2 );
			var status = execution.status();
			assertEquals( "QUIESCENT", status.state().name() );
			assertEquals( 0, status.owners() );
			assertEquals( missed, status.stopBudgetMiss().isPresent() );
			if( missed ) {
				var miss = status.stopBudgetMiss().orElseThrow();
				assertEquals( Duration.ofNanos( nanos ), miss.budget() );
				assertEquals( 0, miss.operations() );
				assertEquals( 0, miss.owners() );
				assertEquals( 0, miss.pendingHandoffs() );
				assertEquals( -1, miss.pendingNative() );
				assertEquals( List.of( flow.meta().id() ), miss.affected() );
			}
		}
		catch( RuntimeException | Error failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			try {
				if( proof.get() != null )
					proof.get().complete();
				clock.addAndGet( nanos );
				consumed.close();
			}
			catch( RuntimeException | Error failure ) {
				if( primary == null )
					throw failure;
				if( primary != failure )
					primary.addSuppressed( failure );
			}
		}
	}

	/** A native outer interceptor cannot wait for its own terminal callback. */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void nativeOuterCleanupCloseNeverWaitsForItsOwnReturn( boolean parallel ) throws Exception {
		Run run = new Run( parallel, List.of( new Mdl().flows().findFirst().orElseThrow() ),
				r -> r.independent( "empty", f -> true ), a -> {
				} );
		run.stopBudget = Duration.ofMillis( 50 );
		run.cleanup = () -> {
			assertThrows( IllegalStateException.class, run.handle::close );
			assertTrue( run.handle.status().stopBudgetMiss().isEmpty(),
					"no self-wait for native cleanup" );
		};
		run.start();
		run.awaitCompletion();
		assertEquals( 1, run.getSummary().getTestsSucceededCount() );
		assertEquals( 0, run.getSummary().getTestsFailedCount() );
		assertEquals( 1, run.closes.get() );
	}

	/**
	 * Both actual factory modes freeze the model-free handle before rule
	 * evaluation.
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void freezePrecedesResourceResolution( boolean parallel ) throws Exception {
		var owner = new java.util.concurrent.atomic.AtomicReference<Run>();
		var evaluated = new java.util.concurrent.atomic.AtomicInteger();
		Run run = new Run( parallel, List.of( new Mdl().flows().findFirst().orElseThrow() ),
				r -> r.independent( "freeze probe", f -> {
					evaluated.incrementAndGet();
					assertThrows( IllegalStateException.class,
							() -> owner.get().handle.stopBudget( Duration.ofSeconds( 2 ) ) );
					return true;
				} ), a -> {
				} );
		owner.set( run );
		run.stopBudget = Duration.ofNanos( 1 );
		run.start();
		run.finish();
		assertEquals( 1, evaluated.get() );
		assertThrows( IllegalStateException.class,
				() -> run.handle.stopBudget( Duration.ofSeconds( 1 ) ) );
		assertTrue( run.handle.status().stopBudgetMiss().isEmpty() );
	}

	/**
	 * Timely original cleanup wakes a real parked close without spending its
	 * budget.
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void actualParkThenCleanupProofWakesImmediately( boolean parallel ) throws Exception {
		CountDownLatch cleanup = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		Run run = new Run( parallel, List.of(), r -> {
		}, a -> {
		} );
		run.stopBudget = Duration.ofSeconds( 30 );
		run.streamCleanup = () -> {
			cleanup.countDown();
			try {
				assertTrue( release.await( 5, TimeUnit.SECONDS ) );
			}
			catch( InterruptedException failure ) {
				Thread.currentThread().interrupt();
				throw new AssertionError( failure );
			}
		};
		FutureTask<Throwable> close = new FutureTask<>(
				() -> assertThrows( IllegalStateException.class, run.handle::close ) );
		Thread closer = new Thread( close, "actual-owner-close" );
		Throwable primary = null;
		try {
			run.start();
			assertTrue( cleanup.await( 3, TimeUnit.SECONDS ) );
			closer.start();
			long start = System.nanoTime();
			while( closer.isAlive() && closer.getState() != Thread.State.TIMED_WAITING
					&& System.nanoTime() - start < TimeUnit.SECONDS.toNanos( 2 ) )
				Thread.yield();
			assertEquals( Thread.State.TIMED_WAITING, closer.getState() );
			release.countDown();
			assertFalse( close.get( 2, TimeUnit.SECONDS ).getMessage().contains( "budget missed" ) );
		}
		catch( Exception | Error failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			release.countDown();
			cleanup( primary, run::awaitCompletion, () -> {
				closer.interrupt(); // This test owns the closer, never a Jupiter pool worker.
				closer.join( 2000 );
				assertFalse( closer.isAlive() );
			} );
		}
		assertEquals( "QUIESCENT", run.handle.status().state().name() );
		assertTrue( run.handle.status().stopBudgetMiss().isEmpty() );
		assertEquals( 1, run.closes.get() );
	}

	/**
	 * Existing real Launcher cleanup is independent progress for a close caller.
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void realCloseWaitsForOriginalCleanupAndRecordsExpiry( boolean parallel ) throws Exception {
		CountDownLatch cleanup = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		Run run = new Run( parallel, List.of(), r -> {
		}, a -> {
		} );
		run.stopBudget = Duration.ofMillis( 50 );
		run.streamCleanup = () -> {
			cleanup.countDown();
			try {
				assertTrue( release.await( 5, TimeUnit.SECONDS ) );
			}
			catch( InterruptedException failure ) {
				Thread.currentThread().interrupt();
				throw new AssertionError( failure );
			}
		};
		Throwable cause = new IllegalStateException( "external Stop during owned cleanup" );
		Throwable primary = null;
		try {
			run.start();
			assertTrue( cleanup.await( 3, TimeUnit.SECONDS ) );
			run.handle.stop( cause );
			var failure = assertThrows( IllegalStateException.class, run.handle::close );
			assertTrue( failure.getMessage().contains( "budget missed" ), failure::toString );
			var miss = run.handle.status().stopBudgetMiss().orElseThrow();
			assertEquals( Duration.ofMillis( 50 ), miss.budget() );
			assertEquals( 1, miss.cleanup() );
			assertEquals( 0, miss.pendingBodies() );
			assertSame( cause, miss.cause() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			release.countDown();
			try {
				run.awaitCompletion();
			}
			catch( Throwable failure ) {
				if( primary == null )
					throw failure;
				if( primary != failure )
					primary.addSuppressed( failure );
			}
		}
		assertEquals( "QUIESCENT", run.handle.status().state().name() );
		assertTrue( run.handle.status().stopBudgetMiss().isPresent() );
		assertEquals( 1, run.closes.get() );
		assertEquals( 0, run.getSummary().getTestsStartedCount() );
	}

	/** The original stream remains owned until its cleanup actually returns. */
	@ParameterizedTest
	@CsvSource({ "-5,9,false", "-5,10,true", "9223372036854775803,10,true" })
	void cleanupCrossingDeadlineIsNotSealedEarly( long origin, long elapsed, boolean missed ) {
		AtomicLong clock = new AtomicLong( origin );
		FlowExecution execution = new FlowExecution( () -> {
		}, clock::get );
		execution.enterFactory();
		assertSame( execution, execution.stopBudget( Duration.ofNanos( 10 ) ) );
		Throwable cause = new IllegalStateException( "original Stop" );
		Stream<DynamicNode> original = empty( execution ).onClose( () -> {
			execution.stop( cause );
			clock.addAndGet( elapsed );
			execution.close(); // No wait for this cleanup's own return.
		} );
		execution.leaveFactory();
		Stream<DynamicNode> consumed = execution.consume( original );
		assertEquals( 0, consumed.count() );
		var failure = assertThrows( IllegalStateException.class, consumed::close );
		assertEquals( missed, failure.getMessage().contains( "budget missed" ) );
		clock.set( origin + 1000 );
		var status = execution.status();
		assertSame( cause, status.cause() );
		assertEquals( "QUIESCENT", status.state().name() );
		assertEquals( missed, status.stopBudgetMiss().isPresent() );
		if( missed )
			assertEquals( 1, status.stopBudgetMiss().orElseThrow().cleanup() );
		assertEquals( -1, status.nativeTerminals() );
	}

	/**
	 * No model is needed to configure; tests() entry freezes even before
	 * resolution.
	 */
	@Test
	void configurationIsModelFreeAndFrozen() {
		FlowExecution execution = new FlowExecution( () -> {
		} );
		assertThrows( IllegalStateException.class,
				() -> execution.stopBudget( Duration.ofNanos( 1 ) ) );
		execution.enterFactory();
		execution.stopBudget( Duration.ofNanos( 1 ) );
		assertThrows( NullPointerException.class, () -> execution.stopBudget( null ) );
		assertThrows( IllegalArgumentException.class, () -> execution.stopBudget( Duration.ZERO ) );
		assertThrows( IllegalArgumentException.class,
				() -> execution.stopBudget( Duration.ofNanos( -1 ) ) );
		assertThrows( ArithmeticException.class,
				() -> execution.stopBudget( Duration.ofSeconds( Long.MAX_VALUE ) ) );
		Stream<DynamicNode> original = empty( execution );
		assertThrows( IllegalStateException.class,
				() -> execution.stopBudget( Duration.ofSeconds( 1 ) ) );
		execution.leaveFactory();
		try( Stream<DynamicNode> consumed = execution.consume( original ) ) {
			assertEquals( 0, consumed.count() );
		}
	}

	/** Healthy execution neither samples the clock nor adds a fixed grace. */
	@Test
	void healthyExecutionNeverReadsBudgetClock() {
		FlowExecution execution = new FlowExecution( () -> {
		}, () -> {
			throw new AssertionError( "healthy clock read" );
		} );
		execution.enterFactory();
		Stream<DynamicNode> original = empty( execution );
		execution.leaveFactory();
		try( Stream<DynamicNode> consumed = execution.consume( original ) ) {
			assertEquals( 0, consumed.count() );
		}
		assertTrue( execution.status().stopBudgetMiss().isEmpty() );
		execution.close();
	}

	private static void cleanup( Throwable primary,
			org.junit.jupiter.api.function.Executable... actions ) {
		Throwable first = primary;
		for( var action : actions ) {
			try {
				action.execute();
			}
			catch( Throwable failure ) {
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
			if( first != null )
				throw new AssertionError( first );
		}
	}

	private static Stream<DynamicNode> empty( FlowExecution execution ) {
		return execution.flocessor( "empty budget", PreparedFlowLifecycleTest.model() )
				.system( State.LESS, Actrs.BEN ).tests();
	}
}
