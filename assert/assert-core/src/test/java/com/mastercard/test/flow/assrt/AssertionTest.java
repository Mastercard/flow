package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static com.mastercard.test.flow.assrt.TestModel.Actors.C;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;

/**
 * Exercises {@link Assertion}
 */
@SuppressWarnings("static-method")
class AssertionTest {

	/**
	 * Happy path of asserting downstream requests
	 */
	@Test
	void assertConsequests() {

		TestFlocessor tf = new TestFlocessor( "assertConsequests", TestModel.abc() )
				.system( State.LESS, B )
				.behaviour( asrt -> {
					Consequests csq = new Consequests()
							.capture( C, "B request to C".getBytes( UTF_8 ) );

					assertSame( asrt, asrt.assertConsequests( csq ) );
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] request",
				" | B request to C | B request to C |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * Captured requests do not match expected structure
	 */
	@Test
	void missingConsequests() {

		TestFlocessor tf = new TestFlocessor( "missingConsequests", TestModel.abc() )
				.system( State.LESS, B )
				.behaviour( asrt -> {
					Consequests csq = new Consequests();
					assertSame( asrt, asrt.assertConsequests( csq ) );
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE Consequent request to C mismatch: expected 1 got 0",
				" | B request to C |   |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * Captured requests do not match expected structure
	 */
	@Test
	void extraConsequests() {

		TestFlocessor tf = new TestFlocessor( "missingConsequests", TestModel.abc() )
				.system( State.LESS, B )
				.behaviour( asrt -> {
					Consequests csq = new Consequests()
							.capture( C, "expected".getBytes( UTF_8 ) )
							.capture( C, "extra".getBytes( UTF_8 ) );
					assertSame( asrt, asrt.assertConsequests( csq ) );
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE Consequent request to C mismatch: expected 1 got 2",
				" | B request to C | expected |",
				" |                |    ----- |",
				" |                |    extra |" ),
				copypasta( tf.events() ) );
	}
}
