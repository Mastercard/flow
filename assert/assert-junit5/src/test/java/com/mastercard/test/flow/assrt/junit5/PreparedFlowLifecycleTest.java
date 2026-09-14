package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.util.Option.Temporary;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;

/** Description ownership and actual provider-free serial lifecycle. */
@SuppressWarnings("static-method")
class PreparedFlowLifecycleTest {
	/**
	 * Verifies that the class-store owner exposes the automatic cleanup contract.
	 */
	@Test
	void classStoreOwnerSupportsBothJupiterCleanupContracts() {
		assertTrue( AutoCloseable.class.isAssignableFrom( FlowExecution.class ) );
	}

	/**
	 * Verifies that fixture cleanup follows all native bodies and is idempotent.
	 */
	@Test
	void returnedStreamClosesFixtureOnlyAfterNativeBodiesDrain() {
		ClosingFactory.events.clear();
		assertEquals( List.of(), launch( ClosingFactory.class ) );
		assertEquals( List.of( "body", "body", "close" ), ClosingFactory.events );
		ClosingFactory.handle.close();
		assertEquals( List.of( "body", "body", "close" ), ClosingFactory.events );
	}

	/** Records body execution and fixture closure for a two-flow serial run. */
	@FlowTest
	static class ClosingFactory {
		/** Ordered body and cleanup events. */
		static final List<String> events = new ArrayList<>();
		/** Execution owner retained to verify repeated cleanup. */
		static FlowExecution handle;

		/**
		 * Creates serial tests whose fixture must remain open until both bodies finish.
		 *
		 * @param execution Injected owner of the serial run
		 * @return Test descriptions with a fixture-close event callback
		 */
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			handle = execution;
			return execution.flocessor( "fixture lifetime", model(
					new Mdl().flows().limit( 2 ).toArray( Flow[]::new ) ) )
					.system( State.LESS, Actrs.BEN ).behaviour( a -> {
						assertTrue( !events.contains( "close" ), "fixture must still be open" );
						events.add( "body" );
						a.actual().response( a.expected().response().content() );
					} ).tests().onClose( () -> events.add( "close" ) );
		}
	}

	/** Verifies that preparation preserves the configured replay snapshot. */
	@Test
	void preparationCopiesExistingRegistrationsWithoutReloadingReplay() {
		assertEquals( List.of(), launch( ReplaySnapshotFactory.class ) );
	}

	/** Changes the ambient replay option after configuring the serial runner. */
	@FlowTest
	static class ReplaySnapshotFactory {
		/**
		 * Prepares descriptions without reloading replay from a changed option.
		 *
		 * @param execution Injected owner of the serial run
		 * @return Descriptions prepared from the original configuration
		 */
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			PreparedFlocessor runner = execution.flocessor( "snapshot", new Mdl() )
					.system( State.LESS, Actrs.BEN )
					.behaviour( a -> a.actual().response( a.expected().response().content() ) );
			// Changes to the ambient replay option after configuration must not cause
			// a second replay object/read while copying the configured run.
			try( Temporary replay = AssertionOptions.REPLAY.temporarily( "nonexistent-flow07-replay" ) ) {
				return runner.tests();
			}
		}
	}

	/** Verifies that duplicate identities are rejected before any system work. */
	@Test
	void ambiguousPreparedIdsFailWithoutRenamingOrSutWork() {
		DuplicateFactory.bodies = 0;
		List<Throwable> failures = launch( DuplicateFactory.class );
		assertEquals( 0, DuplicateFactory.bodies );
		assertTrue(
				failures.stream().anyMatch(
						f -> causes( f ).contains( "Duplicate prepared Flow identity: success []" ) ),
				failures.toString() );
	}

	/** Supplies duplicate flow identities to exercise preparation rejection. */
	@FlowTest
	static class DuplicateFactory {
		/** Number of bodies executed despite the ambiguous identities. */
		static int bodies;

		/**
		 * Attempts to prepare a serial run containing duplicate flow identities.
		 *
		 * @param execution Injected owner of the serial run
		 * @return Descriptions if preparation unexpectedly accepts the duplicates
		 */
		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			return execution.flocessor( "ambiguous", model(
					new Mdl().flows().findFirst().orElseThrow(),
					new Mdl().flows().findFirst().orElseThrow() ) )
					.system( State.LESS, Actrs.BEN ).behaviour( a -> {
						bodies++;
						a.actual().response( a.expected().response().content() );
					} ).tests();
		}
	}

	/**
	 * Creates a fixture model that returns exactly the supplied flows, ignoring
	 * tags.
	 *
	 * @param flows Flows exposed by the model
	 * @return Model backed by the supplied flows
	 */
	static Model model( Flow... flows ) {
		return new Mdl() {
			/** {@inheritDoc} */
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return Stream.of( flows );
			}
		};
	}

	/** Verifies that class cleanup detects abandoned native serial consumption. */
	@Test
	void abandonedNativeConsumptionFailsThroughTheClassBackstop() {
		PureFactory.bodies = 0;
		List<Throwable> failures = launch( AbandonedFactory.class );
		assertEquals( 0, PureFactory.bodies );
		assertTrue(
				failures.stream()
						.anyMatch( f -> causes( f ).contains( "Incomplete Flow serial consumption" ) ),
				failures.toString() );
	}

	private static String causes( Throwable failure ) {
		return failure == null ? "" : failure.toString() + "\n" + causes( failure.getCause() );
	}

	/** Provides descriptions that an extension discards before native execution. */
	@ExtendWith(AbandonConsumption.class)
	@FlowTest
	static class AbandonedFactory {
		// Consumer extension drops the live stream without consuming or closing it.
		/**
		 * Creates the descriptions that the consumer extension will discard.
		 *
		 * @param execution Injected owner of the serial run
		 * @return Unexecuted descriptions from the pure-description fixture
		 */
		@TestFactory
		List<DynamicNode> flows( FlowExecution execution ) {
			return new PureFactory().flows( execution );
		}
	}

	/** Discards factory results to simulate abandoned native consumption. */
	static class AbandonConsumption implements InvocationInterceptor {
		/** {@inheritDoc} */
		@Override
		@SuppressWarnings("unchecked")
		public <T> T interceptTestFactoryMethod( Invocation<T> invocation,
				ReflectiveInvocationContext<Method> method, ExtensionContext context ) throws Throwable {
			invocation.proceed();
			return (T) Stream.empty();
		}
	}

	/**
	 * Verifies that retained descriptions release the completed execution graph.
	 *
	 * @throws Exception If reflective inspection of the retained graph fails
	 */
	@Test
	void retainedDescriptionDetachesTheHeavyExecutionGraph() throws Exception {
		PureFactory.bodies = 0;
		assertEquals( List.of(), launch( PureFactory.class ) );
		assertDetached( PureFactory.saved.getExecutable(),
				java.util.Collections.newSetFromMap( new java.util.IdentityHashMap<>() ) );
		assertThrows( IllegalStateException.class, () -> PureFactory.saved.getExecutable().execute() );
		assertEquals( 9, PureFactory.bodies );
	}

	private static void assertDetached( Object value, Set<Object> visited ) throws Exception {
		if( value == null || !visited.add( value ) ) {
			return;
		}
		assertTrue( !(value instanceof com.mastercard.test.flow.assrt.AbstractFlocessor)
				&& !(value instanceof Flow) && !(value instanceof Model),
				() -> "Retained executable owns heavy graph: " + value.getClass().getName() );
		if( value instanceof Iterable<?> ) {
			for( Object child : (Iterable<?>) value ) {
				assertDetached( child, visited );
			}
		}
		if( value.getClass().getName().startsWith( "com.mastercard.test.flow.assrt.junit5." ) ) {
			for( java.lang.reflect.Field field : value.getClass().getDeclaredFields() ) {
				if( !java.lang.reflect.Modifier.isStatic( field.getModifiers() ) ) {
					field.setAccessible( true );
					assertDetached( field.get( value ), visited );
				}
			}
		}
	}

	/**
	 * Verifies that description creation is inert and execution requires ownership.
	 */
	@Test
	void descriptionsArePureAndOnlyTheOwnedNativeRunCanProcessThem() {
		PureFactory.bodies = 0;
		List<Throwable> failures = launch( PureFactory.class );
		assertEquals( List.of(), failures );
		assertEquals( 9, PureFactory.bodies );
		assertThrows( IllegalStateException.class, () -> PureFactory.saved.getExecutable().execute() );
	}

	/**
	 * Executes a fixture through Jupiter with native parallel execution disabled.
	 *
	 * @param factory Fixture class to execute
	 * @return Failures reported by the launcher
	 */
	static List<Throwable> launch( Class<?> factory ) {
		List<Throwable> failures = new ArrayList<>();
		FlowExecutionTest.execute( factory, "false", new TestExecutionListener() {
			/** {@inheritDoc} */
			@Override
			public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
				result.getThrowable().ifPresent( failures::add );
			}
		} );
		return failures;
	}

	/** Retains an inert description for ownership and graph-detachment checks. */
	@FlowTest
	static class PureFactory {
		/** Number of flow bodies executed by the owned native run. */
		static int bodies;
		/** First description retained beyond completion of the serial run. */
		static DynamicTest saved;

		/**
		 * Materializes descriptions while checking that they cannot execute early.
		 *
		 * @param execution Injected owner of the serial run
		 * @return Descriptions for subsequent owned native execution
		 */
		@TestFactory
		List<DynamicNode> flows( FlowExecution execution ) {
			PreparedFlocessor runner = execution.flocessor( "pure descriptions", new Mdl() )
					.system( State.LESS, Actrs.BEN ).behaviour( a -> {
						bodies++;
						a.actual().response( a.expected().response().content() );
					} );
			List<DynamicNode> nodes;
			try( Stream<DynamicNode> descriptions = runner.tests() ) {
				nodes = descriptions.peek( n -> assertTrue( n.toString().contains( n.getDisplayName() ) ) )
						.toList();
			}
			assertEquals( 0, bodies );
			assertNull( runner.report() );
			saved = (DynamicTest) nodes.get( 0 );
			assertThrows( IllegalStateException.class, () -> saved.getExecutable().execute() );
			assertEquals( 0, bodies );
			return nodes;
		}
	}
}
