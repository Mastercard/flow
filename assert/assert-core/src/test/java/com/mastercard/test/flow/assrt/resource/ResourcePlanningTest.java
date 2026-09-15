package com.mastercard.test.flow.assrt.resource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;

/**
 * Pure public planning/reservation seam, shared by runners and fixture owners.
 */
class ResourcePlanningTest {
	/**
	 * Isolation is not a resource audit, even when another member is classified.
	 */
	@Test
	void wholeChainUnionPreservesUnknownAndExclusiveMembersWithoutSelectingOthers() {
		Flow a = Creator
				.build( f -> f.meta( m -> m.description( "A" ).tags( t -> t.add( "chain:AB" ) ) ) );
		Flow b = Creator
				.build( f -> f.meta( m -> m.description( "B" ).tags( t -> t.add( "chain:AB" ) ) ) );
		Flow outside = Creator
				.build( f -> f.meta( m -> m.description( "outside" ) ).prerequisite( a ) );
		String[] audited = { "AB" };
		ResourceRules rules = new ResourceRules().isolatedChains( "whole-chain audit", audited )
				.isolatedChains( "cleanup audit", "AB", "absent" )
				.isolatedChains( "unselected audit", "absent" )
				.resources( "A state", f -> f == a, "account" )
				.exclusive( "outside reset", f -> f == outside );
		audited[0] = "mutated";
		List<Flow> selected = new ArrayList<>( List.of( a, b, outside ) );
		ChainPlan unknown = rules.chains( selected, selected.stream().map( rules::resolve ).toList() );
		assertSame( unknown.requirements( 0 ), unknown.requirements( 1 ) );
		assertEquals( Set.of( "account" ), unknown.requirements( 0 ).keys() );
		assertEquals( Set.of( "A state" ), unknown.requirements( 0 ).rules() );
		assertEquals( List.of( "whole-chain audit", "cleanup audit" ),
				unknown.requirements( 0 ).isolationRules().stream().toList() );
		assertEquals( Set.of(), unknown.requirements( 2 ).isolationRules() );
		assertEquals( Set.of(), rules.resolve( b ).isolationRules() );
		assertThrows( UnsupportedOperationException.class,
				() -> unknown.requirements( 0 ).isolationRules().clear() );
		assertTrue( unknown.requirements( 0 ).unknown(), "a classified member cannot erase UNKNOWN" );
		assertTrue( unknown.requirements( 0 ).exclusive() );
		assertTrue( rules.resolve( b ).unknown(), "isolation does not classify a member as EMPTY" );
		rules.resources( "B state", f -> f == b, "queue" );
		ChainPlan known = rules.chains( selected, selected.stream().map( rules::resolve ).toList() );
		assertEquals( Set.of( "account", "queue" ), known.requirements( 0 ).keys() );
		assertFalse( known.requirements( 0 ).unknown() );
		assertFalse( known.requirements( 0 ).exclusive(), "unchained exclusive is not component-wide" );
		assertTrue( known.requirements( 2 ).exclusive() );
		rules.exclusive( "B reset", f -> f == b );
		ChainPlan exclusive = rules.chains( selected,
				selected.stream().map( rules::resolve ).toList() );
		assertTrue( exclusive.requirements( 0 ).exclusive() );
		ChainPlan subset = rules.chains( List.of( a ), List.of( rules.resolve( a ) ) );
		assertEquals( -1, subset.next( 0 ) );
		assertEquals( Set.of( "account" ), subset.requirements( 0 ).keys() );
		assertFalse( subset.requirements( 0 ).exclusive(),
				"unselected chain member adds no restriction" );
		rules.isolatedChains( "later audit", "AB" );
		selected.clear();
		assertEquals( Set.of( "whole-chain audit", "cleanup audit" ),
				unknown.requirements( 0 ).isolationRules(),
				"plan does not retain mutable declarations" );
		assertEquals( 1, unknown.next( 0 ), "selected membership is frozen" );
		assertTrue( unknown.requirements( 0 ).unknown(),
				"later member rules cannot change the snapshot" );
		assertEquals( Set.of( "chain:AB" ), a.meta().tags() );
		assertThrows( IllegalArgumentException.class,
				() -> rules.resources( "whole-chain audit", f -> true ) );
		assertThrows( IllegalArgumentException.class, () -> rules.isolatedChains( "A state", "AB" ) );
		assertThrows( IllegalArgumentException.class, () -> rules.isolatedChains( "empty" ) );
		assertThrows( IllegalArgumentException.class, () -> rules.isolatedChains( "blank", " " ) );
	}

	/**
	 * Default exclusion is distinct from member classification and unrelated
	 * audits.
	 */
	@Test
	void defaultChainReservationKeepsKnownEmptyMembersSeparateFromIsolation() {
		Flow a = Creator.build( f -> f.meta( m -> m.description( "A" )
				.tags( t -> t.add( "chain:AB" ) ) ) );
		ResourceRules rules = new ResourceRules().resources( "empty", f -> true )
				.isolatedChains( "absent scenario", "other" );
		ResourceRequirements member = rules.resolve( a );
		ChainPlan plan = rules.chains( List.of( a ), List.of( member ) );
		assertFalse( member.exclusive() );
		assertFalse( plan.requirements( 0 ).unknown() );
		assertTrue( plan.requirements( 0 ).exclusive() );
		assertEquals( Set.of(), plan.requirements( 0 ).keys() );
		assertEquals( Set.of( "empty" ), plan.requirements( 0 ).rules() );
		assertEquals( Set.of(), plan.requirements( 0 ).isolationRules() );
		assertEquals( -1, plan.next( 0 ) );
		ResourceRequirements unknown = new ResourceRules().isolatedChains( "not membership", "AB" )
				.resolve( a );
		assertTrue( unknown.unknown() );
		assertEquals( Set.of(), unknown.rules() );
		assertEquals( Set.of(), unknown.isolationRules() );
	}

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
				try( Grant denied = second.tryAcquire() ) {
					assertNull( denied );
				}
			}
			try( Grant held = second.tryAcquire() ) {
				assertNotNull( held );
				try( Grant denied = third.tryAcquire() ) {
					assertNull( denied );
				}
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
	 * A blocked whole-set request protects free keys without holding them or a
	 * slot.
	 */
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5 })
	void blockedWholeSetHoldsNeitherFreeKeyNorExecutionCapacity( int limit ) {
		ResourceReservations scope = ResourceReservations.shared();
		var capacity = scope.capacity( limit );
		Request b = scope.register( scope.capacity( 1 ), requirements( "B" ), () -> {
		} );
		Request both = scope.register( capacity, requirements( "A", new String( "B" ) ), () -> {
		} );
		Request a = scope.register( capacity, requirements( "A" ), () -> {
		} );
		Request c = scope.register( capacity, requirements( "C" ), () -> {
		} );
		try {
			try( Grant heldB = b.tryAcquire() ) {
				assertNotNull( heldB );
				try( Grant denied = both.tryAcquire() ) {
					assertNull( denied );
				}
				try( Grant youngerA = a.tryAcquire(); Grant disjoint = c.tryAcquire() ) {
					assertNull( youngerA, "older AB protects A even while B is busy" );
					assertNotNull( disjoint, "disjoint C can use the blocked owner's capacity" );
					try( Grant denied = both.tryAcquire() ) {
						assertNull( denied );
					}
				}
			}
			for( int retry = 0; retry < 3; retry++ ) {
				try( Grant younger = a.tryAcquire() ) {
					assertNull( younger, "retry order cannot replace dependency-ready age" );
				}
			}
			try( Grant complete = both.tryAcquire() ) {
				assertNotNull( complete );
			}
			try( Grant after = a.tryAcquire() ) {
				assertNotNull( after );
			}
		}
		finally {
			b.cancel();
			both.cancel();
			a.cancel();
			c.cancel();
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
				try( Grant denied = second.tryAcquire() ) {
					assertNull( denied );
				}
				try( Grant disjointOwner = other.tryAcquire() ) {
					assertNull( disjointOwner, "capacity-blocked older work protects its key" );
				}
				first.cancel();
				try( Grant denied = second.tryAcquire() ) {
					assertNull( denied, "cancelling a granted request cannot release live use" );
				}
				second.cancel();
				try( Grant free = other.tryAcquire() ) {
					assertNotNull( free, "withdrawal proves the older request held no partial key" );
				}
			}
			second.cancel();
			try( Grant denied = second.tryAcquire() ) {
				assertNull( denied );
			}
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
		List<Request> probes = new ArrayList<>();
		Request first = scope.register( scope.capacity( 1 ), requirements( "notify" ), () -> {
		} );
		Request second = scope.register( scope.capacity( 1 ), requirements( "notify" ), () -> {
			FutureTask<Boolean> probe = new FutureTask<>( () -> {
				Request nested = scope.register( scope.capacity( 1 ), requirements(), () -> {
				} );
				probes.add( nested );
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
			try( Grant denied = second.tryAcquire() ) {
				assertNull( denied );
			}
			held.close();
			held.close();
			assertEquals( 1, notified.get() );
		}
		finally {
			first.cancel();
			second.cancel();
			probes.forEach( Request::cancel );
		}
	}

	/**
	 * The exclusive gate exists before its owner has capacity or retries admission.
	 *
	 * @param unknown Whether exclusion is the UNKNOWN fallback
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void readyExclusiveGatesNewerEmptyWorkButPreservesOlderPending( boolean unknown ) {
		ResourceReservations scope = ResourceReservations.shared();
		var capacity = scope.capacity( 1 );
		Request active = scope.register( capacity, requirements(), () -> {
		} );
		Request older = scope.register( scope.capacity( 1 ), requirements( "older" ), () -> {
		} );
		ResourceRequirements reset = unknown ? new ResourceRules().resolve( null )
				: new ResourceRules().exclusive( "reset", f -> true ).resolve( null );
		Request exclusive = scope.register( capacity, reset, () -> {
		} );
		Request empty = scope.register( scope.capacity( 1 ), requirements(), () -> {
		} );
		Request disjoint = scope.register( scope.capacity( 1 ), requirements( "disjoint" ), () -> {
		} );
		try( Grant held = active.tryAcquire(); Grant beforeGate = older.tryAcquire() ) {
			assertNotNull( held );
			assertNotNull( beforeGate, "older pending work precedes even a ready exclusive" );
			try( Grant deniedEmpty = empty.tryAcquire(); Grant deniedKey = disjoint.tryAcquire() ) {
				assertNull( deniedEmpty, "even EMPTY is gated before exclusive's first try" );
				assertNull( deniedKey );
			}
			try( Grant denied = exclusive.tryAcquire() ) {
				assertNull( denied );
			}
			held.close();
			try( Grant denied = exclusive.tryAcquire() ) {
				assertNull( denied, "other active work still has to drain" );
			}
			beforeGate.close();
			try( Grant alone = exclusive.tryAcquire() ) {
				assertNotNull( alone );
				try( Grant deniedEmpty = empty.tryAcquire(); Grant deniedKey = disjoint.tryAcquire() ) {
					assertNull( deniedEmpty );
					assertNull( deniedKey );
				}
			}
			try( Grant resumedEmpty = empty.tryAcquire(); Grant resumedKey = disjoint.tryAcquire() ) {
				assertNotNull( resumedEmpty );
				assertNotNull( resumedKey );
			}
		}
		finally {
			List.of( active, older, exclusive, empty, disjoint ).forEach( Request::cancel );
		}
	}

	/**
	 * Withdrawal itself enables admission; no grant release is available to wake
	 * it.
	 *
	 * @throws Exception If the cross-thread reservation probe fails
	 */
	@Test
	void cancellationNotifiesOutsideReservationLockWithoutAnyRelease() throws Exception {
		ResourceReservations scope = ResourceReservations.shared();
		AtomicInteger notified = new AtomicInteger();
		List<Request> probes = new ArrayList<>();
		Request older = scope.register( scope.capacity( 1 ), requirements( "cancel" ), () -> {
		} );
		Request newer = scope.register( scope.capacity( 1 ), requirements( "cancel" ), () -> {
			FutureTask<Boolean> probe = new FutureTask<>( () -> {
				probes.add( scope.register( scope.capacity( 1 ), requirements(), () -> {
				} ) );
				return true;
			} );
			new Thread( probe, "cancel-lock-probe" ).start();
			try {
				assertTrue( probe.get( 5, TimeUnit.SECONDS ) );
				notified.incrementAndGet();
			}
			catch( Exception failure ) {
				throw new AssertionError( failure );
			}
		} );
		try {
			try( Grant denied = newer.tryAcquire() ) {
				assertNull( denied );
			}
			older.cancel();
			assertEquals( 1, notified.get(), "withdrawal must wake the gated request" );
			older.cancel();
			assertEquals( 1, notified.get(), "duplicate withdrawal has no effect" );
			try( Grant resumed = newer.tryAcquire() ) {
				assertNotNull( resumed );
			}
		}
		finally {
			newer.cancel();
			older.cancel();
			probes.forEach( Request::cancel );
		}
	}

	private static ResourceRequirements requirements( String... keys ) {
		return new ResourceRules().resources( "fixture audit", f -> true, keys ).resolve( null );
	}
}
