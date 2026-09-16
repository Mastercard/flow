package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.*;
import static com.mastercard.test.flow.assrt.FlowAdmission.EXHAUSTED;
import static com.mastercard.test.flow.assrt.FlowAdmission.WAITING;
import static com.mastercard.test.flow.assrt.FlowAdmission.Outcome.SUCCESSFUL;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.assrt.resource.ResourceRules;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.Transmission.Type;

/**
 * Pure prepared-run seam; real native execution is exercised in the adapter.
 */
class FlowAdmissionTest {
	/**
	 * The actual timed waiter samples the query but does not revalidate or revisit
	 * pending resources on unchanged ticks, independently of selection size.
	 *
	 * @param size Number of blocked resource requests
	 * @throws Exception If the controlled factory does not drain
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 1000 })
	void unchangedCancellationTicksDoNotRetryReadiness( int size ) throws Exception {
		var scope = ResourceReservations.shared();
		var rules = new ResourceRules().resources( "K", f -> true, "cancel19-core" );
		Flow first = flow( "held" );
		var holder = scope.register( scope.capacity( 1 ), rules.resolve( first ), () -> {
		} );
		AtomicReference<FlowAdmission> owner = new AtomicReference<>();
		AtomicInteger queries = new AtomicInteger();
		AtomicInteger validations = new AtomicInteger();
		AtomicBoolean cancelled = new AtomicBoolean();
		CountDownLatch sampled = new CountDownLatch( 1 );
		CountDownLatch resumeQuery = new CountDownLatch( 1 );
		Throwable cleanupStop = new IllegalStateException( "test cleanup" );
		FutureTask<Throwable> factory = new FutureTask<>( () -> {
			FlowAdmission run = new FlowAdmission( 1 );
			owner.set( run );
			run.cancellationQuery( () -> {
				assertFalse( Thread.holdsLock( run.history() ) );
				if( queries.incrementAndGet() == 5 ) {
					sampled.countDown();
					await( resumeQuery );
				}
				return cancelled.get();
			} );
			Throwable primary = null;
			try {
				List<Flow> flows = IntStream.range( 0, size ).mapToObj( i -> flow( "blocked " + i ) )
						.toList();
				run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
				try {
					int index = run.next( validations::incrementAndGet );
					throw new AssertionError( "cancelled wait admitted work: " + index );
				}
				catch( IllegalStateException stopped ) {
					assertSame( run.stopCause(), stopped.getCause() );
					return run.stopCause();
				}
			}
			catch( RuntimeException | Error failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				// This pure-core factory has ended without handing any admission to a body.
				Throwable firstFailure = primary;
				for( Runnable cleanup : List.<Runnable>of( () -> run.stop( cleanupStop ),
						() -> run.enclosingFinished( cleanupStop ), run::release ) ) {
					try {
						cleanup.run();
					}
					catch( RuntimeException | Error failure ) {
						if( firstFailure == null )
							firstFailure = failure;
						else if( firstFailure != failure )
							firstFailure.addSuppressed( failure );
					}
				}
				if( primary == null ) {
					if( firstFailure instanceof RuntimeException failure )
						throw failure;
					if( firstFailure instanceof Error failure )
						throw failure;
				}
			}
		} );
		Thread thread = new Thread( factory, "cancellation-ticks" );
		Throwable primary = null;
		try( Grant grant = holder.tryAcquire() ) {
			assertNotNull( grant );
			try {
				thread.start();
				assertTrue( sampled.await( 3, TimeUnit.SECONDS ),
						"unchanged token rechecks never arrived" );
				FlowAdmission run = owner.get();
				synchronized( run.history() ) {
					assertEquals( 1, validations.get() );
					assertEquals( size, run.resourceAttempts );
					assertEquals( 3, run.unchangedWakes );
					assertEquals( 0, run.eventWakes );
					assertEquals( 0, run.successorVisits() );
				}
				cancelled.set( true );
				resumeQuery.countDown();
				assertEquals( "Native Flow cancellation requested",
						factory.get( 5, TimeUnit.SECONDS ).getMessage() );
				assertEquals( 5, queries.get(), "a latched stop must not query again" );
			}
			catch( Throwable failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				Throwable firstFailure = primary;
				for( Executable cleanup : List.<Executable>of( () -> {
					if( owner.get() != null )
						owner.get().stop( cleanupStop );
				}, resumeQuery::countDown, () -> factory.get( 5, TimeUnit.SECONDS ), () -> {
					thread.join( 1000 );
					assertFalse( thread.isAlive() );
				} ) ) {
					try {
						cleanup.execute();
					}
					catch( Throwable failure ) {
						if( firstFailure == null )
							firstFailure = failure;
						else if( firstFailure != failure )
							firstFailure.addSuppressed( failure );
					}
				}
				if( primary == null && firstFailure != null ) {
					if( firstFailure instanceof Exception failure )
						throw failure;
					throw (Error) firstFailure;
				}
			}
		}
		finally {
			holder.cancel();
		}
	}

	/**
	 * Real notifications bypass even a deliberately long query interval. A naked
	 * monitor notification is not a readiness change; absent queries park untimed.
	 *
	 * @param query Whether the actual channel is present
	 * @param event The existing signal releasing the factory
	 * @throws Exception If the controlled factory fails to drain
	 */
	@ParameterizedTest
	@CsvSource({ "false,resource", "true,resource", "false,completion", "true,completion",
			"false,stop", "true,stop", "false,interrupt", "true,interrupt" })
	void unchangedWakeAndRealEventsKeepTheirSeparatePaths( boolean query, String event )
			throws Exception {
		var scope = ResourceReservations.shared();
		var rules = new ResourceRules().resources( "K", f -> true, "cancel19-events" );
		Flow first = flow( "first" );
		boolean completion = event.equals( "completion" );
		var holderRules = completion
				? new ResourceRules().resources( "other", f -> true, "cancel19-other" )
				: rules;
		var holder = scope.register( scope.capacity( 1 ), holderRules.resolve( first ), () -> {
		} );
		AtomicReference<FlowAdmission> owner = new AtomicReference<>();
		AtomicInteger validations = new AtomicInteger();
		CountDownLatch prepared = new CountDownLatch( 1 );
		CountDownLatch eventDelivered = new CountDownLatch( 1 );
		Throwable cause = new IllegalStateException( "explicit event Stop" );
		FutureTask<Integer> factory = new FutureTask<>( () -> {
			FlowAdmission run = new FlowAdmission( 1, 60_000 );
			owner.set( run );
			if( query )
				run.cancellationQuery( () -> {
					assertFalse( Thread.holdsLock( run.history() ) );
					return false;
				} );
			boolean entered = false;
			try {
				List<Flow> flows = completion ? List.of( first, flow( "next", first ) ) : List.of( first );
				run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
				if( completion ) {
					assertEquals( 0, run.poll() );
					enter( run, 0 );
					entered = true;
				}
				prepared.countDown();
				try {
					int index = run.next( validations::incrementAndGet );
					enter( run, index );
					complete( run, index );
					return index;
				}
				catch( IllegalStateException stopped ) {
					if( event.equals( "interrupt" ) ) {
						assertInstanceOf( InterruptedException.class, stopped.getCause() );
						assertTrue( Thread.currentThread().isInterrupted() );
						run.stop( stopped ); // The adapter's existing exception path.
						assertSame( stopped, run.stopCause() );
					}
					else
						assertSame( cause, stopped.getCause() );
					return WAITING;
				}
			}
			finally {
				// Stop wakes before its out-of-lock withdrawal batch has returned.
				if( !event.equals( "interrupt" ) )
					await( eventDelivered );
				run.stop( cause );
				if( entered )
					complete( run, 0 );
				run.enclosingFinished( cause );
				run.release();
			}
		} );
		Thread thread = new Thread( factory, "readiness-events" );
		Throwable primary = null;
		try( Grant grant = holder.tryAcquire() ) {
			assertNotNull( grant );
			try {
				thread.start();
				await( prepared );
				FlowAdmission run = owner.get();
				awaitReadinessWait( thread, run, query, 0 );
				long attempts;
				synchronized( run.history() ) {
					attempts = run.resourceAttempts;
					run.history().notifyAll();
				}
				awaitReadinessWait( thread, run, query, 1 );
				synchronized( run.history() ) {
					assertEquals( attempts, run.resourceAttempts );
					assertEquals( 1, validations.get() );
					assertEquals( 0, run.eventWakes );
				}
				switch( event ) {
					case "resource":
						grant.close();
						break;
					case "completion":
						complete( run, 0 );
						break;
					case "stop":
						run.stop( cause );
						break;
					case "interrupt":
						thread.interrupt();
						break;
					default:
						fail( event );
				}
				eventDelivered.countDown();
				assertEquals( event.equals( "resource" ) ? 0 : completion ? 1 : WAITING,
						factory.get( 5, TimeUnit.SECONDS ) );
				if( event.equals( "interrupt" ) )
					assertEquals( 0, run.eventWakes );
				else
					assertTrue( run.eventWakes > 0,
							"processing, native completion and grant return can each signal readiness" );
			}
			catch( Throwable failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				try {
					if( owner.get() != null )
						owner.get().stop( cause );
				}
				finally {
					eventDelivered.countDown();
					try {
						factory.get( 5, TimeUnit.SECONDS );
						thread.join( 1000 );
						assertFalse( thread.isAlive() );
					}
					catch( Throwable cleanup ) {
						if( primary == null )
							throw cleanup;
						if( primary != cleanup )
							primary.addSuppressed( cleanup );
					}
				}
			}
		}
		finally {
			holder.cancel();
		}
	}

	private static void awaitReadinessWait( Thread thread, FlowAdmission run, boolean timed,
			long unchanged ) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos( 5 );
		while( System.nanoTime() < deadline ) {
			synchronized( run.history() ) {
				if( run.unchangedWakes >= unchanged && thread.getState() == (timed
						? Thread.State.TIMED_WAITING
						: Thread.State.WAITING) )
					return;
			}
			Thread.yield();
		}
		fail( "factory did not park in its readiness wait" );
	}

	/**
	 * Optional query observation uses the existing irreversible Stop/body gate and
	 * cannot replace an earlier cause or restart after the token clears.
	 *
	 * @param body     Whether admission has already handed off to native execution
	 * @param explicit Whether an explicit Stop won before query observation
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "false,true", "true,true" })
	void cancellationPreservesFirstCauseAndNeverReopensEntry( boolean body, boolean explicit ) {
		FlowAdmission run = new FlowAdmission( 1 );
		AtomicBoolean cancelled = new AtomicBoolean();
		AtomicInteger queries = new AtomicInteger();
		run.cancellationQuery( () -> {
			assertFalse( Thread.holdsLock( run.history() ) );
			queries.incrementAndGet();
			return cancelled.get();
		} );
		assertThrows( IllegalStateException.class, () -> run.cancellationQuery( null ) );
		Throwable first = new IllegalStateException( "earlier explicit cause" );
		boolean admitted = false;
		try {
			Flow flow = flow( "gate" );
			var rules = new ResourceRules().resources( "empty", f -> true );
			run.prepare( List.of( flow ), List.of( rules.resolve( flow ) ) );
			if( body ) {
				assertEquals( 0, run.poll() );
				admitted = true;
				run.registered( 0, "0" );
				run.started( "0" );
			}
			cancelled.set( true );
			if( explicit )
				run.stop( first );
			if( body )
				assertFalse( run.enter( 0, "0" ) );
			else {
				Throwable rejected = assertThrows( IllegalStateException.class, run::poll );
				assertSame( run.stopCause(), rejected.getCause() );
			}
			Throwable observed = run.stopCause();
			assertNotNull( observed );
			if( explicit )
				assertSame( first, observed );
			else
				assertEquals( "Native Flow cancellation requested", observed.getMessage() );
			int count = queries.get();
			cancelled.set( false );
			assertFalse( run.enter( 0, "0" ) );
			assertThrows( IllegalStateException.class, run::poll );
			run.stop( new IllegalStateException( "later stop" ) );
			assertSame( observed, run.stopCause() );
			assertEquals( count, queries.get() );
			assertEquals( (body ? 1 : 0) + (explicit ? 0 : 1), count );
			assertEquals( 0, run.status().entered() );
		}
		finally {
			run.stop( first );
			if( admitted )
				run.finished( "0", FlowAdmission.Outcome.ABORTED, run.stopCause() );
			run.enclosingFinished( first );
			run.release();
		}
	}

	/**
	 * Zero issued leaves do not prove that prepared pending requests are gone.
	 *
	 * @param size      Selected count, -1 before preparation, -2 for failed
	 *                  preparation
	 * @param stopFirst Whether explicit Stop precedes the first disposal attempt
	 */
	@ParameterizedTest
	@CsvSource({ "-1,false", "0,false", "3,false", "-1,true", "0,true", "3,true", "-2,true" })
	void prematureReleaseCannotDiscardPendingRequests( int size, boolean stopFirst ) {
		var run = new FlowAdmission( 1 );
		var scope = ResourceReservations.shared();
		Flow flow = emptyFlow( "unadmitted []" );
		var rules = new ResourceRules().resources( "shared fixture", f -> true, "early-release" );
		Throwable primary = null;
		try {
			Throwable cause = new IllegalStateException( "cancelled before admission" );
			if( size == -2 )
				cause = assertThrows( IllegalArgumentException.class, () -> run.prepare(
						List.of( flow, flow ), List.of( rules.resolve( flow ), rules.resolve( flow ) ) ) );
			else if( size >= 0 ) {
				List<Flow> flows = IntStream.range( 0, size )
						.<Flow>mapToObj( i -> i == 0 ? flow : emptyFlow( "pending " + i + " []" ) ).toList();
				run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
			}
			if( !stopFirst )
				cause = assertThrows( IllegalStateException.class, run::release );
			assertNotEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			run.stop( cause );
			assertSame( cause, run.stopCause() );
			assertTrue( run.status().incomplete() );
			assertEquals( Math.max( 0, size ), run.status().selected() );
			assertEquals( 0, run.status().admitted() );
			assertEquals( 0, run.status().entered() );
			assertEquals( 0, run.status().completed() );
			assertEquals( 0, run.status().nativeTerminals() );
			assertEquals( Result.PENDING, run.history().get( flow ) );
			run.release();
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertSame( cause, run.status().cause() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			cleanup( run, List.of(), List.of(), primary );
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh reuse", f -> true ).resolve( null ), () -> {
					} );
			try( Grant grant = reuse.tryAcquire() ) {
				assertNotNull( grant, "even rejected disposal must leave no orphaned priority gate" );
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

	/** Normal empty selection still requires an explicit factory terminal. */
	@Test
	void emptySelectionCompletesOnlyAtFactoryTerminal() {
		var run = new FlowAdmission( 1 );
		Throwable primary = null;
		try {
			run.prepare( List.of(), List.of() );
			run.enumerationClosed();
			assertEquals( ExecutionStatus.State.ACTIVE, run.status().state() );
			assertTrue( run.factoryFinished( SUCCESSFUL, null ) );
			run.release();
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertFalse( run.status().incomplete() );
			assertNull( run.stopCause() );
			assertEquals( 0, run.status().nativeTerminals() );
			run.close();
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			cleanup( run, List.of(), List.of(), primary );
		}
	}

	/**
	 * Rejected disposal must withdraw every request even when notifications fail.
	 */
	@Test
	void prematureReleasePreservesFaultAndAttemptsEveryWithdrawal() {
		var run = new FlowAdmission( 1 );
		var scope = ResourceReservations.shared();
		var rules = new ResourceRules().resources( "shared fixture", f -> true, "release-fault" );
		List<Flow> flows = List.of( emptyFlow( "A []" ), emptyFlow( "B []" ), emptyFlow( "C []" ) );
		List<ResourceReservations.Request> observers = new ArrayList<>();
		var calls = new AtomicInteger();
		var callback = new IllegalArgumentException( "first withdrawal notification" );
		var later = new IllegalArgumentException( "second withdrawal notification" );
		Throwable primary = null;
		try {
			run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
			observers.add( scope.register( scope.capacity( 1 ), rules.resolve( null ), () -> {
				assertFalse( Thread.holdsLock( run.history() ) );
				assertFalse( Thread.holdsLock( scope ) );
				assertFalse( run.disposable(), "withdrawal batch is still in flight" );
				assertThrows( IllegalStateException.class, run::release );
				assertEquals( ExecutionStatus.State.STOPPING, run.status().state() );
				switch( calls.incrementAndGet() ) {
					case 1 -> throw callback;
					case 2 -> throw later;
					default -> {
					}
				}
			} ) );
			var failure = assertThrows( IllegalStateException.class, run::release );
			assertSame( failure, run.stopCause() );
			assertEquals( "Flow admission is not safely complete", failure.getMessage() );
			assertEquals( List.of( callback ), List.of( failure.getSuppressed() ) );
			assertEquals( List.of( later ), List.of( callback.getSuppressed() ) );
			assertEquals( 3, calls.get(), "all pending requests must be withdrawn before returning" );
			assertEquals( ExecutionStatus.State.STOPPING, run.status().state() );
			assertEquals( 0, run.status().admitted() );
			assertEquals( 0, run.status().completed() );
			assertEquals( 0, run.status().nativeTerminals() );
			flows.forEach( flow -> assertEquals( Result.PENDING, run.history().get( flow ) ) );
			run.release();
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertSame( failure, run.status().cause() );
			assertTrue( run.status().incomplete() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			observers.forEach( ResourceReservations.Request::cancel );
			cleanup( run, List.of(), List.of(), primary );
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh reuse", f -> true ).resolve( null ), () -> {
					} );
			try( Grant grant = reuse.tryAcquire() ) {
				assertNotNull( grant, "callback failures cannot orphan pending requests" );
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

	/** Retention and one grant's close cannot skip other extracted safe grants. */
	@Test
	void stopClosesEverySafeGrantAfterRetentionCallbackFailure() {
		Flow a = emptyFlow( "A [chain:AB]" );
		Flow b = emptyFlow( "B [chain:AB]" );
		Flow c = emptyFlow( "C [chain:CD]" );
		Flow d = emptyFlow( "D [chain:CD]" );
		Flow live = emptyFlow( "live []" );
		List<Flow> flows = List.of( a, b, c, d, live );
		var rules = new ResourceRules().resources( "empty", f -> true )
				.isolatedChains( "separate chain fixtures", "AB", "CD" );
		var run = new FlowAdmission( 3 );
		var scope = ResourceReservations.shared();
		List<Integer> admitted = new ArrayList<>();
		List<Integer> entered = new ArrayList<>();
		List<ResourceReservations.Request> observers = new ArrayList<>();
		ResourceReservations.Operation operation = null;
		var retentionFailure = new IllegalArgumentException( "retention callback" );
		var closeFailure = new IllegalStateException( "safe close callback" );
		var cause = new IllegalStateException( "original Stop" );
		var calls = new AtomicInteger();
		var closes = new AtomicInteger();
		Throwable primary = null;
		try {
			run.prepare( flows, rules.chains( flows, flows.stream().map( rules::resolve ).toList() ) );
			for( int expected : new int[] { 0, 2, 4 } ) {
				int index = poll( run, admitted );
				assertEquals( expected, index );
				enter( run, index );
				entered.add( index );
			}
			for( int index : new int[] { 0, 2 } ) {
				complete( run, index );
				admitted.remove( Integer.valueOf( index ) );
			}
			operation = run.reservation( 4 ).operation();
			observers.add( scope.register( scope.capacity( 1 ), rules.resolve( null ), () -> {
				assertFalse( Thread.holdsLock( run.history() ) );
				assertFalse( Thread.holdsLock( scope ) );
				switch( calls.incrementAndGet() ) {
					case 1 -> throw retentionFailure;
					case 2 -> throw closeFailure;
					default -> {
					}
				}
			} ) );
			assertSame( retentionFailure, assertThrows( IllegalArgumentException.class,
					() -> run.stop( cause ) ) );
			assertEquals( List.of( closeFailure ), List.of( retentionFailure.getSuppressed() ) );
			assertEquals( 3, calls.get(), "retention plus both extracted grant closes" );
			assertEquals( 1, run.status().owners(), "only live native use remains" );
			run.factoryFinished( SUCCESSFUL, null );
			run.whenDrained( () -> {
				closes.incrementAndGet();
				run.release();
			} );
			operation.complete();
			assertEquals( 0, closes.get(), "operation proof does not end active processing" );
			run.processed( 4, Result.SUCCESS, null );
			admitted.clear();
			assertEquals( 1, closes.get() );
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertSame( cause, run.status().cause() );
			assertTrue( run.status().incomplete() );
			assertEquals( 3, run.status().entered(), "neither chain continuation entered" );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			calls.set( 100 );
			observers.forEach( ResourceReservations.Request::cancel );
			cleanup( run, admitted, entered, primary );
			if( operation != null )
				operation.complete();
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh reuse", f -> true ).resolve( null ), () -> {
					} );
			try( Grant grant = reuse.tryAcquire() ) {
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
	 * Committed retirement survives wakeup errors without masking a protocol fault.
	 *
	 * @param event Factory/scope termination or conflicting skip after native start
	 */
	@ParameterizedTest
	@ValueSource(strings = { "factory", "enclosing", "conflict" })
	void terminalEffectsSurviveCallbackFailure( String event ) {
		var run = new FlowAdmission( 2 );
		var scope = ResourceReservations.shared();
		var rules = new ResourceRules().resources( "empty", f -> true );
		List<Flow> flows = List.of( emptyFlow( "A []" ), emptyFlow( "B []" ),
				emptyFlow( "C []" ), emptyFlow( "D []" ) );
		List<Integer> admitted = new ArrayList<>();
		List<ResourceReservations.Request> observers = new ArrayList<>();
		var callback = new IllegalArgumentException( "terminal withdrawal notification" );
		var later = new IllegalStateException( "later terminal withdrawal notification" );
		var cause = new IllegalStateException( "enclosing terminal" );
		var calls = new AtomicInteger();
		var closes = new AtomicInteger();
		Throwable primary = null;
		try {
			run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
			assertEquals( 0, poll( run, admitted ) );
			assertEquals( 1, poll( run, admitted ) );
			run.registered( 0, "0" );
			run.registered( 1, "1" );
			if( event.equals( "conflict" ) )
				run.started( "0" );
			run.whenDrained( () -> {
				closes.incrementAndGet();
				run.release();
			} );
			observers.add( scope.register( scope.capacity( 1 ), rules.resolve( null ), () -> {
				assertFalse( Thread.holdsLock( run.history() ) );
				assertFalse( Thread.holdsLock( scope ) );
				assertEquals( 0, closes.get(), "no disposal inside a committed effect batch" );
				int call = calls.incrementAndGet();
				if( call == 1 )
					throw callback;
				if( call == 2 )
					throw later;
			} ) );
			RuntimeException thrown = assertThrows( RuntimeException.class, () -> {
				switch( event ) {
					case "factory" -> run.factoryFinished( SUCCESSFUL, null );
					case "enclosing" -> run.enclosingFinished( cause );
					default -> run.skipped( "0", "invalid after start" );
				}
			} );
			assertEquals( List.of( later ), List.of( callback.getSuppressed() ) );
			if( event.equals( "conflict" ) ) {
				assertSame( run.stopCause(), thrown );
				assertEquals( "Conflicting native Flow skip", thrown.getMessage() );
				assertEquals( List.of( callback ), List.of( thrown.getSuppressed() ) );
				assertEquals( 0, closes.get(), "a conflict is not a native terminal" );
				assertEquals( 2, run.status().owners() );
				run.enclosingFinished( cause );
			}
			else
				assertSame( callback, thrown );
			admitted.clear();
			assertEquals( 1, closes.get() );
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertTrue( run.status().incomplete() );
			assertEquals( 0, run.status().nativeTerminals(), "scope end invents no leaf outcomes" );
			assertEquals( 0, run.status().completed() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			observers.forEach( ResourceReservations.Request::cancel );
			cleanup( run, admitted, List.of(), primary );
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh reuse", f -> true ).resolve( null ), () -> {
					} );
			try( Grant grant = reuse.tryAcquire() ) {
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
	 * A committed withdrawal batch must finish even when different wakeups fail.
	 */
	@Test
	void stopAttemptsEveryWithdrawalAndRetainsLiveOwnership() {
		List<Flow> flows = List.of( emptyFlow( "active []" ), emptyFlow( "pending B []" ),
				emptyFlow( "pending C []" ), emptyFlow( "pending D []" ) );
		var rules = new ResourceRules().resources( "shared", f -> true, "stop-batch" );
		FlowAdmission run = new FlowAdmission( 1 );
		var scope = ResourceReservations.shared();
		List<ResourceReservations.Request> observers = new ArrayList<>();
		List<Integer> admitted = new ArrayList<>();
		List<Integer> entered = new ArrayList<>();
		ResourceReservations.Operation operation = null;
		var first = new IllegalStateException( "first withdrawal callback" );
		var later = new IllegalArgumentException( "later withdrawal callback" );
		var cause = new IllegalStateException( "original stop" );
		AtomicInteger notifications = new AtomicInteger();
		AtomicInteger laterNotifications = new AtomicInteger();
		Throwable primary = null;
		try {
			run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
			assertEquals( 0, poll( run, admitted ) );
			enter( run, 0 );
			entered.add( 0 );
			operation = run.reservation( 0 ).operation();
			observers.add( scope.register( scope.capacity( 1 ), rules.resolve( null ), () -> {
				assertFalse( Thread.holdsLock( run.history() ) );
				assertFalse( Thread.holdsLock( scope ) );
				if( notifications.incrementAndGet() == 1 )
					throw first;
			} ) );
			observers.add( scope.register( scope.capacity( 1 ), rules.resolve( null ), () -> {
				if( laterNotifications.incrementAndGet() == 2 )
					throw later;
			} ) );
			assertSame( first, assertThrows( IllegalStateException.class, () -> run.stop( cause ) ) );
			assertEquals( List.of( later ), List.of( first.getSuppressed() ) );
			assertEquals( 4, notifications.get(), "three withdrawals and outstanding-use retention" );
			assertEquals( 4, laterNotifications.get() );
			assertSame( cause, run.stopCause() );
			assertSame( cause, assertThrows( IllegalStateException.class,
					observers.get( 0 )::tryAcquire ).getCause(), "active operation is explicitly unsafe" );
			run.stop( later );
			assertFalse( run.disposable() );
			assertEquals( 1, run.status().owners() );
			complete( run, 0 );
			admitted.remove( Integer.valueOf( 0 ) );
			assertFalse( run.disposable(), "native completion still cannot end the operation" );
			operation.complete();
			assertTrue( run.disposable() );
			assertSame( cause, run.stopCause() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			// Disarm both fault notifications before draining the owned use. Even a
			// failed oracle checks same-JVM reuse before rethrowing its original failure.
			notifications.set( 100 );
			laterNotifications.set( 100 );
			observers.forEach( ResourceReservations.Request::cancel );
			cleanup( run, admitted, entered, primary );
			if( operation != null )
				operation.complete();
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh reuse", f -> true ).resolve( null ), () -> {
					} );
			try( Grant grant = reuse.tryAcquire() ) {
				assertNotNull( grant, "all extracted pending ages must have been withdrawn" );
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
	 * Withdrawal notification pauses Stop after it extracts an unused chain grant.
	 * Factory termination and exact proof must work on either side of its close.
	 *
	 * @param proofBeforeClose Whether the last operation ends inside the
	 *                         notification
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void stopDrainsExtractedChainGrant( boolean proofBeforeClose ) {
		Flow first = Creator.build( f -> f.meta( m -> m.description( "A" )
				.tags( t -> t.add( "chain:drain" ) ) ) );
		Flow second = Creator.build( f -> f.meta( m -> m.description( "B" )
				.tags( t -> t.add( "chain:drain" ) ) ).prerequisite( first ) );
		List<Flow> flows = List.of( first, second, emptyFlow( "pending []" ) );
		var rules = new ResourceRules().resources( "empty", f -> true );
		FlowAdmission run = new FlowAdmission( 1 );
		var scope = ResourceReservations.shared();
		AtomicReference<ResourceReservations.Request> observer = new AtomicReference<>();
		List<Integer> admitted = new ArrayList<>();
		List<Integer> entered = new ArrayList<>();
		AtomicInteger closes = new AtomicInteger();
		Stream<?> stream = Stream.empty().onClose( closes::incrementAndGet );
		ResourceReservations.Operation operation = null;
		Throwable cause = new IllegalStateException( "stop in chain gap" );
		Throwable primary = null;
		try {
			run.prepare( flows, rules.chains( flows, flows.stream().map( rules::resolve ).toList() ) );
			assertEquals( 0, poll( run, admitted ) );
			enter( run, 0 );
			entered.add( 0 );
			operation = run.reservation( 0 ).operation();
			complete( run, 0 );
			admitted.remove( Integer.valueOf( 0 ) );
			var proof = operation;
			observer.set( scope.register( scope.capacity( 1 ), rules.resolve( null ), () -> {
				observer.get().cancel();
				assertFalse( run.factoryFinished( SUCCESSFUL, null ) );
				run.whenDrained( () -> {
					stream.close();
					run.release();
				} );
				if( proofBeforeClose )
					proof.complete();
				assertEquals( 1, run.status().owners(), "Stop has not closed its extracted grant" );
				assertEquals( 0, closes.get() );
			} ) );
			run.stop( cause );
			if( !proofBeforeClose ) {
				assertEquals( ExecutionStatus.State.STOPPING, run.status().state() );
				assertEquals( 0, closes.get() );
				operation.complete();
			}
			assertEquals( 1, closes.get(), "no further resource event should be needed" );
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertEquals( 1, run.status().entered(), "B never enters" );
			assertSame( cause, run.status().cause() );
			assertTrue( run.status().incomplete() );
			operation.complete();
			run.stop( new IllegalStateException( "later" ) );
			assertEquals( 1, closes.get() );
			assertSame( cause, run.status().cause() );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( observer.get() != null )
				observer.get().cancel();
			cleanup( run, admitted, entered, primary );
			if( operation != null )
				operation.complete();
			stream.close();
			var reuse = scope.register( scope.capacity( 1 ),
					new ResourceRules().exclusive( "fresh reuse", f -> true ).resolve( null ), () -> {
					} );
			try( Grant grant = reuse.tryAcquire() ) {
				assertNotNull( grant, "even a failed oracle must leave the shared scope reusable" );
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

	/** Bounded immutable snapshots outlive disposed model and native tables. */
	@Test
	void stoppedSnapshotsRemainBoundedAndDoNotInventNativeEvidence() {
		List<Flow> flows = IntStream.range( 0, 8 )
				.<Flow>mapToObj( i -> emptyFlow( i + "long identity".repeat( 20 ) + " []" ) ).toList();
		FlowAdmission run = prepare( 2, flows );
		var before = run.status();
		Throwable cause = new IllegalStateException( "stopped before admission" );
		try {
			assertThrows( NullPointerException.class, () -> run.enclosingFinished( null ) );
			assertEquals( ExecutionStatus.State.ACTIVE, run.status().state() );
			run.stop( cause );
			var stopped = run.status();
			assertTrue( stopped.incomplete() );
			assertEquals( 5, stopped.affected().size() );
			assertTrue( stopped.affected().stream().allMatch( id -> id.length() <= 120 ) );
			assertThrows( UnsupportedOperationException.class, () -> stopped.affected().clear() );
			run.enclosingFinished( cause );
			run.release();
			assertEquals( ExecutionStatus.State.QUIESCENT, run.status().state() );
			assertSame( cause, run.status().cause() );
			assertEquals( 8, run.status().selected() );
			assertEquals( 0, run.status().admitted() );
			assertEquals( 0, run.status().completed() );
			assertEquals( 0, run.status().nativeTerminals() );
			assertEquals( ExecutionStatus.State.ACTIVE, before.state(), "snapshot is not a live view" );
			run.stop( new IllegalStateException( "after quiescence" ) );
			assertSame( cause, run.status().cause() );
		}
		finally {
			run.stop( cause );
		}
	}

	/**
	 * Native completion does not let a chain bypass its outstanding operation.
	 *
	 * @param stopped Whether Stop arrives after native completion
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void chainOperationMustDrainBeforeContinuation( boolean stopped ) {
		Flow first = Creator.build( f -> f.meta( m -> m.description( "A" )
				.tags( t -> t.add( "chain:operation" ) ) ) );
		Flow second = Creator.build( f -> f.meta( m -> m.description( "B" )
				.tags( t -> t.add( "chain:operation" ) ) ).prerequisite( first ) );
		FlowAdmission run = new FlowAdmission( 2 );
		List<Flow> flows = List.of( first, second );
		var rules = new ResourceRules().resources( "empty", f -> true );
		run.prepare( flows, new com.mastercard.test.flow.assrt.resource.ChainPlan( flows,
				flows.stream().map( rules::resolve ).toList() ) );
		List<Integer> admitted = new ArrayList<>();
		List<Integer> entered = new ArrayList<>();
		ResourceReservations.Operation operation = null;
		Throwable primary = null;
		boolean finalized = false;
		try {
			assertEquals( 0, poll( run, admitted ) );
			enter( run, 0 );
			entered.add( 0 );
			operation = run.reservation( 0 ).operation();
			complete( run, 0 );
			admitted.remove( Integer.valueOf( 0 ) );
			assertEquals( WAITING, poll( run, admitted ), "native return is not operation proof" );
			Throwable cause = new IllegalStateException( "stop after native return" );
			if( stopped )
				run.stop( cause );
			operation.complete();
			if( stopped ) {
				assertThrows( IllegalStateException.class, run::poll );
				assertSame( cause, run.stopCause() );
				assertTrue( run.disposable() );
			}
			else {
				assertEquals( 1, poll( run, admitted ) );
				enter( run, 1 );
				entered.add( 1 );
				complete( run, 1 );
				admitted.remove( Integer.valueOf( 1 ) );
				finish( run );
				finalized = true;
			}
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( !finalized )
				cleanup( run, admitted, entered, primary );
			if( operation != null )
				operation.complete();
		}
	}

	/**
	 * Body and native return cannot complete an explicitly outstanding operation.
	 *
	 * @param capacity Outstanding grant limit
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void exactOperationProofRetiresOnlyRemainingOwnership( int capacity ) {
		FlowAdmission run = prepare( capacity, List.of( emptyFlow( "A []" ) ) );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		var operation = run.reservation( 0 ).operation();
		RuntimeException cause = new IllegalStateException( "cancel request returned" );
		var scope = ResourceReservations.shared();
		var request = scope.register( scope.capacity( 1 ),
				new ResourceRules().exclusive( "conflicting C", f -> true ).resolve( null ), () -> {
				} );
		try {
			run.stop( cause );
			complete( run, 0 );
			assertFalse( run.disposable(), "native return is not background completion" );
			assertSame( cause,
					assertThrows( IllegalStateException.class, request::tryAcquire ).getCause() );
			operation.complete();
			operation.complete();
			assertTrue( run.disposable() );
			try( Grant granted = request.tryAcquire() ) {
				assertNotNull( granted, "positive exact proof permits C, not restarting A" );
			}
			assertSame( cause, run.stopCause() );
			assertThrows( IllegalStateException.class, run::poll );
		}
		finally {
			request.cancel();
			run.stop( cause );
			complete( run, 0 );
			operation.complete();
		}
	}

	/** A null stop cannot silently clear readiness while leaving admission open. */
	@Test
	void nullStopIsRejectedWithoutChangingAdmission() {
		FlowAdmission run = prepare( 1, List.of( emptyFlow( "still active []" ) ) );
		try {
			assertThrows( NullPointerException.class, () -> run.stop( null ) );
			assertEquals( 0, run.poll() );
			enter( run, 0 );
			complete( run, 0 );
			finish( run );
		}
		finally {
			run.stop( new IllegalStateException( "test cleanup" ) );
		}
	}

	/**
	 * All joint successors precede a request created by the parent's release
	 * listener, without making the completing worker wait for admission.
	 *
	 * @param capacity Outstanding grant limit
	 * @throws Exception If completion waits for the factory
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void completionPublishesCanonicalCohortBeforeReleasingParent( int capacity ) throws Exception {
		Flow parent = flow( "parent" );
		List<Flow> flows = List.of( parent, flow( "first child", parent ),
				flow( "second child", parent ) );
		ResourceRules rules = new ResourceRules().resources( "parent", f -> f == parent, "cohort-B" )
				.resources( "children", f -> f != parent, "cohort-A" );
		FlowAdmission run = new FlowAdmission( capacity );
		var scope = ResourceReservations.shared();
		AtomicReference<ResourceReservations.Request> observer = new AtomicReference<>();
		AtomicReference<ResourceReservations.Request> newer = new AtomicReference<>();
		AtomicReference<Boolean> bypassed = new AtomicReference<>();
		List<Integer> entered = new ArrayList<>();
		List<Integer> admitted = new ArrayList<>();
		boolean finalized = false;
		Throwable primary = null;
		try {
			run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
			assertEquals( 0, poll( run, admitted ) );
			enter( run, 0 );
			entered.add( 0 );
			observer.set( scope.register( scope.capacity( 1 ),
					new ResourceRules().resources( "observer", f -> true ).resolve( null ), () -> {
						observer.get().cancel();
						var request = scope.register( scope.capacity( 1 ), rules.resolve( flows.get( 1 ) ),
								() -> {
								} );
						newer.set( request );
						try( Grant grant = request.tryAcquire() ) {
							bypassed.set( grant != null );
						}
					} ) );
			run.processed( 0, Result.SUCCESS, null );
			FutureTask<Void> completion = new FutureTask<>( () -> {
				run.finished( "0", SUCCESSFUL, null );
				return null;
			} );
			new Thread( completion, "native-completion" ).start();
			completion.get( 5, TimeUnit.SECONDS );
			admitted.remove( Integer.valueOf( 0 ) );
			assertEquals( false, bypassed.get(), "children must be published before parent release" );
			for( int expected : new int[] { 1, 2 } ) {
				int next = poll( run, admitted );
				enter( run, next );
				entered.add( next );
				assertEquals( expected, next, "joint readiness has canonical ties" );
				try( Grant grant = newer.get().tryAcquire() ) {
					assertNull( grant, "both children were published before the release listener" );
				}
				complete( run, next );
				admitted.remove( Integer.valueOf( next ) );
			}
			try( Grant grant = newer.get().tryAcquire() ) {
				assertNotNull( grant );
			}
			assertEquals( 2, run.successorVisits() );
			finish( run );
			finalized = true;
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( observer.get() != null )
				observer.get().cancel();
			if( newer.get() != null )
				newer.get().cancel();
			if( !finalized )
				cleanup( run, admitted, entered, primary );
		}
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 5, TimeUnit.SECONDS ), "admission coordination timed out" );
		}
		catch( InterruptedException failure ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( failure );
		}
	}

	/**
	 * A dependency-blocked exclusive has no age or gate, even with a cheap rank.
	 *
	 * @param capacity Outstanding grant limit
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void dependencyBlockedExclusiveCannotGateItsPrerequisiteOrOlderRoot( int capacity ) {
		Flow parent = flow( "parent" );
		Flow exclusive = flow( "exclusive", parent );
		Flow older = flow( "older" );
		List<Flow> flows = List.of( parent, exclusive, older );
		ResourceRules rules = new ResourceRules().resources( "empty", f -> true )
				.exclusive( "reset", f -> f == exclusive );
		FlowAdmission run = new FlowAdmission( capacity );
		List<Integer> entered = new ArrayList<>();
		List<Integer> admitted = new ArrayList<>();
		boolean finalized = false;
		Throwable primary = null;
		try {
			run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
			int first = poll( run, admitted );
			enter( run, first );
			entered.add( first );
			assertEquals( 0, first );
			if( capacity > 1 ) {
				int next = poll( run, admitted );
				enter( run, next );
				entered.add( next );
				assertEquals( 2, next, "dependency-blocked exclusive cannot gate EMPTY" );
			}
			complete( run, 0 );
			admitted.remove( Integer.valueOf( 0 ) );
			if( capacity == 1 ) {
				int next = poll( run, admitted );
				enter( run, next );
				entered.add( next );
				assertEquals( 2, next, "older pending root precedes newly ready exclusive" );
			}
			assertEquals( WAITING, poll( run, admitted ), "exclusive must drain older active work" );
			complete( run, 2 );
			admitted.remove( Integer.valueOf( 2 ) );
			assertEquals( 1, poll( run, admitted ) );
			enter( run, 1 );
			entered.add( 1 );
			complete( run, 1 );
			admitted.remove( Integer.valueOf( 1 ) );
			finish( run );
			finalized = true;
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( !finalized )
				cleanup( run, admitted, entered, primary );
		}
	}

	/**
	 * Stop withdraws a ready gate without a release event or callbacks under either
	 * bookkeeping lock.
	 *
	 * @throws Exception If a callback or lock probe fails to drain
	 */
	@Test
	void stoppedPendingGateWakesOutsideBothLocksAndNeverReopens() throws Exception {
		FlowAdmission run = new FlowAdmission( 1 );
		Flow flow = flow( "never admitted" );
		ResourceRules rules = new ResourceRules().exclusive( "gate", f -> true );
		run.prepare( List.of( flow ), List.of( rules.resolve( flow ) ) );
		var scope = ResourceReservations.shared();
		var empty = new ResourceRules().resources( "empty", f -> true ).resolve( null );
		CountDownLatch notified = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		var waiting = scope.register( scope.capacity( 1 ), empty, () -> {
			notified.countDown();
			await( release );
		} );
		FutureTask<Void> stopping = new FutureTask<>( () -> {
			run.stop( new IllegalStateException( "requested stop" ) );
			return null;
		} );
		Thread thread = new Thread( stopping, "admission-stop" );
		AtomicReference<Grant> acquired = new AtomicReference<>();
		FutureTask<Boolean> probe = new FutureTask<>( () -> {
			synchronized( run.history() ) {
				acquired.set( waiting.tryAcquire() );
				return acquired.get() != null;
			}
		} );
		Throwable primary = null;
		try {
			try( Grant grant = waiting.tryAcquire() ) {
				assertNull( grant, "unpolled ready exclusive already gates EMPTY" );
			}
			thread.start();
			await( notified );
			new Thread( probe, "admission-lock-probe" ).start();
			assertTrue( probe.get( 5, TimeUnit.SECONDS ), "both locks are free during notification" );
			assertThrows( IllegalStateException.class, run::poll );
			assertFalse( run.enter( 0, "never emitted" ) );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			release.countDown();
			try {
				// Grant.close now wakes its own request too. Neither hold the owner
				// monitor nor await that notification before releasing this test hold.
				if( acquired.get() != null )
					acquired.get().close();
				if( thread.getState() != Thread.State.NEW )
					stopping.get( 5, TimeUnit.SECONDS );
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				primary.addSuppressed( cleanup );
			}
			finally {
				waiting.cancel();
				run.stop( new IllegalStateException( "test cleanup" ) );
			}
		}
		assertThrows( IllegalStateException.class, run::poll );
	}

	/**
	 * Readiness is published before a factory polls, not discovered by its retry
	 * order.
	 *
	 * @param capacity Outstanding grant limit
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void oldReadyRootPrecedesNewLowerCanonicalChild( int capacity ) {
		Flow parent = flow( "parent" );
		Flow child = flow( "child", parent );
		Flow oldRoot = flow( "old root" );
		List<Flow> flows = List.of( parent, child, oldRoot );
		ResourceRules rules = new ResourceRules().resources( "parent", f -> f == parent, "B" )
				.resources( "conflict", f -> f != parent, "A" );
		FlowAdmission run = new FlowAdmission( capacity );
		List<Integer> entered = new ArrayList<>();
		List<Integer> admitted = new ArrayList<>();
		boolean finalized = false;
		Throwable primary = null;
		try {
			run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
			assertEquals( 0, poll( run, admitted ) );
			enter( run, 0 );
			entered.add( 0 );
			complete( run, 0 );
			admitted.remove( Integer.valueOf( 0 ) );
			int next = poll( run, admitted );
			enter( run, next );
			entered.add( next );
			assertEquals( 2, next, "old root was ready before the cheaper child" );
			assertEquals( WAITING, poll( run, admitted ) );
			complete( run, 2 );
			admitted.remove( Integer.valueOf( 2 ) );
			assertEquals( 1, poll( run, admitted ) );
			enter( run, 1 );
			entered.add( 1 );
			complete( run, 1 );
			admitted.remove( Integer.valueOf( 1 ) );
			assertEquals( 1, run.successorVisits() );
			finish( run );
			finalized = true;
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			if( !finalized )
				cleanup( run, admitted, entered, primary );
		}
	}

	private static int poll( FlowAdmission run, List<Integer> admitted ) {
		int next = run.poll();
		if( next >= 0 )
			admitted.add( next );
		return next;
	}

	// These tests have no live bodies: entered work is synchronously drained, and
	// an admitted but unentered description is known unused, not force-released.
	private static void cleanup( FlowAdmission run, List<Integer> admitted,
			List<Integer> entered, Throwable primary ) {
		List<Runnable> actions = new ArrayList<>();
		actions.add( () -> run.stop( new IllegalStateException( "test cleanup" ) ) );
		admitted.forEach( index -> actions.add( () -> {
			if( entered.contains( index ) )
				complete( run, index );
			else {
				run.registered( index, "" + index );
				run.finished( "" + index, FlowAdmission.Outcome.ABORTED, null );
			}
		} ) );
		Throwable failure = primary;
		for( Runnable action : actions ) {
			try {
				action.run();
			}
			catch( Throwable cleanup ) {
				if( failure == null )
					failure = cleanup;
				else if( failure != cleanup )
					failure.addSuppressed( cleanup );
			}
		}
		if( primary == null && failure != null )
			throw new AssertionError( "test cleanup failed", failure );
	}

	/**
	 * Stop may return a between-member reservation, but not one still used by an
	 * entered member or its outer native cleanup.
	 *
	 * @param capacity Core/serial outstanding capacity
	 * @param between  Whether the first member has already terminated safely
	 */
	@ParameterizedTest
	@CsvSource({ "1,false", "2,false", "5,false", "1,true", "2,true", "5,true" })
	void stoppedChainsReleaseOnlyProvenDrainedUse( int capacity, boolean between ) {
		FlowAdmission run = prepare( capacity,
				List.of( emptyFlow( "A [chain:AB]" ), emptyFlow( "B [chain:AB]" ) ) );
		var scope = ResourceReservations.shared();
		var outside = scope.register( scope.capacity( 1 ),
				new ResourceRules().resources( "empty", f -> true ).resolve( null ), () -> {
				} );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		Throwable primary = null;
		try {
			run.processed( 0, Result.SUCCESS, null );
			if( between )
				run.finished( "0", SUCCESSFUL, null );
			run.stop( new IllegalStateException( "observed stop" ) );
			assertThrows( IllegalStateException.class, run::poll );
			assertFalse( run.enter( 1, "unused" ), "no stopped member may enter" );
			try( Grant grant = outside.tryAcquire() ) {
				if( between )
					assertNotNull( grant, "no active member remains" );
				else
					assertNull( grant, "body drainage alone does not prove native cleanup" );
			}
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			outside.cancel();
			try {
				complete( run, 0 );
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
		}
		assertThrows( IllegalStateException.class, run::close,
				"late drainage never restarts the owner" );
	}

	/**
	 * Every member's resource is required before the first member starts.
	 *
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void isolatedChainAcquiresWholeUnionBeforeItsFirstMember( int capacity ) {
		List<Flow> flows = List.of( emptyFlow( "A [chain:AB]" ), emptyFlow( "B [chain:AB]" ) );
		ResourceRules rules = new ResourceRules().isolatedChains( "whole-chain audit", "AB" )
				.resources( "A state", f -> f == flows.get( 0 ), "chain-A" )
				.resources( "B state", f -> f == flows.get( 1 ), "chain-B" );
		FlowAdmission run = new FlowAdmission( capacity );
		var scope = ResourceReservations.shared();
		var b = scope.register( scope.capacity( 1 ), rules.resolve( flows.get( 1 ) ), () -> {
		} );
		var a = scope.register( scope.capacity( 1 ), rules.resolve( flows.get( 0 ) ), () -> {
		} );
		run.prepare( flows, rules.chains( flows, flows.stream().map( rules::resolve ).toList() ) );
		try( Grant held = b.tryAcquire() ) {
			assertNotNull( held );
			assertEquals( WAITING, run.poll() );
			try( Grant free = a.tryAcquire() ) {
				assertNotNull( free, "older A may proceed: waiting whole chain holds no partial A" );
			}
		}
		finally {
			a.cancel();
			b.cancel();
		}
		for( int i = 0; i < 2; i++ ) {
			assertEquals( i, run.poll() );
			enter( run, i );
			complete( run, i );
		}
		finish( run );
	}

	/**
	 * Contract the combined graph, not just hard prerequisites. None of the
	 * order-only edges may be dropped to rescue an impossible uninterrupted chain.
	 *
	 * @param capacity   Core/serial outstanding capacity
	 * @param constraint Source of the contradictory precedence
	 */
	@ParameterizedTest
	@CsvSource({ "1,hard", "2,hard", "5,hard", "1,basis", "2,basis", "5,basis",
			"1,publication", "2,publication", "5,publication", "1,alias", "2,alias", "5,alias" })
	void chainContractionRejectsEveryKindOfCombinedContradiction( int capacity, String constraint ) {
		List<Flow> flows;
		if( constraint.equals( "basis" ) ) {
			Flw a = emptyFlow( "A1 [chain:A]" );
			Flw b = emptyFlow( "B []" ).basis( a );
			flows = List.of( a, b, emptyFlow( "A2 [chain:A]" ).basis( b ) );
		}
		else if( constraint.equals( "hard" ) ) {
			Flow a = Creator
					.build( f -> f.meta( m -> m.description( "A1" ).tags( t -> t.add( "chain:A" ) ) ) );
			Flow b = flow( "B", a );
			Flow end = Creator
					.build( f -> f.meta( m -> m.description( "A2" ).tags( t -> t.add( "chain:A" ) ) )
							.prerequisite( b ) );
			flows = List.of( a, b, end );
		}
		else {
			Text shared = new Text( "request" ) {
				@Override
				public Text child() {
					return this;
				}
			};
			List<Flow> producers = new ArrayList<>();
			for( String name : List.of( "A1", "B", "A2" ) ) {
				producers.add( Creator.build( f -> f.meta( m -> m.description( name ).tags( t -> {
					if( name.startsWith( "A" ) )
						t.add( "chain:A" );
				} ) ).call( i -> i.from( TestModel.Actors.A ).to( TestModel.Actors.B )
						.request( constraint.equals( "alias" ) ? shared : new Text( "request" ) )
						.response( new Text( name ) ) ) ) );
			}
			flows = new ArrayList<>( producers );
			if( constraint.equals( "publication" ) )
				flows.add( fanIn( "sink", producers, 2 ) );
		}
		ResourceRules rules = new ResourceRules().resources( "empty", f -> true )
				.isolatedChains( "isolation cannot erase ordering", "A" );
		FlowAdmission run = new FlowAdmission( capacity );
		assertTrue( assertThrows( IllegalArgumentException.class,
				() -> run.prepare( flows,
						rules.chains( flows, flows.stream().map( rules::resolve ).toList() ) ) )
								.getMessage().contains( "contracted chain" ) );
		assertThrows( IllegalStateException.class, run::poll, "no invalid plan can admit work" );
	}

	/**
	 * Auditing a whole chain permits disjoint units, not concurrent members or a
	 * level barrier between unrelated chains.
	 *
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void isolatedChainsContinueWithoutASeparateGrantOrCrossChainBarrier( int capacity ) {
		Flow a = emptyFlow( "A [chain:AB]" );
		Flow b = emptyFlow( "B [chain:AB]" );
		Flow c = emptyFlow( "C [chain:CD]" );
		Flow d = emptyFlow( "D [chain:CD]" );
		List<Flow> flows = List.of( a, b, c, d );
		ResourceRules rules = new ResourceRules().resources( "owned empty", f -> true )
				.isolatedChains( "whole scenarios have isolated state and callbacks", "AB", "CD" );
		FlowAdmission run = new FlowAdmission( capacity );
		run.prepare( flows, rules.chains( flows, flows.stream().map( rules::resolve ).toList() ) );
		List<Integer> entered = new ArrayList<>();
		try {
			assertEquals( 0, run.poll() );
			enter( run, 0 );
			entered.add( 0 );
			if( capacity > 1 ) {
				assertEquals( 2, run.poll(), "C may overlap A, B may not" );
				enter( run, 2 );
				entered.add( 2 );
			}
			assertEquals( WAITING, run.poll() );
			complete( run, 0 );
			assertEquals( 1, run.poll(), "B continues even while C is held" );
			enter( run, 1 );
			entered.add( 1 );
			complete( run, 1 );
			if( capacity == 1 ) {
				assertEquals( 2, run.poll() );
				enter( run, 2 );
				entered.add( 2 );
			}
			complete( run, 2 );
			assertEquals( 3, run.poll() );
			enter( run, 3 );
			entered.add( 3 );
			complete( run, 3 );
			finish( run );
			entered.clear();
		}
		finally {
			if( !entered.isEmpty() ) {
				run.stop( new IllegalStateException( "test cleanup" ) );
				entered.forEach( i -> complete( run, i ) );
			}
		}
	}

	/**
	 * A chain owns the whole interval, not merely each member's named keys.
	 *
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void defaultChainRetainsGlobalOwnershipThroughNativeCleanupAndBetweenMembers( int capacity ) {
		Flow a = Creator
				.build( f -> f.meta( m -> m.description( "A" ).tags( t -> t.add( "chain:scenario" ) ) ) );
		Flow b = Creator
				.build( f -> f.meta( m -> m.description( "B" ).tags( t -> t.add( "chain:scenario" ) ) ) );
		FlowAdmission run = prepare( capacity, List.of( a, b ) );
		var scope = ResourceReservations.shared();
		var empty = new ResourceRules().resources( "outside empty", f -> true ).resolve( null );
		var outside = scope.register( scope.capacity( 1 ), empty, () -> {
		} );
		List<Integer> entered = new ArrayList<>();
		try {
			assertEquals( 0, run.poll() );
			enter( run, 0 );
			entered.add( 0 );
			run.processed( 0, Result.SUCCESS, null );
			try( Grant unexpected = outside.tryAcquire() ) {
				assertNull( unexpected, "default chain excludes EMPTY during outer native cleanup" );
			}
			assertEquals( WAITING, run.poll(), "next member waits for actual native completion" );
			run.finished( "0", SUCCESSFUL, null );
			try( Grant unexpected = outside.tryAcquire() ) {
				assertNull( unexpected, "the same grant must survive the gap between members" );
			}
			assertEquals( 1, run.poll() );
			enter( run, 1 );
			entered.add( 1 );
			complete( run, 1 );
			try( Grant available = outside.tryAcquire() ) {
				assertNotNull( available );
			}
			finish( run );
			entered.clear();
		}
		finally {
			outside.cancel();
			if( !entered.isEmpty() ) {
				run.stop( new IllegalStateException( "test cleanup" ) );
				entered.forEach( i -> complete( run, i ) );
			}
		}
	}

	/**
	 * A blocked first publisher cannot be overtaken, but its error is not data for
	 * B.
	 *
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void canonicalPublicationWaitsBeforeAdmissionWithoutAddingHistoryDependencies( int capacity ) {
		Flow a = publicationFlow( "A" );
		Flow b = publicationFlow( "B" );
		Flow c = Creator.build( f -> f.meta( m -> m.description( "C" ) )
				.call( i -> i.from( TestModel.Actors.A ).to( TestModel.Actors.B )
						.request( new Text( "pending" ) ).response( new Text( "pending" ) ) )
				.dependency( a, d -> d.from( i -> true, Type.RESPONSE, ".+" )
						.to( i -> true, Type.REQUEST, ".+" ) )
				.dependency( b, d -> d.from( i -> true, Type.RESPONSE, ".+" )
						.to( i -> true, Type.RESPONSE, ".+" ) ) );
		List<Flow> flows = List.of( a, b, c, publicationFlow( "D" ) );
		ResourceRules rules = new ResourceRules().resources( "isolated", f -> true )
				.resources( "A prerequisite resource", f -> f == a, "publication-A" );
		var scope = ResourceReservations.shared();
		var holder = scope.register( scope.capacity( 1 ), rules.resolve( a ), () -> {
		} );
		FlowAdmission run = new FlowAdmission( capacity );
		run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
		try( Grant held = holder.tryAcquire() ) {
			assertNotNull( held );
			assertEquals( 0, b.dependencies().count(), "B is genuinely dependency-ready" );
			assertEquals( 3, run.poll(), "only unrelated D may pass resource-blocked A" );
			enter( run, 3 );
			complete( run, 3 );
			assertEquals( WAITING, run.poll(), "B waits before dispatch, not on a worker" );
		}
		finally {
			holder.cancel();
		}
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		RuntimeException failure = new IllegalArgumentException( "publication failed" );
		run.processed( 0, Result.ERROR, failure );
		assertEquals( WAITING, run.poll(), "processing return is not safe native completion" );
		run.finished( "0", FlowAdmission.Outcome.FAILED, failure );
		assertEquals( 1, run.poll() );
		assertTrue( run.history().skipReason( b, State.FUL, Set.of( TestModel.Actors.B ) ).isEmpty() );
		assertEquals( 0, b.dependencies().count() );
		enter( run, 1 );
		complete( run, 1 );
		assertEquals( 2, run.poll() );
		assertTrue( run.history().skipReason( c, State.FUL, Set.of( TestModel.Actors.B ) ).isPresent(),
				"the genuine failed source still suppresses C" );
		enter( run, 2 );
		run.processed( 2, Result.SKIP, null );
		run.finished( "2", FlowAdmission.Outcome.ABORTED, null );
		assertEquals( 3, run.successorVisits(), "two data edges and one order-only pair" );
		finish( run );
	}

	private static Flow publicationFlow( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( TestModel.Actors.A ).to( TestModel.Actors.B )
						.request( new Text( "request" ) ).response( new Text( name ) ) ) );
	}

	/**
	 * Writer ordering alone cannot protect a consumer of the same mutable message.
	 *
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void sharedMessageConsumersWaitForCanonicalWritersAcrossDestinations( int capacity ) {
		Flow a = publicationFlow( "A" );
		Flow b = publicationFlow( "B" );
		Text shared = new Text( "pending" ) {
			@Override
			public Text child() {
				return this;
			}
		};
		Flow c = publicationSink( "C", a, shared );
		Flow d = publicationSink( "D", b, shared );
		assertSame( c.root().request(), d.root().request() );
		FlowAdmission run = prepare( capacity, List.of( a, b, c, d, publicationFlow( "E" ) ) );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		complete( run, 0 );
		assertEquals( 1, run.poll() );
		enter( run, 1 );
		try {
			if( capacity > 1 ) {
				assertEquals( 4, run.poll(), "C must not consume while B can mutate D's alias" );
				enter( run, 4 );
				complete( run, 4 );
			}
			assertEquals( WAITING, run.poll() );
		}
		finally {
			complete( run, 1 );
		}
		for( int i : capacity == 1 ? List.of( 2, 3, 4 ) : List.of( 2, 3 ) ) {
			assertEquals( i, run.poll() );
			enter( run, i );
			assertEquals( i == (capacity == 1 ? 4 : 3) ? EXHAUSTED : WAITING, run.poll() );
			complete( run, i );
		}
		assertEquals( 5, run.successorVisits() );
		finish( run );
	}

	private static Flow publicationSink( String name, Flow source, Text message ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( TestModel.Actors.A ).to( TestModel.Actors.B )
						.request( message ).response( new Text( "response" ) ) )
				.dependency( source, d -> d.from( i -> true, Type.RESPONSE, ".+" )
						.to( i -> true, Type.REQUEST, ".+" ) ) );
	}

	/**
	 * Multiple destination memberships do not serialize the whole connected graph.
	 *
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void overlappingDestinationGroupsLeaveNonconflictingProducersConcurrent( int capacity ) {
		Flow a = publicationFlow( "A" );
		Flow b = publicationFlow( "B" );
		Flow c = publicationFlow( "C" );
		Flow x = fanIn( "X", List.of( a, c ), 1 );
		Flow y = fanIn( "Y", List.of( b, c ), 1 );
		FlowAdmission run = prepare( capacity, List.of( a, b, c, x, y ) );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		try {
			if( capacity > 1 ) {
				assertEquals( 1, run.poll(), "B shares a graph component with A, but not a destination" );
				enter( run, 1 );
				complete( run, 1 );
			}
			assertEquals( WAITING, run.poll(), "C participates in both destination groups" );
		}
		finally {
			complete( run, 0 );
		}
		for( int i = capacity == 1 ? 1 : 2; i < 5; i++ ) {
			assertEquals( i, run.poll() );
			enter( run, i );
			complete( run, i );
		}
		assertEquals( 6, run.successorVisits() );
		finish( run );
	}

	/**
	 * Retained group edges grow with memberships, not all producer pairs.
	 *
	 * @param size     Number of producers
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@CsvSource({ "100,1", "100,2", "100,5", "1000,1", "1000,2", "1000,5" })
	void publicationGroupsKeepOnlyAdjacentCanonicalEdgesAndEveryBinding( int size, int capacity ) {
		List<Flow> producers = IntStream.range( 0, size ).mapToObj( i -> publicationFlow( "p" + i ) )
				.toList();
		Flow sink = fanIn( "sink", producers, 2 );
		List<Flow> flows = new ArrayList<>( producers );
		flows.add( sink );
		FlowAdmission run = prepare( capacity, flows );
		for( int i = 0; i < flows.size(); i++ ) {
			assertEquals( i, run.poll() );
			enter( run, i );
			try {
				assertEquals( i == size ? EXHAUSTED : WAITING, run.poll() );
			}
			finally {
				complete( run, i );
			}
		}
		assertEquals( 2L * size, sink.dependencies().count() );
		assertEquals( 2L * size - 1, run.successorVisits(), "K hard edges plus K-1 order-only pairs" );
		finish( run );
	}

	private static Flow fanIn( String name, List<Flow> sources, int multiplicity ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) )
					.call( i -> i.from( TestModel.Actors.A ).to( TestModel.Actors.B )
							.request( new Text( "pending" ) ).response( new Text( "response" ) ) );
			for( Flow source : sources )
				for( int binding = 0; binding < multiplicity; binding++ )
					f.dependency( source, d -> d.from( i -> true, Type.RESPONSE, ".+" )
							.to( i -> true, Type.REQUEST, ".+" ) );
		} );
	}

	/**
	 * The logical window is an exact core bound, independent of Jupiter scheduling.
	 */
	@Test
	void twentyFourOutstandingGrantsBlockTheNextEmissionUntilNativeDrainage() {
		List<Flow> flows = IntStream.range( 0, 40 ).mapToObj( i -> flow( "flow" + i ) ).toList();
		FlowAdmission run = prepare( 24, flows );
		for( int i = 0; i < 24; i++ ) {
			assertEquals( i, run.poll() );
			enter( run, i );
		}
		try {
			assertEquals( WAITING, run.poll() );
			run.processed( 0, Result.SUCCESS, null );
			assertEquals( WAITING, run.poll(), "processing alone does not free a slot" );
		}
		finally {
			for( int i = 0; i < 24; i++ )
				complete( run, i );
		}
		for( int i = 24; i < 40; i++ ) {
			assertEquals( i, run.poll() );
			enter( run, i );
			complete( run, i );
		}
		finish( run );
	}

	/**
	 * A fork/join's own counters, not level membership, govern release.
	 * 
	 * @param capacity Core/serial outstanding capacity
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void forkJoinCompletesExactlyOnceAtEachCoreCapacity( int capacity ) {
		Flow root = flow( "root" );
		Flow left = flow( "left", root, root );
		Flow right = flow( "right", root );
		Flow join = flow( "join", left, right );
		Flow unrelated = flow( "unrelated" );
		FlowAdmission run = prepare( capacity, List.of( root, left, right, join, unrelated ) );
		Deque<Integer> held = new ArrayDeque<>();
		List<Integer> completed = new ArrayList<>();
		int peak = 0;
		try {
			for( ;; ) {
				int next = run.poll();
				if( next >= 0 ) {
					if( next == 3 ) {
						assertTrue( completed.containsAll( List.of( 1, 2 ) ) );
					}
					enter( run, next );
					held.addLast( next );
					peak = Math.max( peak, held.size() );
				}
				else if( !held.isEmpty() ) {
					int finished = held.removeFirst();
					complete( run, finished );
					completed.add( finished );
				}
				else {
					assertEquals( EXHAUSTED, next, "no unexplained nonterminal stall" );
					break;
				}
			}
			assertEquals( Set.of( 0, 1, 2, 3, 4 ), Set.copyOf( completed ) );
			assertEquals( 5, completed.size() );
			assertEquals( Math.min( capacity, 3 ), peak );
			assertEquals( 4, run.successorVisits(), "duplicate bindings remain one scheduling edge" );
			finish( run );
		}
		finally {
			held.forEach( i -> complete( run, i ) );
		}
	}

	/** Native terminal and publication are separate from readiness and capacity. */
	@Test
	void independentSuccessorDoesNotWaitForAnUnrelatedHeldBranch() {
		Flow a = flow( "a" );
		Flow b = flow( "b", a );
		Flow c = flow( "c" );
		Flow d = flow( "d", c );
		FlowAdmission run = prepare( 2, List.of( a, b, c, d ) );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		assertEquals( 2, run.poll() );
		enter( run, 2 );
		try {
			assertEquals( WAITING, run.poll() );
			run.processed( 0, Result.SUCCESS, null );
			assertEquals( Result.SUCCESS, run.history().get( a ) );
			assertEquals( WAITING, run.poll(), "body return is not native cleanup completion" );
			run.finished( "0", SUCCESSFUL, null );
			assertEquals( 1, run.poll(), "b can proceed while c is still held" );
			enter( run, 1 );
			complete( run, 1 );
			assertEquals( WAITING, run.poll(), "d still needs its own c" );
		}
		finally {
			complete( run, 2 );
		}
		assertEquals( 3, run.poll() );
		enter( run, 3 );
		complete( run, 3 );
		assertEquals( 2, run.successorVisits() );
		finish( run );
	}

	/** Duplicate evidence is idempotent; conflicts stop queued pre-use. */
	@Test
	void duplicatesDoNotDoubleCountAndConflictingEvidenceStaysLatched() {
		Flow first = flow( "first" );
		Flow next = flow( "next", first );
		FlowAdmission run = prepare( 2, List.of( first, next ) );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		run.registered( 0, "0" );
		run.started( "0" );
		complete( run, 0 );
		complete( run, 0 );
		assertEquals( 1, run.successorVisits() );
		assertEquals( 1, run.poll() );
		run.registered( 1, "1" );
		run.started( "1" );
		IllegalStateException conflict = assertThrows( IllegalStateException.class,
				() -> run.finished( "0", FlowAdmission.Outcome.FAILED,
						new IllegalStateException( "different" ) ) );
		assertFalse( run.enter( 1, "1" ), "already emitted work cannot bypass stop" );
		run.finished( "1", FlowAdmission.Outcome.ABORTED, null );
		assertSame( conflict, assertThrows( IllegalStateException.class, run::poll ).getCause() );
		assertEquals( Result.SUCCESS, run.history().get( first ) );
		assertEquals( Result.PENDING, run.history().get( next ),
				"absence is not success or a made-up skip" );
		assertThrows( IllegalStateException.class, run::close );
	}

	/**
	 * Terminal delivery cannot release ongoing use; late drainage never restarts.
	 */
	@Test
	void earlyTerminalRetainsUseUntilLateDrainageAndNeverRestarts() {
		Flow first = flow( "first" );
		Flow next = flow( "next", first );
		var rules = new ResourceRules().resources( "fixture", f -> true, "admission-late" );
		FlowAdmission run = new FlowAdmission( 2 );
		run.prepare( List.of( first, next ), List.of( rules.resolve( first ), rules.resolve( next ) ) );
		var scope = ResourceReservations.shared();
		var probe = scope.register( scope.capacity( 1 ), rules.resolve( first ), () -> {
		} );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		try {
			run.finished( "0", FlowAdmission.Outcome.FAILED, new IllegalStateException( "early" ) );
			assertNull( probe.tryAcquire() );
			assertThrows( IllegalStateException.class, run::poll );
		}
		finally {
			run.processed( 0, Result.ERROR, new IllegalArgumentException( "actual" ) );
		}
		try( Grant released = probe.tryAcquire() ) {
			assertNotNull( released );
		}
		finally {
			probe.cancel();
		}
		assertThrows( IllegalStateException.class, run::poll );
		assertEquals( 0, run.successorVisits() );
		assertThrows( IllegalStateException.class, run::close );
	}

	/**
	 * Basis preferences neither auto-select absent ancestors nor defeat hard order.
	 */
	@Test
	void selectedAncestorAcrossAnAbsentBasisAndHardReversedBasisFollowCanonicalOrder() {
		Flow ancestor = flow( "ancestor" );
		Flow absent = Deriver.build( ancestor, f -> f.meta( m -> m.description( "absent" ) ) );
		Flow child = Deriver.build( absent, f -> f.meta( m -> m.description( "child" ) ) );
		FlowAdmission run = prepare( 5, List.of( ancestor, child ) );
		assertEquals( 0, run.poll() );
		assertEquals( WAITING, run.poll() );
		enter( run, 0 );
		complete( run, 0 );
		assertEquals( 1, run.poll() );
		enter( run, 1 );
		complete( run, 1 );
		finish( run );
		FlowAdmission absentRun = prepare( 1, List.of( child ) );
		assertEquals( 0, absentRun.poll() );
		enter( absentRun, 0 );
		complete( absentRun, 0 );
		finish( absentRun );
		// Preserve the serial visibility of PENDING for a later ancestor too.
		FlowAdmission reversed = prepare( 2, List.of( child, ancestor ) );
		assertEquals( 0, reversed.poll() );
		enter( reversed, 0 );
		assertEquals( WAITING, reversed.poll() );
		complete( reversed, 0 );
		assertEquals( 1, reversed.poll() );
		enter( reversed, 1 );
		complete( reversed, 1 );
		finish( reversed );
		Flw hardChild = emptyFlow( "hard child []" );
		Flow hardAncestor = flow( "hard ancestor", hardChild, hardChild );
		hardChild.basis( hardAncestor );
		FlowAdmission hard = prepare( 2, List.of( hardChild, hardAncestor ) );
		assertEquals( 0, hard.poll() );
		enter( hard, 0 );
		assertEquals( WAITING, hard.poll() );
		complete( hard, 0 );
		assertEquals( 1, hard.poll() );
		enter( hard, 1 );
		complete( hard, 1 );
		assertEquals( 1, hard.successorVisits(), "hard/basis pair deduplicated, bindings retained" );
		assertEquals( 2, hardAncestor.dependencies().count() );
		finish( hard );
	}

	/**
	 * Deep bases and shared absent paths must not become an ancestor closure.
	 *
	 * @param size  Selected flow count
	 * @param shape Basis shape and canonical rank order
	 * @throws IOException If measurement evidence cannot be retained
	 */
	@ParameterizedTest
	@CsvSource({ "100,deep", "1000,deep", "7000,deep", "100,inverted", "1000,inverted",
			"7000,inverted", "100,siblings", "1000,siblings", "7000,siblings" })
	void basisReadinessRetainsLinearEdgesAndVisitsSharedAbsentPathsOnce( int size, String shape )
			throws IOException {
		AtomicInteger basisCalls = new AtomicInteger();
		List<Flow> flows = new ArrayList<>();
		Flow basis = null;
		for( int i = 0; i < size; i++ ) {
			basis = basisFlow( "basis" + i, basis, basisCalls, 4 * size );
			if( !shape.equals( "siblings" ) || i == 0 )
				flows.add( basis );
		}
		if( shape.equals( "siblings" ) ) {
			for( int i = 1; i < size; i++ )
				flows.add( basisFlow( "sibling" + i, basis, basisCalls, 4 * size ) );
		}
		if( shape.equals( "inverted" ) )
			Collections.shuffle( flows, new Random( 13 ) );
		long preparing = System.nanoTime();
		FlowAdmission run = prepare( 5, flows );
		long preparationNanos = System.nanoTime() - preparing;
		int uniqueFlows = shape.equals( "siblings" ) ? 2 * size - 1 : size;
		assertEquals( uniqueFlows, basisCalls.get(), "one basis lookup per distinct identity" );
		for( int i = 0; i < size; i++ ) {
			assertEquals( i, run.next( () -> {
			} ) );
			enter( run, i );
			try {
				if( !shape.equals( "siblings" ) && i < size - 1 )
					assertEquals( WAITING, run.poll(), "all nodes on one basis path are comparable" );
			}
			finally {
				complete( run, i );
			}
		}
		// Every node completed once, so the visits count is the retained edge count.
		assertTrue( run.successorVisits() <= 2L * size, "at most two basis edges per selected flow" );
		if( shape.equals( "siblings" ) )
			assertEquals( size - 1, run.successorVisits() );
		Files.writeString( Files.createDirectories( Path.of( "target", "admission13" ) )
				.resolve( "basis-" + shape + "-" + size + ".txt" ),
				"shape=" + shape + ", V=" + size + ", basisDepth=" + (size - 1
						+ (shape.equals( "siblings" ) ? 1 : 0)) + ", basisCalls=" + basisCalls.get()
						+ ", uniqueFlows=" + uniqueFlows + ", retainedEdges=" + run.successorVisits()
						+ ", successorVisits=" + run.successorVisits() + ", emitted=" + size
						+ ", preparationNanos=" + preparationNanos + "\n" );
		finish( run );
	}

	/** Sibling branches must not be serialized behind one another. */
	@Test
	void basisBranchesProceedWhileAnUnrelatedSiblingIsHeld() {
		Flw root = emptyFlow( "root []" );
		Flw left = emptyFlow( "left []" ).basis( root );
		Flw right = emptyFlow( "right []" ).basis( root );
		Flw leaf = emptyFlow( "right leaf []" ).basis( right );
		FlowAdmission run = prepare( 5, List.of( root, left, right, leaf ) );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		complete( run, 0 );
		assertEquals( 1, run.poll() );
		enter( run, 1 );
		try {
			assertEquals( 2, run.poll() );
			enter( run, 2 );
			complete( run, 2 );
			assertEquals( 3, run.poll() );
			enter( run, 3 );
			complete( run, 3 );
		}
		finally {
			complete( run, 1 );
		}
		assertEquals( 3, run.successorVisits() );
		finish( run );
	}

	private static Flow basisFlow( String name, Flow basis, AtomicInteger calls, int limit ) {
		return new Flw( name + " []" ) {
			@Override
			public com.mastercard.test.flow.Interaction root() {
				return null;
			}

			@Override
			public Flow basis() {
				assertTrue( calls.incrementAndGet() <= limit, "repeated ancestry traversal is not linear" );
				return basis;
			}
		};
	}

	/**
	 * Admission work visits each direct edge once, not every node per completion.
	 *
	 * @param size  Selected node count
	 * @param shape Direct prerequisite shape
	 * @throws IOException If measurement evidence cannot be retained
	 */
	@ParameterizedTest
	@CsvSource({ "100,fork", "1000,fork", "7000,fork", "100,chain", "1000,chain", "7000,chain",
			"100,fanin", "1000,fanin", "7000,fanin" })
	void readinessCountsOnlyDirectSuccessorTransitions( int size, String shape ) throws IOException {
		List<Flow> flows = new ArrayList<>( List.of( flow( "root" ) ) );
		for( int i = 1; i < size; i++ ) {
			Flow previous = flows.get( i - 1 );
			flows.add( shape.equals( "fork" ) ? flow( "child" + i, flows.get( 0 ) )
					: shape.equals( "fanin" ) && i > 1 ? flow( "join" + i, previous, flows.get( i - 2 ) )
							: flow( "child" + i, previous ) );
		}
		long prepared = System.nanoTime();
		FlowAdmission run = prepare( 5, flows );
		long preparationNanos = System.nanoTime() - prepared;
		long execution = System.nanoTime();
		for( int i = 0; i < flows.size(); i++ ) {
			assertEquals( i, run.poll() );
			enter( run, i );
			complete( run, i );
		}
		long executionNanos = System.nanoTime() - execution;
		assertEquals( shape.equals( "fanin" ) ? 2 * size - 3 : size - 1, run.successorVisits() );
		Files.writeString( Files.createDirectories( Path.of( "target", "admission13" ) )
				.resolve( "readiness-" + shape + "-" + size + ".txt" ),
				"shape=" + shape + ", V=" + size
						+ ", emitted=" + flows.size() + ", successorVisits=" + run.successorVisits()
						+ ", preparationNanos=" + preparationNanos + ", dispatchAndCompletionNanos="
						+ executionNanos
						+ " (includes resource, History and test calls; not isolated readiness time)\n" );
		finish( run );
	}

	/**
	 * Source methods and model equality never execute under the History monitor.
	 */
	@Test
	void planningAndHistoryTraverseTheModelOutsideBookkeeping() {
		FlowAdmission run = new FlowAdmission( 1 );
		Flw first = new Flw( "first []" ) {
			@Override
			public com.mastercard.test.flow.Interaction root() {
				assertFalse( Thread.holdsLock( run.history() ) );
				return null;
			}

			@Override
			public Flow basis() {
				assertFalse( Thread.holdsLock( run.history() ) );
				return super.basis();
			}

			@Override
			public Stream<Dependency> dependencies() {
				assertFalse( Thread.holdsLock( run.history() ) );
				return super.dependencies();
			}

			@Override
			public int hashCode() {
				assertFalse( Thread.holdsLock( run.history() ) );
				return super.hashCode();
			}
		};
		var rules = new ResourceRules().resources( "owned empty", f -> true );
		run.prepare( List.of( first ), List.of( rules.resolve( first ) ) );
		assertTrue( run.history().skipReason( first, State.LESS, Set.of() ).isEmpty() );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		complete( run, 0 );
		assertEquals( Result.SUCCESS, run.history().get( first ) );
		finish( run );
	}

	/**
	 * Only the preparing factory can wait; completion cannot lose its wakeup.
	 *
	 * @throws Exception If controlled factory coordination fails
	 */
	@Test
	void oneFactoryWaiterIsReleasedByItsOwnPredecessor() throws Exception {
		AtomicReference<FlowAdmission> owner = new AtomicReference<>();
		CountDownLatch checking = new CountDownLatch( 1 );
		FutureTask<Void> factory = new FutureTask<>( () -> {
			Flow first = flow( "first" );
			FlowAdmission run = prepare( 1, List.of( first, flow( "next", first ) ) );
			owner.set( run );
			assertEquals( 0, run.poll() );
			enter( run, 0 );
			assertEquals( WAITING, run.poll() );
			assertEquals( 1, run.next( () -> {
				assertFalse( Thread.holdsLock( run.history() ), "adapter check is outside bookkeeping" );
				checking.countDown();
			} ) );
			enter( run, 1 );
			complete( run, 1 );
			finish( run );
			return null;
		} );
		Thread thread = new Thread( factory, "admission-factory" );
		thread.start();
		try {
			assertTrue( checking.await( 5, TimeUnit.SECONDS ) );
			assertThrows( IllegalStateException.class,
					() -> owner.get().next( () -> fail( "second waiter" ) ) );
		}
		finally {
			if( owner.get() != null )
				complete( owner.get(), 0 );
		}
		factory.get( 5, TimeUnit.SECONDS );
		thread.join( 1000 );
		assertFalse( thread.isAlive() );
	}

	/** Native progress is requested only when ordinary admission cannot proceed. */
	@Test
	void nativeProgressCanCompleteEmittedWorkBeforeFactoryWaits() {
		Flow first = flow( "first" );
		FlowAdmission run = prepare( 1, List.of( first, flow( "next", first ) ) );
		AtomicInteger validations = new AtomicInteger();
		AtomicInteger progress = new AtomicInteger();
		assertEquals( 0, run.next( validations::incrementAndGet,
				() -> fail( "ready admission must not request native progress" ) ) );
		enter( run, 0 );
		assertEquals( 1, run.next( validations::incrementAndGet, () -> {
			assertFalse( Thread.holdsLock( run.history() ) );
			progress.incrementAndGet();
			complete( run, 0 );
		} ) );
		assertEquals( 3, validations.get() );
		assertEquals( 1, progress.get() );
		enter( run, 1 );
		complete( run, 1 );
		finish( run );
	}

	/**
	 * Resource notifications can observe History on another thread without
	 * deadlock.
	 */
	@Test
	void resourceReleaseRunsOutsideTheHistoryMonitor() throws Exception {
		Flow first = flow( "first" );
		var rules = new ResourceRules().resources( "fixture", f -> true, "admission-monitor" );
		FlowAdmission run = new FlowAdmission( 1 );
		run.prepare( List.of( first ), List.of( rules.resolve( first ) ) );
		assertEquals( 0, run.poll() );
		enter( run, 0 );
		var scope = ResourceReservations.shared();
		var probe = scope.register( scope.capacity( 1 ), rules.resolve( first ), () -> {
			FutureTask<Result> read = new FutureTask<>( () -> run.history().get( first ) );
			Thread thread = new Thread( read, "admission-history-observer" );
			thread.start();
			try {
				assertEquals( Result.SUCCESS, read.get( 5, TimeUnit.SECONDS ) );
			}
			catch( Exception failure ) {
				throw new AssertionError( failure );
			}
		} );
		try {
			complete( run, 0 );
		}
		finally {
			probe.cancel();
		}
		finish( run );
	}

	/**
	 * Unemitted proof retires a handoff, not an operation that still uses its
	 * grant.
	 */
	@Test
	void unusedHandoffRetainsOutstandingOperationWithoutInventingResults() {
		Flow flow = flow( "unemitted" );
		FlowAdmission run = prepare( 1, List.of( flow ) );
		assertEquals( 0, run.poll() );
		Grant grant = run.reservation( 0 );
		var operation = grant.operation();
		Throwable cause = new IllegalStateException( "before native handoff" );
		try {
			run.stop( cause );
			run.unused( 0 );
			assertFalse( grant.released() );
			assertEquals( 1, run.status().owners() );
			assertEquals( 0, run.status().entered() );
			assertEquals( 0, run.status().completed() );
			assertEquals( 0, run.status().nativeTerminals() );
			assertEquals( Result.PENDING, run.history().get( flow ) );
			assertThrows( IllegalStateException.class, () -> run.reservation( 0 ) );
			assertThrows( IllegalStateException.class, () -> run.unused( 0 ) );
			operation.complete();
			assertTrue( grant.released() );
			assertEquals( 0, run.status().owners() );
			assertSame( cause, run.stopCause() );
		}
		finally {
			operation.complete();
			run.enclosingFinished( cause );
			run.release();
		}
	}

	/**
	 * Actual skip evidence is idempotent, remains distinct from History and stops
	 * admission.
	 */
	@Test
	void nativeSkipReturnsOwnershipWithoutInventingProcessing() {
		Flow flow = flow( "skipped" );
		FlowAdmission run = prepare( 1, List.of( flow ) );
		assertEquals( 0, run.poll() );
		Grant grant = run.reservation( 0 );
		run.registered( 0, "skipped-leaf" );
		try {
			run.skipped( "skipped-leaf", "native reason" );
			Throwable cause = run.stopCause();
			assertEquals( "Native Flow skipped: native reason", cause.getMessage() );
			run.skipped( "skipped-leaf", "native reason" );
			assertTrue( grant.released() );
			assertEquals( 0, run.status().owners() );
			assertEquals( 0, run.status().nativeTerminals() );
			assertEquals( 0, run.status().entered() );
			assertEquals( Result.PENDING, run.history().get( flow ) );
			assertSame( cause, assertThrows( IllegalStateException.class, run::poll ).getCause() );
			assertThrows( IllegalStateException.class, () -> run.started( "skipped-leaf" ) );
			assertThrows( IllegalStateException.class,
					() -> run.skipped( "skipped-leaf", "different reason" ) );
			assertThrows( IllegalStateException.class,
					() -> run.finished( "skipped-leaf", SUCCESSFUL, null ) );
			assertSame( cause, run.stopCause() );
		}
		finally {
			run.enclosingFinished( new IllegalStateException( "test scope ended" ) );
			run.release();
		}
	}

	/**
	 * Receipt delivery is factory-owned, outside History, and restored after a
	 * callback throws.
	 */
	@Test
	void receiptFailureRestoresFactoryDeliveryWithoutRetiringTheGrant() throws Exception {
		FlowAdmission run = prepare( 1, List.of( flow( "receipt" ) ) );
		assertEquals( 0, run.poll() );
		Grant grant = run.reservation( 0 );
		var cause = new IllegalArgumentException( "delivery" );
		try {
			assertSame( cause, assertThrows( IllegalArgumentException.class, () -> run.receipt( 0, g -> {
				assertSame( grant, g );
				assertFalse( Thread.holdsLock( run.history() ) );
				assertThrows( IllegalStateException.class,
						() -> run.receipt( 0, nested -> fail( "nested delivery" ) ) );
				throw cause;
			} ) ) );
			FutureTask<Void> foreign = new FutureTask<>( () -> {
				assertThrows( IllegalStateException.class,
						() -> run.receipt( 0, g -> fail( "foreign delivery" ) ) );
				return null;
			} );
			Thread thread = new Thread( foreign, "foreign-receipt" );
			thread.start();
			foreign.get( 5, TimeUnit.SECONDS );
			thread.join( 1000 );
			assertFalse( thread.isAlive() );
			run.receipt( 0, g -> assertSame( grant, g ) );
			assertFalse( grant.released() );
			assertNull( run.stopCause() );
			enter( run, 0 );
			complete( run, 0 );
			finish( run );
		}
		finally {
			run.enclosingFinished( new IllegalStateException( "test scope ended" ) );
			if( run.disposable() )
				run.release();
		}
	}

	private static Flow flow( String name, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Flw emptyFlow( String name ) {
		return new Flw( name ) {
			@Override
			public com.mastercard.test.flow.Interaction root() {
				return null;
			}
		};
	}

	private static FlowAdmission prepare( int capacity, List<Flow> flows ) {
		FlowAdmission run = new FlowAdmission( capacity );
		ResourceRules rules = new ResourceRules().resources( "owned empty", f -> true );
		run.prepare( flows, flows.stream().map( rules::resolve ).toList() );
		return run;
	}

	private static void enter( FlowAdmission run, int index ) {
		run.registered( index, "" + index );
		run.started( "" + index );
		assertTrue( run.enter( index, "" + index ) );
	}

	private static void complete( FlowAdmission run, int index ) {
		run.processed( index, Result.SUCCESS, null );
		run.finished( "" + index, SUCCESSFUL, null );
	}

	private static void finish( FlowAdmission run ) {
		assertEquals( EXHAUSTED, run.poll() );
		run.enumerationClosed();
		assertTrue( run.factoryFinished( SUCCESSFUL, null ) );
		assertFalse( run.factoryFinished( SUCCESSFUL, null ), "exactly one finalization" );
		run.release();
		assertDoesNotThrow( run::close );
	}
}
