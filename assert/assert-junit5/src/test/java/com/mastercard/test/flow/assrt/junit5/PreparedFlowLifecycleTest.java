package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Preparation-time behaviour of the prepared adapter in a real Launcher run.
 */
@SuppressWarnings("static-method")
class PreparedFlowLifecycleTest {

	/** Verifies that preparation preserves the configured replay snapshot. */
	@Test
	void preparationCopiesExistingRegistrationsWithoutReloadingReplay() {
		assertEquals( List.of(),
				FlowExecutionTest.execute( ReplaySnapshotFactory.class, false ).failures );
	}

	/** Changes the ambient replay option after configuring the runner. */
	@FlowTest
	static class ReplaySnapshotFactory {
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
		FlowExecutionTest.Run run = FlowExecutionTest.execute( DuplicateFactory.class, false );
		assertEquals( 0, DuplicateFactory.bodies );
		assertEquals( List.of(), run.results );
		assertTrue( run.failures.stream().anyMatch(
				f -> causes( f ).contains( "Duplicate prepared Flow identity: success []" ) ),
				run.failures::toString );
	}

	private static String causes( Throwable failure ) {
		return failure == null ? "" : failure.toString() + "\n" + causes( failure.getCause() );
	}

	/** Supplies duplicate flow identities to exercise preparation rejection. */
	@FlowTest
	static class DuplicateFactory {
		static int bodies;

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
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return Stream.of( flows );
			}
		};
	}
}
