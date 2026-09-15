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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
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
		FutureTask<Boolean> probe = new FutureTask<>( () -> {
			synchronized( run.history() ) {
				try( Grant granted = waiting.tryAcquire() ) {
					return granted != null;
				}
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
