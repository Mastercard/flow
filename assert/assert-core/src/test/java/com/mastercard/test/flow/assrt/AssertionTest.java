
package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static com.mastercard.test.flow.assrt.TestModel.Actors.C;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Exercises {@link Assertion} functions that aren't complicated enough to get
 * their own dedicated test
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

	/**
	 * Adding assertions on the grandchild of the entrypoint
	 */
	@Test
	void assertGrandchild() {
		TestFlocessor tf = new TestFlocessor( "assertGrandchild", TestModel.abc() )
				.system( State.LESS, B, C )
				.behaviour( asrt -> {
					// everything goes well at the entrypoint
					asrt.actual()
							.request( asrt.expected().request().content() )
							.response( asrt.expected().response().content() );

					// and we've got a window into the system internals, so we can add intra-system
					// assertions too
					asrt.assertDownstream()
							.filter( a -> a.expected().responder() == C )
							.forEach( a -> a.actual().request( a.expected().request().content() ) );
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] request",
				" | B request to C | B request to C |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * We can add a programmatically-defined filter to control which flows are
	 * exercised, and rejection by that filter isn't necessarily silent
	 */
	@Test
	void exercising() {
		List<String> rejectionLog = new ArrayList<>();

		TestFlocessor tf = new TestFlocessor( "exercising", TestModel.abcWithDependency() )
				.system( State.LESS, B )
				.exercising( f -> "dependency".equals( f.meta().description() ), rejectionLog::add )
				.behaviour( asrt -> {
					asrt.actual().request( asrt.expected().request().content() );
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE dependency []",
				"com.mastercard.test.flow.assrt.TestModel.abcWithDependency(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |" ),
				copypasta( tf.events() ),
				"The flow passed by the filter is exercised as normal" );

		assertEquals(
				"[Flow 'dependent []' rejected by .exercising() filter]",
				rejectionLog.toString(),
				"The rejected flow is logged" );
	}

	/**
	 * The programmatically-defined filter can be suppressed by system properties
	 */
	@Test
	void exercisingSupression() {

		try( Temporary suppression = AssertionOptions.SUPPRESS_FILTER.temporarily( "true" ) ) {
			List<String> rejectionLog = new ArrayList<>();

			TestFlocessor tf = new TestFlocessor( "exercisingSupression", TestModel.abcWithDependency() )
					.system( State.LESS, B )
					.exercising( f -> "dependency".equals( f.meta().description() ), rejectionLog::add )
					.behaviour( asrt -> {
						asrt.actual().request( asrt.expected().request().content() );
					} );

			tf.execute();

			assertEquals( copypasta(
					"COMPARE dependency []",
					"com.mastercard.test.flow.assrt.TestModel.abcWithDependency(TestModel.java:_) A->B [] request",
					" | A request to B | A request to B |",
					"",
					"COMPARE dependent []",
					"com.mastercard.test.flow.assrt.TestModel.abcWithDependency(TestModel.java:_) A->B [] request",
					" | A request to B | A request to B |" ),
					copypasta( tf.events() ),
					"Both flows exercised" );

			assertEquals(
					"[Rejection of flow 'dependent []' by .exercising() filter suppressed by system property mctf.suppress.filter=true]",
					rejectionLog.toString(),
					"The rejection suppression is logged" );
		}
	}
}
