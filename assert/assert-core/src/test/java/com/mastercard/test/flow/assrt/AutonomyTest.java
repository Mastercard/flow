package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.TestModel.Actors;

/**
 * Illustrates the effects of
 * {@link AbstractFlocessor#autonomous(com.mastercard.test.flow.Actor...)}
 */
@SuppressWarnings("static-method")
class AutonomyTest {

	/**
	 * Builds a flocessor then asserts on its results
	 *
	 * @param state  Whether the SUT is statefull or stateless
	 * @param auton  The autonomous actor, or <code>null</code> if there are none
	 * @param expect Expected test results
	 */
	private static void test( State state, Actor auton, String... expect ) {
		TestFlocessor tf = new TestFlocessor( "baseline", TestModel.asynchronousTransfer() )
				.system( state, Actors.B, Actors.C )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );
		if( auton != null ) {
			tf.autonomous( auton );
		}

		tf.execute();

		assertEquals( copypasta( expect ),
				copypasta( "events", tf.events(), "results", tf.results() ) );
	}

	/**
	 * One of the flows happens entirely within the stateless system
	 */
	@Test
	void stateless() {
		test( State.LESS, null,
				"events",
				"COMPARE first []",
				"com.mastercard.test.flow.assrt.TestModel.asynchronousTransfer(TestModel.java:_) A->B [] response",
				" | OK, but not right now | OK, but not right now |",
				"",
				"SKIP No interactions with system [B,C]",
				"COMPARE third []",
				"com.mastercard.test.flow.assrt.TestModel.asynchronousTransfer(TestModel.java:_) A->C [] response",
				" | Yep! Is 'this' it? | Yep! Is 'this' it? |",
				"results",
				"first [] SUCCESS",
				// skipped due to interactions being internal to the SUT
				"second [] SKIP",
				// processed as the system is stateless so results of prerequisites can be
				// ignored
				"third [] SUCCESS" );
	}

	/**
	 * One of the flows happens entirely within the stateful system
	 */
	@Test
	void stateful() {
		test( State.FUL, null,
				"events",
				"COMPARE first []",
				"com.mastercard.test.flow.assrt.TestModel.asynchronousTransfer(TestModel.java:_) A->B [] response",
				" | OK, but not right now | OK, but not right now |",
				"",
				"SKIP No interactions with system [B,C]",
				"SKIP Missing dependency",
				"results",
				"first [] SUCCESS",
				// skipped due to interactions being internal to the SUT
				"second [] SKIP",
				// skipped as prerequisite 'second' was skipped - we can't rely on the state
				// that we expected it to leave behind
				"third [] SKIP" );
	}

	/**
	 * One of the flows happens entirely within the stateful system, but we've set
	 * the expectation that one of the actors in the system is capable of taking
	 * autonomous actions
	 */
	@Test
	void autonomous() {
		test( State.FUL, Actors.B,
				"events",
				"COMPARE first []",
				"com.mastercard.test.flow.assrt.TestModel.asynchronousTransfer(TestModel.java:_) A->B [] response",
				" | OK, but not right now | OK, but not right now |",
				"",
				"SKIP No interactions with system [B,C], but autonomous actor 'B' is assumed to be doing something",
				"COMPARE third []",
				"com.mastercard.test.flow.assrt.TestModel.asynchronousTransfer(TestModel.java:_) A->C [] response",
				" | Yep! Is 'this' it? | Yep! Is 'this' it? |",
				"results",
				"first [] SUCCESS",
				// skipped due to interactions being internal to the SUT
				"second [] NOT_OBSERVED",
				// Processed! The test is able to assume that autonomous actor B is doing what
				// we expect
				"third [] SUCCESS" );
	}

}
