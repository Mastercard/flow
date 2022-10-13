package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.util.Option.Temporary;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercises flow-filtering aspects of the assertion mechanism
 */
@SuppressWarnings("static-method")
class FilterTest {

	/**
	 * Test driver
	 *
	 * @param cfg      How to configure the flocessor
	 * @param expected The expected processing events
	 */
	private static void test( Consumer<TestFlocessor> cfg, String... expected ) {
		TestFlocessor tf = new TestFlocessor( "filtering", TestModel.triple() )
				.system( State.LESS, B )
				.behaviour( asrt -> {
					asrt.actual().request( asrt.expected().request().content() );
				} );
		cfg.accept( tf );

		tf.execute();

		assertEquals(
				copypasta( expected ),
				copypasta( tf.events() ) );
	}

	/**
	 * No filter config applied, so all three flows are exercised. This test
	 * provides a comparison point for other tests.
	 */
	@Test
	void base() {
		test( tf -> {
			// no-op
		},
				"COMPARE first [a, b, c]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |",
				"",
				"COMPARE second [b, c, d]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |",
				"",
				"COMPARE third [c, d, e]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |" );
	}

	/**
	 * The tag/index filter can be configured via code. In this case we're
	 * restricting execution to the first and second flows based on a tag.
	 */
	@Test
	void filtering() {
		test( tf -> tf.filtering( fc -> fc.includedTags( Tags.tags( "b" ) ) ),
				"COMPARE first [a, b, c]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |",
				"",
				"COMPARE second [b, c, d]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |" );
	}

	/**
	 * We can add a programmatically-defined filter to control which flows are
	 * exercised (based on stuff <i>other</i> than tags), and rejection by that
	 * filter isn't necessarily silent. In this case we're restricting execution to
	 * the first and third flows based on the description.
	 */
	@Test
	void exercising() {
		List<String> rejectionLog = new ArrayList<>();

		test( tf -> tf.exercising(
				f -> f.meta().description().contains( "t" ),
				rejectionLog::add ),
				"COMPARE first [a, b, c]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |",
				"",
				"COMPARE third [c, d, e]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |" );

		assertEquals(
				"[Flow 'second [b, c, d]' rejected by .exercising() filter]",
				rejectionLog.toString(),
				"The rejected flow is logged" );
	}

	/**
	 * The tag/index and programmatic filters can be combined. In this case the
	 * first flow is the only thing that makes it through.
	 */
	@Test
	void combined() {
		List<String> rejectionLog = new ArrayList<>();
		test( tf -> tf
				.filtering( fc -> fc.includedTags( Tags.tags( "b" ) ) )
				.exercising(
						f -> f.meta().description().contains( "t" ),
						rejectionLog::add ),
				"COMPARE first [a, b, c]",
				"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |" );
		assertEquals(
				"[Flow 'second [b, c, d]' rejected by .exercising() filter]",
				rejectionLog.toString(),
				"The rejected flow is logged" );
	}

	/**
	 * Filter behaviour can be suppressed via a system property. Now we're back to
	 * all three flows being exercised
	 */
	@Test
	void supression() {

		try( Temporary suppression = AssertionOptions.SUPPRESS_FILTER.temporarily( "true" ) ) {
			List<String> rejectionLog = new ArrayList<>();

			test( tf -> tf
					.filtering( fc -> fc.includedTags( Tags.tags( "b" ) ) )
					.exercising(
							f -> f.meta().description().contains( "t" ),
							rejectionLog::add ),
					"COMPARE first [a, b, c]",
					"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
					" | A request to B | A request to B |",
					"",
					"COMPARE second [b, c, d]",
					"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
					" | A request to B | A request to B |",
					"",
					"COMPARE third [c, d, e]",
					"com.mastercard.test.flow.assrt.TestModel.triple(TestModel.java:_) A->B [] request",
					" | A request to B | A request to B |" );

			assertEquals(
					"[]",
					rejectionLog.toString(),
					"Rejection log is empty" );
		}
	}
}
