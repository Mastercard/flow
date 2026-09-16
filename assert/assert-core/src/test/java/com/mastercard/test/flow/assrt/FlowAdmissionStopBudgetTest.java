package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.assrt.resource.ResourceRules;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Operation;

/** Owner-observed stop timing at the existing admission seam. */
class FlowAdmissionStopBudgetTest {
	/** Capacity proof is not completion of already registered owner cleanup. */
	@Test
	void closeWaitsBetweenFinalProofAndRegisteredCleanup() throws Exception {
		var clockProbe = new AtomicReference<Runnable>();
		FlowAdmission run = prepared( () -> {
			Runnable probe = clockProbe.getAndSet( null );
			if( probe != null )
				probe.run();
			return 0;
		}, Duration.ofSeconds( 30 ) );
		var grant = run.reservation( 0 );
		Operation operation = grant.operation();
		Throwable cause = new IllegalStateException( "stop before final proof" );
		CountDownLatch cleanupEntered = new CountDownLatch( 1 );
		CountDownLatch releaseCleanup = new CountDownLatch( 1 );
		Runnable requiredCleanup = () -> {
			assertFalse( Thread.holdsLock( run.history() ), "cleanup outside owner bookkeeping" );
			cleanupEntered.countDown();
			try {
				assertTrue( releaseCleanup.await( 5, TimeUnit.SECONDS ) );
			}
			catch( InterruptedException failure ) {
				Thread.currentThread().interrupt();
				throw new AssertionError( failure );
			}
			run.release();
		};
		FutureTask<Void> proof = new FutureTask<>( () -> {
			operation.complete();
			return null;
		} );
		Thread prover = new Thread( proof, "final-operation-proof" );
		FutureTask<Throwable> close = new FutureTask<>( () -> {
			// Hold the existing owner monitor through the close decision. The clock
			// orders real capacity mutation before its outside-lock owner notification.
			synchronized( run.history() ) {
				clockProbe.set( () -> {
					prover.start();
					long start = System.nanoTime();
					while( (!grant.released() || prover.getState() != Thread.State.BLOCKED)
							&& System.nanoTime() - start < TimeUnit.SECONDS.toNanos( 2 ) )
						Thread.yield();
					assertTrue( grant.released(), "actual final operation released capacity" );
					assertEquals( Thread.State.BLOCKED, prover.getState(),
							"owner notification awaits History" );
					assertTrue( run.disposable() );
					assertEquals( 1, cleanupEntered.getCount(), "registered cleanup is still unclaimed" );
				} );
				return assertThrows( IllegalStateException.class, run::close );
			}
		} );
		Thread closer = new Thread( close, "proof-gap-close" );
		Throwable primary = null;
		try {
			run.stop( cause );
			run.processed( 0, History.Result.SUCCESS, null );
			run.finished( "leaf", FlowAdmission.Outcome.SUCCESSFUL, null );
			run.enclosingFinished( cause );
			run.whenDrained( requiredCleanup );
			closer.start();
			assertTrue( cleanupEntered.await( 3, TimeUnit.SECONDS ) );
			parked( closer );
			assertFalse( close.isDone(), "close must wait for required cleanup, not just zero capacity" );
			releaseCleanup.countDown();
			assertSame( cause, close.get( 2, TimeUnit.SECONDS ).getCause() );
			proof.get( 2, TimeUnit.SECONDS );
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertTrue( run.status().stopBudgetMiss().isEmpty() );
		}
		catch( Exception | Error failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			releaseCleanup.countDown();
			cleanup( primary, operation::complete, () -> {
				if( prover.getState() != Thread.State.NEW )
					proof.get( 3, TimeUnit.SECONDS );
			}, () -> {
				closer.interrupt();
				closer.join( 2000 );
				assertFalse( closer.isAlive() );
			}, () -> {
				prover.join( 2000 );
				assertFalse( prover.isAlive() );
			}, () -> finish( run, null ) );
		}
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

	/**
	 * Bounded diagnostics retain the original cause without rendering user code.
	 */
	@Test
	void missedCloseDoesNotRenderTheStopCause() {
		AtomicLong clock = new AtomicLong();
		FlowAdmission run = prepared( clock::get, Duration.ofNanos( 1 ) );
		Throwable cause = new IllegalStateException( "original Stop" ) {
			@Override
			public String toString() {
				throw new AssertionError( "must not render the Stop cause" );
			}
		};
		try {
			run.stop( cause );
			clock.incrementAndGet();
			var reported = assertThrows( IllegalStateException.class, run::close );
			assertSame( cause, reported.getCause() );
			assertTrue( reported.getMessage().contains( "budget missed" ) );
			assertTrue( reported.getMessage().contains( "pendingBodies=1" ) );
			assertTrue( reported.getMessage().contains( "budget flow []" ) );
		}
		finally {
			run.processed( 0, History.Result.SUCCESS, null );
			run.finished( "leaf", FlowAdmission.Outcome.SUCCESSFUL, null );
			run.enclosingFinished( cause );
			run.release();
		}
	}

	/**
	 * Real proof wakes the parked close immediately; interruption is not expiry.
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void parkedCloseWakesOnProofOrInterruption( boolean interrupt ) throws Exception {
		AtomicLong clock = new AtomicLong();
		FlowAdmission run = prepared( clock::get, Duration.ofSeconds( 30 ) );
		Operation operation = run.reservation( 0 ).operation();
		Throwable cause = new IllegalStateException( "stop" );
		run.stop( cause );
		run.processed( 0, History.Result.SUCCESS, null );
		run.finished( "leaf", FlowAdmission.Outcome.SUCCESSFUL, null );
		run.enclosingFinished( cause );
		run.whenDrained( run::release );
		FutureTask<Throwable> close = new FutureTask<>( () -> {
			var failure = assertThrows( IllegalStateException.class, run::close );
			assertEquals( interrupt, Thread.currentThread().isInterrupted() );
			return failure;
		} );
		Thread thread = new Thread( close, "stop-proof-wake" );
		Throwable primary = null;
		try {
			thread.start();
			parked( thread );
			if( interrupt )
				thread.interrupt();
			else
				operation.complete();
			var failure = close.get( 2, TimeUnit.SECONDS );
			assertSame( cause, failure.getCause() );
			assertEquals( interrupt, failure.getMessage().contains( "interrupted" ) );
			assertEquals( interrupt ? 1 : 0, failure.getSuppressed().length );
			assertTrue( run.status().stopBudgetMiss().isEmpty() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			try {
				operation.complete();
				thread.interrupt();
				close.get( 3, TimeUnit.SECONDS );
				thread.join( 1000 );
				assertFalse( thread.isAlive() );
			}
			catch( Throwable failure ) {
				if( primary == null )
					throw failure;
				if( primary != failure )
					primary.addSuppressed( failure );
			}
		}
	}

	/**
	 * Claimed callbacks remain work even when the exact operation proves
	 * completion.
	 */
	@Test
	void callbackTimeCountsAndReentrantCloseDoesNotWaitForItself() {
		AtomicLong clock = new AtomicLong();
		FlowAdmission run = prepared( clock::get, Duration.ofNanos( 10 ) );
		Throwable cause = new IllegalStateException( "first Stop" );
		var firstFailure = new IllegalArgumentException( "first callback" );
		var secondFailure = new IllegalArgumentException( "second callback" );
		Operation first = run.reservation( 0 ).operation( proof -> {
			assertFalse( Thread.holdsLock( run.history() ) );
			assertThrows( IllegalStateException.class, run::close );
			clock.set( 10 );
			proof.complete();
			throw firstFailure;
		} );
		Operation second = run.reservation( 0 ).operation( proof -> {
			throw secondFailure;
		} );
		try {
			assertSame( firstFailure,
					assertThrows( IllegalArgumentException.class, () -> run.stop( cause ) ) );
			assertArrayEquals( new Throwable[] { secondFailure }, firstFailure.getSuppressed() );
			var miss = run.status().stopBudgetMiss().orElseThrow();
			assertSame( cause, miss.cause() );
			assertEquals( 2, miss.callbacks() );
			assertEquals( 1, miss.operations() );
			assertEquals( 1, miss.pendingBodies() );
			assertEquals( 1, miss.pendingNative() );
			assertTrue( miss.cancellationEffects() );
			assertTrue( assertThrows( IllegalStateException.class, run::close ).getMessage()
					.contains( "budget missed" ) );
		}
		finally {
			first.complete();
			second.complete();
			run.processed( 0, History.Result.SUCCESS, null );
			run.finished( "leaf", FlowAdmission.Outcome.SUCCESSFUL, null );
			run.enclosingFinished( cause );
			run.release();
		}
	}

	/** Completed healthy execution never samples the stop clock. */
	@Test
	void healthyAdmissionNeverReadsClock() {
		var throwing = new AtomicBoolean( true );
		FlowAdmission run = prepared( () -> {
			if( throwing.get() )
				throw new AssertionError( "healthy clock" );
			return 0;
		}, Duration.ofNanos( 1 ) );
		Throwable primary = null;
		try {
			run.processed( 0, History.Result.SUCCESS, null );
			run.finished( "leaf", FlowAdmission.Outcome.SUCCESSFUL, null );
			run.enumerationClosed();
			assertTrue( run.factoryFinished( FlowAdmission.Outcome.SUCCESSFUL, null ) );
			run.release();
			run.close();
			assertTrue( run.status().stopBudgetMiss().isEmpty() );
		}
		catch( RuntimeException | Error failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			throwing.set( false );
			finish( run, primary );
		}
	}

	private static void finish( FlowAdmission run, Throwable primary ) {
		// This pure-core test scope supplies enclosing proof, not a fabricated native
		// leaf result. If setup failed after entry, record only its actual scope exit.
		cleanup( primary, () -> {
			if( run.status().entered() != run.status().completed() )
				run.processed( 0, null, new IllegalStateException( "test scope ended" ) );
		}, () -> run.enclosingFinished( new IllegalStateException( "test scope ended" ) ), () -> {
			if( run.status().state() != ExecutionStatus.State.QUIESCENT )
				run.release();
		} );
	}

	/**
	 * All retained identities survive native retirement, but the miss stays
	 * bounded.
	 */
	@Test
	void retiredOwnersHaveBoundedImmutableMissEvidence() {
		AtomicLong clock = new AtomicLong();
		FlowAdmission run = new FlowAdmission( 7, clock::get );
		run.stopBudget( Duration.ofNanos( 1 ) );
		var flows = java.util.stream.IntStream.range( 0, 7 ).mapToObj( i -> Creator.build(
				f -> f.meta( m -> m.description( i + "x".repeat( 200 ) ) ) ) ).toList();
		var rules = new ResourceRules().resources( "empty", f -> true );
		run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
		var operations = new java.util.ArrayList<Operation>();
		Throwable cause = new IllegalStateException( "stop" );
		try {
			for( int i = 0; i < 7; i++ ) {
				assertEquals( i, run.poll() );
				run.registered( i, "leaf" + i );
				run.started( "leaf" + i );
				assertTrue( run.enter( i, "leaf" + i ) );
				operations.add( run.reservation( i ).operation() );
				run.processed( i, History.Result.SUCCESS, null );
				run.finished( "leaf" + i, FlowAdmission.Outcome.SUCCESSFUL, null );
			}
			run.stop( cause );
			clock.set( 1 );
			operations.get( 0 ).complete();
			var miss = run.status().stopBudgetMiss().orElseThrow();
			assertEquals( 6, miss.owners() );
			assertEquals( 6, miss.operations() );
			assertEquals( 0, miss.pendingNative() );
			assertEquals( 5, miss.affected().size() );
			assertTrue( miss.affected().get( 0 ).startsWith( "0" ) );
			assertTrue( miss.affected().stream().allMatch( id -> id.length() == 120 ) );
			assertThrows( UnsupportedOperationException.class, () -> miss.affected().clear() );
		}
		finally {
			operations.forEach( Operation::complete );
			run.enclosingFinished( cause );
			run.release();
		}
		assertEquals( 5, run.status().stopBudgetMiss().orElseThrow().affected().size() );
	}

	private static void parked( Thread thread ) {
		long start = System.nanoTime();
		while( thread.isAlive() && thread.getState() != Thread.State.TIMED_WAITING
				&& System.nanoTime() - start < TimeUnit.SECONDS.toNanos( 2 ) )
			Thread.yield();
		assertEquals( Thread.State.TIMED_WAITING, thread.getState(), "close must really park" );
	}

	/** Validation is atomic, preserves nanoseconds and freezes on preparation. */
	@Test
	void durationValidationAndFreeze() {
		AtomicLong clock = new AtomicLong();
		FlowAdmission run = new FlowAdmission( 1, clock::get );
		run.stopBudget( Duration.ofNanos( 1 ) );
		assertThrows( NullPointerException.class, () -> run.stopBudget( null ) );
		assertThrows( IllegalArgumentException.class, () -> run.stopBudget( Duration.ZERO ) );
		assertThrows( IllegalArgumentException.class, () -> run.stopBudget( Duration.ofNanos( -1 ) ) );
		assertThrows( ArithmeticException.class,
				() -> run.stopBudget( Duration.ofSeconds( Long.MAX_VALUE ) ) );
		run.prepare( List.of(), List.of() );
		assertThrows( IllegalStateException.class, () -> run.stopBudget( Duration.ofSeconds( 1 ) ) );
		run.stop( new IllegalStateException( "stop" ) );
		clock.incrementAndGet();
		run.release();
		assertEquals( Duration.ofNanos( 1 ), run.status().stopBudgetMiss().orElseThrow().budget() );
	}

	/** Last operation proof changes capacity before notifying its owner. */
	@ParameterizedTest
	@CsvSource({ "-100,9,false", "-100,10,true", "9223372036854775803,10,true" })
	void lateProofAndTimelySeal( long origin, long elapsed, boolean missed ) {
		AtomicLong clock = new AtomicLong( origin );
		FlowAdmission run = prepared( clock::get, Duration.ofNanos( 10 ) );
		Operation operation = run.reservation( 0 ).operation();
		Throwable cause = new IllegalStateException( "first" );
		try {
			run.stop( cause );
			clock.set( origin + 5 );
			run.stop( new IllegalStateException( "repeated" ) );
			run.processed( 0, History.Result.SUCCESS, null );
			run.finished( "leaf", FlowAdmission.Outcome.SUCCESSFUL, null );
			run.enclosingFinished( cause );
			run.whenDrained( run::release );
			clock.set( origin + elapsed );
			operation.complete();
			clock.set( origin + 1000 );
			var status = run.status();
			assertEquals( ExecutionStatus.State.QUIESCENT, status.state() );
			assertSame( cause, status.cause() );
			assertEquals( missed, status.stopBudgetMiss().isPresent() );
			if( missed ) {
				var miss = status.stopBudgetMiss().orElseThrow();
				assertEquals( Duration.ofNanos( 10 ), miss.elapsed() );
				assertEquals( List.of( "budget flow []" ), miss.affected() );
				assertEquals( 0, miss.operations(),
						"owner-observed proof, not invented historical counts" );
			}
		}
		finally {
			operation.complete();
		}
	}

	/** The close caller actually parks; no status poll or proof makes time pass. */
	@Test
	void reachableCloseTimesOutAndRetainsOwnership() throws Exception {
		FlowAdmission run = prepared( System::nanoTime, Duration.ofMillis( 100 ) );
		Operation operation = run.reservation( 0 ).operation();
		run.processed( 0, History.Result.SUCCESS, null );
		run.finished( "leaf", FlowAdmission.Outcome.SUCCESSFUL, null );
		CountDownLatch entered = new CountDownLatch( 1 );
		FutureTask<Throwable> close = new FutureTask<>( () -> {
			entered.countDown();
			return assertThrows( IllegalStateException.class, run::close );
		} );
		Thread thread = new Thread( close, "budget-close" );
		try {
			thread.start();
			assertTrue( entered.await( 2, TimeUnit.SECONDS ) );
			Throwable failure = close.get( 3, TimeUnit.SECONDS );
			assertTrue( failure.getMessage().contains( "budget missed" ), failure::toString );
			var miss = run.status().stopBudgetMiss().orElseThrow();
			assertEquals( 1, miss.operations() );
			assertEquals( 1, miss.owners() );
			assertEquals( 1, run.status().owners() );
		}
		finally {
			operation.complete();
			run.enclosingFinished( new IllegalStateException( "test cleanup" ) );
			run.release();
			thread.join( 3000 );
			assertFalse( thread.isAlive() );
		}
	}

	private static FlowAdmission prepared( LongSupplier clock, Duration duration ) {
		var cleaning = new AtomicBoolean();
		FlowAdmission run = new FlowAdmission( 1, () -> cleaning.get() ? 0 : clock.getAsLong() );
		try {
			run.stopBudget( duration );
			var flow = Creator.build( f -> f.meta( m -> m.description( "budget flow" ) ) );
			var rules = new ResourceRules().resources( "empty", f -> true );
			run.prepare( List.of( flow ), List.of( rules.resolve( flow ) ) );
			assertEquals( 0, run.poll() );
			run.registered( 0, "leaf" );
			run.started( "leaf" );
			assertTrue( run.enter( 0, "leaf" ) );
			return run;
		}
		catch( RuntimeException | Error failure ) {
			cleaning.set( true );
			finish( run, failure );
			throw failure;
		}
	}

	/** A late final proof must not erase an unobserved deadline. */
	@Test
	void lateOwnerCompletionLatchesDefaultBudgetWithoutPolling() {
		AtomicLong clock = new AtomicLong( -100 );
		FlowAdmission run = new FlowAdmission( 1, clock::get );
		run.prepare( List.of(), List.of() );
		Throwable cause = new IllegalStateException( "first stop" );
		run.stop( cause );
		clock.addAndGet( Duration.ofSeconds( 30 ).toNanos() );
		run.enclosingFinished( cause );
		run.release();
		ExecutionStatus status = run.status();
		assertEquals( ExecutionStatus.State.QUIESCENT, status.state() );
		var miss = status.stopBudgetMiss().orElseThrow();
		assertEquals( Duration.ofSeconds( 30 ), miss.budget() );
		assertEquals( Duration.ofSeconds( 30 ), miss.elapsed() );
		assertSame( cause, miss.cause() );
	}
}
