package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.History.Result;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;

/**
 * Exercises {@link History}
 */
@SuppressWarnings("static-method")
class HistoryTest {

	private enum Actors implements Actor {
		AVA, BEN, CHE,
	}

	private static final Flow dependency = Creator.build( flow -> flow
			.meta( data -> data
					.description( "dnc" ) )
			.call( a -> a.from( Actors.AVA ).to( Actors.BEN )
					.request( null ).response( null ) ) );

	private static final Flow dependent = Creator.build( flow -> flow
			.meta( data -> data
					.description( "dnt" ) )
			.prerequisite( dependency )
			.call( a -> a.from( Actors.AVA ).to( Actors.BEN )
					.request( null ).response( null ) ) );

	/**
	 * Shows that the assertion failure of a basis will provoke a skip
	 */
	@Test
	void basisFailure() {

		Flow parent = Creator.build( flow -> flow
				.meta( data -> data
						.description( "parent" ) ) );
		Flow child = Deriver.build( parent, flow -> flow
				.meta( data -> data
						.description( "child" ) ) );

		History hst = new History();

		Stream.of( null, Result.PENDING, Result.SUCCESS, Result.SKIP, Result.ERROR )
				.forEach( r -> {
					hst.recordResult( parent, r );
					assertEquals( null, hst.skipReason( child, State.FUL, Collections.emptySet() )
							.orElse( null ),
							"basis " + r );
				} );

		hst.recordResult( parent, Result.UNEXPECTED );

		assertEquals( "Ancestor failed", hst.skipReason( child, State.FUL, Collections.emptySet() )
				.orElse( null ),
				"basis failure" );

		try {
			Options.SUPPRESS_BASIS_CHECK.set( "true" );
			assertEquals( null, hst.skipReason( child, State.FUL, Collections.emptySet() )
					.orElse( null ),
					"supressed basis failure" );
		}
		finally {
			Options.SUPPRESS_BASIS_CHECK.clear();
		}
	}

	/**
	 * The dependency has been processed
	 */
	@Test
	void dependencyPresent() {
		History hst = new History();
		hst.recordResult( dependency, Result.SUCCESS );

		assertEquals( null, hst.skipReason(
				dependent, State.FUL, Collections.singleton( Actors.BEN ) )
				.orElse( null ) );
	}

	/**
	 * The dependency has not been processed
	 */
	@Test
	void dependencyMissing() {
		History hst = new History();

		assertEquals( "Missing dependency", hst.skipReason(
				dependent, State.FUL, Collections.singleton( Actors.BEN ) )
				.orElse( null ) );

	}

	/**
	 * The dependency has failed
	 */
	@Test
	void dependencyFailure() {
		History hst = new History();
		hst.recordResult( dependency, Result.ERROR );
		assertEquals( "Missing dependency", hst.skipReason(
				dependent, State.FUL, Collections.singleton( Actors.BEN ) )
				.orElse( null ) );

		try {
			Options.SUPPRESS_DEPENDENCY_CHECK.set( "true" );
			assertEquals( null, hst.skipReason(
					dependent, State.FUL, Collections.singleton( Actors.BEN ) )
					.orElse( null ),
					"suppressed dependency failure" );
		}
		finally {
			Options.SUPPRESS_DEPENDENCY_CHECK.clear();
		}
	}

	/**
	 * The dependency has failed, but it doesn't intersect with the system under
	 * test so we can proceed
	 */
	@Test
	void nonintersectingDependencyFailure() {
		History hst = new History();
		hst.recordResult( dependency, Result.ERROR );
		assertEquals( null, hst.skipReason(
				dependent, State.FUL, Collections.singleton( Actors.CHE ) )
				.orElse( null ) );
	}

	/**
	 * The dependency has failed, but the system is stateless so we can proceed
	 */
	@Test
	void statelessDependencyFailure() {
		History hst = new History();
		hst.recordResult( dependency, Result.ERROR );
		assertEquals( null, hst.skipReason(
				dependent, State.LESS, Collections.singleton( Actors.BEN ) )
				.orElse( null ) );
	}
}
