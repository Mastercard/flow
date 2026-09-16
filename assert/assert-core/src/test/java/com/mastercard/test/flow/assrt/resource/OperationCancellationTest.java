package com.mastercard.test.flow.assrt.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.ContextDomain;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Operation;

/** Exact operation cancellation through the shared reservation seam. */
class OperationCancellationTest {
	/**
	 * A physical fixture configures one handler before publishing its footprint.
	 */
	@Test
	void fixtureRegistrationIsFixedBeforeSharingAndBoundToExactOperations() {
		var domain = new ContextDomain();
		List<Operation> delivered = new CopyOnWriteArrayList<>();
		assertSame( domain, domain.cancellation( delivered::add ) );
		assertThrows( IllegalStateException.class, () -> domain.cancellation( delivered::add ) );
		var scope = ResourceReservations.shared();
		var capacity = scope.capacity( 1 );
		var request = scope.register( capacity, domain.requirements(), () -> {
		} );
		try( var grant = request.tryAcquire() ) {
			var receipt = domain.receipt( grant );
			var operation = receipt.operation();
			try {
				assertTrue( delivered.isEmpty(), "admission and registration do not request cancellation" );
				capacity.stopping( new IllegalStateException( "fixture owner Stop" ) );
				assertEquals( List.of( operation ), delivered );
				assertEquals( 1, grant.operations() );
				assertNull( domain.uncertainty(), "request return does not damage fixture state" );
			}
			finally {
				operation.complete();
			}
		}
		finally {
			request.cancel();
		}
		var alreadyShared = new ContextDomain();
		alreadyShared.requirements();
		assertThrows( IllegalStateException.class,
				() -> alreadyShared.cancellation( delivered::add ) );
		try( var use = domain.tryAcquire() ) {
			assertNotNull( use );
		}
	}

	/**
	 * All grants and callbacks are accounted before any delivery. Actual proof and
	 * callback return are independent, even when delivery throws or proof wins.
	 *
	 * @throws Exception If controlled Stop fails to drain
	 */
	@Test
	void stopClaimsEveryLiveOperationBeforeDeliveryAndRetainsUntilBothProofs() throws Exception {
		var scope = ResourceReservations.shared();
		var owner = scope.capacity( 2 );
		var other = scope.capacity( 1 );
		var empty = new ResourceRules().resources( "empty", f -> true ).resolve( null );
		var requests = scope.register( owner, List.of( empty, empty ), () -> {
		} );
		var peer = scope.register( other, empty, () -> {
		} );
		var conflict = scope.register( scope.capacity( 1 ),
				new ResourceRules().exclusive( "all", f -> true ).resolve( null ), () -> {
				} );
		var cause = new IllegalStateException( "first Stop" );
		var callbackFailure = new IllegalArgumentException( "client cancel failed" );
		var entered = new CountDownLatch( 1 );
		var release = new CountDownLatch( 1 );
		List<Operation> delivered = new CopyOnWriteArrayList<>();
		try( var first = requests.get( 0 ).tryAcquire();
				var second = requests.get( 1 ).tryAcquire();
				var independent = peer.tryAcquire() ) {
			var completed = first.operation( delivered::add );
			var live = first.operation( operation -> {
				delivered.add( operation );
				entered.countDown();
				await( release );
				throw callbackFailure;
			} );
			var late = second.operation( delivered::add );
			var uncooperative = second.operation( delivered::add );
			var isolated = independent.operation( delivered::add );
			completed.complete();
			FutureTask<Void> stopping = new FutureTask<>( () -> {
				assertSame( callbackFailure,
						assertThrows( IllegalArgumentException.class, () -> owner.stopping( cause ) ) );
				return null;
			} );
			Thread thread = new Thread( stopping, "controlled-operation-stop" );
			Throwable primary = null;
			try {
				thread.start();
				await( entered );
				assertEquals( List.of( live ), delivered );
				assertThrows( IllegalStateException.class, second::operation,
						"later grants are stopped before the first hook returns" );
				owner.stopping( new IllegalStateException( "concurrent repeated Stop" ) );
				assertSame( cause, owner.uncertainty() );
				live.complete();
				late.complete();
				first.close();
				second.close();
				assertEquals( 0, first.operations(), "callback work is not an actual operation" );
				assertTrue( first.pending(), "continuation must wait for claimed callback work" );
				assertEquals( 2, owner.owned(), "held callback retains the whole first grant" );
				assertSame( cause,
						assertThrows( IllegalStateException.class, conflict::tryAcquire ).getCause() );
				release.countDown();
				stopping.get( 5, TimeUnit.SECONDS );
				assertFalse( first.pending() );
				assertEquals( List.of( live, late, uncooperative ), delivered,
						"claimed delivery survives proof and earlier callback failure, not other owners" );
				assertEquals( 1, owner.owned(), "callback return/throw does not prove uncooperative use" );
				assertEquals( 1, second.operations() );
				assertSame( cause,
						assertThrows( IllegalStateException.class, conflict::tryAcquire ).getCause() );
				uncooperative.complete();
				assertEquals( 0, owner.owned() );
				assertEquals( 1, independent.operations() );
				owner.stopping( cause );
				assertEquals( 3, delivered.size() );
			}
			catch( Throwable failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				release.countDown();
				Throwable failure = primary;
				for( var operation : List.of( completed, live, late, uncooperative, isolated ) ) {
					try {
						operation.complete();
					}
					catch( Throwable cleanup ) {
						if( failure == null )
							failure = cleanup;
						else if( failure != cleanup )
							failure.addSuppressed( cleanup );
					}
				}
				try {
					thread.join( 5000 );
					assertFalse( thread.isAlive(), "Stop thread drained" );
					if( thread.getState() != Thread.State.NEW )
						stopping.get( 5, TimeUnit.SECONDS );
				}
				catch( Throwable cleanup ) {
					if( failure == null )
						failure = cleanup;
					else if( failure != cleanup )
						failure.addSuppressed( cleanup );
				}
				if( primary == null && failure != null ) {
					if( failure instanceof Exception exception )
						throw exception;
					throw (Error) failure;
				}
			}
		}
		finally {
			requests.forEach( ResourceReservations.Request::cancel );
			peer.cancel();
			conflict.cancel();
		}
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 5, TimeUnit.SECONDS ), "controlled gate timed out" );
		}
		catch( InterruptedException failure ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( failure );
		}
	}
}
