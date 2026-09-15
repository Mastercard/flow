package com.mastercard.test.flow.assrt.resource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;

/**
 * Pure public planning/reservation seam, shared by runners and fixture owners.
 */
class ResourcePlanningTest {
	/** Checks immutable rule unions, provenance and exclusive precedence. */
	@Test
	void unionKeepsEveryMatchingRuleAndExclusiveCannotBeErased() {
		Flow flow = Creator.build( f -> f.meta( m -> m.description( "audit" ) ) );
		AtomicInteger calls = new AtomicInteger();
		String[] keys = { "account", "queue" };
		ResourceRules rules = new ResourceRules()
				.resources( "state", f -> {
					calls.incrementAndGet();
					return true;
				}, keys )
				.exclusive( "reset", f -> {
					calls.incrementAndGet();
					return true;
				} )
				.resources( "empty", f -> {
					calls.incrementAndGet();
					return true;
				} )
				.resources( "unrelated", f -> {
					calls.incrementAndGet();
					return false;
				}, "other" );
		keys[0] = "mutated";
		ResourceRequirements result = rules.resolve( flow );
		assertEquals( Set.of( "account", "queue" ), result.keys() );
		assertEquals( List.of( "state", "reset", "empty" ), result.rules().stream().toList() );
		assertTrue( result.exclusive() );
		assertFalse( result.unknown() );
		assertEquals( 4, calls.get() );
		assertThrows( UnsupportedOperationException.class, () -> result.keys().clear() );
		assertThrows( UnsupportedOperationException.class, () -> result.rules().clear() );
		assertThrows( IllegalArgumentException.class, () -> rules.resources( "state", f -> true ) );
		assertThrows( IllegalArgumentException.class, () -> rules.resources( " ", f -> true ) );
		assertThrows( IllegalArgumentException.class, () -> rules.resources( "bad", f -> true, "" ) );
		assertEquals( Set.of(), flow.meta().tags() );
	}

	/** Proves UNKNOWN conflicts with even an explicitly audited empty set. */
	@Test
	void unknownDiffersFromExplicitlyEmptyAndConflictsWithIt() {
		ResourceRequirements unknown = new ResourceRules().resolve( null );
		ResourceRequirements empty = requirements();
		assertTrue( unknown.unknown() );
		assertTrue( unknown.exclusive() );
		assertFalse( empty.unknown() );
		assertFalse( empty.exclusive() );
		ResourceReservations scope = ResourceReservations.shared();
		Request first = scope.register( scope.capacity( 1 ), empty, () -> {
		} );
		Request second = scope.register( scope.capacity( 1 ), unknown, () -> {
		} );
		Request third = scope.register( scope.capacity( 1 ), empty, () -> {
		} );
		try {
			try( Grant held = first.tryAcquire() ) {
				assertNotNull( held );
				assertNull( second.tryAcquire() );
			}
			try( Grant held = second.tryAcquire() ) {
				assertNotNull( held );
				assertNull( third.tryAcquire() );
			}
			try( Grant held = third.tryAcquire() ) {
				assertNotNull( held );
			}
		}
		finally {
			first.cancel();
			second.cancel();
			third.cancel();
		}
	}

	/**
	 * A blocked whole-set request must not retain a partial set or execution slot.
	 */
	@Test
	void blockedWholeSetHoldsNeitherFreeKeyNorExecutionCapacity() {
		ResourceReservations scope = ResourceReservations.shared();
		var capacity = scope.capacity( 1 );
		Request b = scope.register( scope.capacity( 1 ), requirements( "B" ), () -> {
		} );
		Request both = scope.register( capacity, requirements( "A", new String( "B" ) ), () -> {
		} );
		Request a = scope.register( capacity, requirements( "A" ), () -> {
		} );
		try {
			try( Grant heldB = b.tryAcquire() ) {
				assertNotNull( heldB );
				assertNull( both.tryAcquire() );
				try( Grant freeA = a.tryAcquire() ) {
					assertNotNull( freeA, "blocked set held neither A nor its owner's only slot" );
					assertNull( both.tryAcquire() );
				}
			}
			try( Grant complete = both.tryAcquire() ) {
				assertNotNull( complete );
			}
		}
		finally {
			b.cancel();
			both.cancel();
			a.cancel();
		}
	}

	/**
	 * Cancellation withdraws pending requests without releasing active ownership.
	 */
	@Test
	void executionCapacityAndUnusedCancellationDoNotLeakKeys() {
		ResourceReservations scope = ResourceReservations.shared();
		var capacity = scope.capacity( 1 );
		Request first = scope.register( capacity, requirements(), () -> {
		} );
		Request second = scope.register( capacity, requirements( "slot-key" ), () -> {
		} );
		Request other = scope.register( scope.capacity( 1 ), requirements( "slot-key" ), () -> {
		} );
		try {
			try( Grant held = first.tryAcquire() ) {
				assertNotNull( held );
				assertNull( second.tryAcquire() );
				try( Grant disjointOwner = other.tryAcquire() ) {
					assertNotNull( disjointOwner, "capacity-blocked request must not reserve its key" );
				}
				first.cancel();
				assertNull( second.tryAcquire(), "cancelling a granted request cannot release live use" );
			}
			second.cancel();
			assertNull( second.tryAcquire() );
		}
		finally {
			first.cancel();
			second.cancel();
			other.cancel();
		}
	}

	/**
	 * Checks idempotent release and callbacks that can re-enter the shared scope.
	 *
	 * @throws Exception If the cross-thread reservation probe fails
	 */
	@Test
	void releaseNotifiesOutsideReservationLockAndOnlyOnce() throws Exception {
		ResourceReservations scope = ResourceReservations.shared();
		AtomicInteger notified = new AtomicInteger();
		Request first = scope.register( scope.capacity( 1 ), requirements( "notify" ), () -> {
		} );
		Request second = scope.register( scope.capacity( 1 ), requirements( "notify" ), () -> {
			FutureTask<Boolean> probe = new FutureTask<>( () -> {
				Request nested = scope.register( scope.capacity( 1 ), requirements(), () -> {
				} );
				nested.cancel();
				return true;
			} );
			Thread thread = new Thread( probe, "reservation-lock-probe" );
			thread.start();
			try {
				assertTrue( probe.get( 5, TimeUnit.SECONDS ) );
				notified.incrementAndGet();
			}
			catch( Exception failure ) {
				throw new AssertionError( failure );
			}
		} );
		try( Grant held = first.tryAcquire() ) {
			assertNotNull( held );
			assertNull( second.tryAcquire() );
			held.close();
			held.close();
			assertEquals( 1, notified.get() );
		}
		finally {
			first.cancel();
			second.cancel();
		}
	}

	private static ResourceRequirements requirements( String... keys ) {
		return new ResourceRules().resources( "fixture audit", f -> true, keys ).resolve( null );
	}
}
