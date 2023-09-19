package com.mastercard.test.flow.assrt;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.Reporting.ALWAYS;
import static com.mastercard.test.flow.assrt.Reporting.FAILURES;
import static com.mastercard.test.flow.assrt.Reporting.NEVER;
import static com.mastercard.test.flow.assrt.Reporting.QUIETLY;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;

/**
 * Exercises {@link Reporting} values
 */
@SuppressWarnings("static-method")
class ReportingTest {

	/**
	 * Identifies those modes that generate a report
	 */
	@Test
	void writing() {
		test( Reporting::writing, ALWAYS, FAILURES, QUIETLY );
	}

	/**
	 * Identifies those modes that show a report with no failures
	 */
	@Test
	void noFailOpen() {
		test( r -> r.shouldOpen( false ), ALWAYS );
	}

	/**
	 * Identifies those modes that show a report with failures
	 */
	@Test
	void failOpen() {
		test( r -> r.shouldOpen( true ), ALWAYS, FAILURES );
	}

	private static void test( Function<Reporting, Boolean> test, Reporting... active ) {
		EnumSet<Reporting> as = EnumSet.noneOf( Reporting.class );
		Collections.addAll( as, active );
		as.forEach( r -> Assertions
				.assertEquals( true, test.apply( r ), "for " + r ) );
		EnumSet.complementOf( as ).forEach( r -> Assertions
				.assertEquals( false, test.apply( r ), "for " + r ) );
	}

	/**
	 * Shows that flows that succeed are tagged as such in the report
	 */
	@Test
	void successTagging() {
		TestFlocessor tf = new TestFlocessor( "successTagging", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );
		tf.execute();

		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		assertTrue( ie.tags.contains( "PASS" ), ie.tags.toString() );
		FlowData fd = r.detail( ie );
		assertTrue( fd.tags.contains( "PASS" ), fd.tags.toString() );
	}

	/**
	 * Shows that when no report is being generated we stop asserting at the first
	 * failure
	 */
	@Test
	void unreportedFailure() {
		TestFlocessor tf = new TestFlocessor( "unreportedFailure", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( NEVER )
				.behaviour( assrt -> {
					assrt.actual()
							.request( "unexpected req content!".getBytes( UTF_8 ) )
							.response( "unexpected res content!".getBytes( UTF_8 ) );
				} );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] request",
				" | A request to B | unexpected req content! |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * Shows that flows that fail are tagged as such in the report, and that
	 * captured data is populated into the report
	 *
	 * @throws Exception if the json dump fails
	 */
	@Test
	void failureReporting() throws Exception {
		TestFlocessor tf = new TestFlocessor( "failureReporting", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					assrt.actual()
							.request( "unexpected req content!".getBytes( UTF_8 ) )
							.response( "unexpected res content!".getBytes( UTF_8 ) );
				} );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] request",
				" | A request to B | unexpected req content! |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | unexpected res content! |" ),
				copypasta( tf.events() ) );

		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		assertTrue( ie.tags.contains( "FAIL" ), ie.tags.toString() );
		FlowData fd = r.detail( ie );
		assertTrue( fd.tags.contains( "FAIL" ), fd.tags.toString() );

		assertEquals( copypasta(
				"{",
				"  'requester' : 'A',",
				"  'responder' : 'B',",
				"  'tags' : [ ],",
				"  'request' : {",
				"    'full' : {",
				"      'expect' : 'A request to B',",
				"      'expectBytes' : 'QSByZXF1ZXN0IHRvIEI=',",
				"      'actual' : 'unexpected req content!',",
				"      'actualBytes' : 'dW5leHBlY3RlZCByZXEgY29udGVudCE='",
				"    },",
				"    'asserted' : {",
				"      'expect' : 'A request to B',",
				"      'actual' : 'unexpected req content!'",
				"    }",
				"  },",
				"  'response' : {",
				"    'full' : {",
				"      'expect' : 'B response to A',",
				"      'expectBytes' : 'QiByZXNwb25zZSB0byBB',",
				"      'actual' : 'unexpected res content!',",
				"      'actualBytes' : 'dW5leHBlY3RlZCByZXMgY29udGVudCE='",
				"    },",
				"    'asserted' : {",
				"      'expect' : 'B response to A',",
				"      'actual' : 'unexpected res content!'",
				"    }",
				"  },",
				"  'children' : [ {",
				"    'requester' : 'B',",
				"    'responder' : 'C',",
				"    'tags' : [ ],",
				"    'request' : {",
				"      'full' : {",
				"        'expect' : 'B request to C',",
				"        'expectBytes' : 'QiByZXF1ZXN0IHRvIEM=',",
				"        'actual' : null,",
				"        'actualBytes' : null",
				"      },",
				"      'asserted' : {",
				"        'expect' : null,",
				"        'actual' : null",
				"      }",
				"    },",
				"    'response' : {",
				"      'full' : {",
				"        'expect' : 'C response to B',",
				"        'expectBytes' : 'QyByZXNwb25zZSB0byBC',",
				"        'actual' : null,",
				"        'actualBytes' : null",
				"      },",
				"      'asserted' : {",
				"        'expect' : null,",
				"        'actual' : null",
				"      }",
				"    },",
				"    'children' : [ ]",
				"  } ]",
				"}" ),
				copypasta( new ObjectMapper()
						.enable( INDENT_OUTPUT )
						.writeValueAsString( fd.root ) ) );
	}

	/**
	 * Shows that flows that explode are tagged as such in the report and that the
	 * failures are logged
	 */
	@Test
	void errorTagging() {
		TestFlocessor tf = new TestFlocessor( "errorTagging", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					throw new RuntimeException( "kaboom" );
				} );
		tf.execute();

		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		assertTrue( ie.tags.contains( "ERROR" ), ie.tags.toString() );
		FlowData fd = r.detail( ie );
		assertTrue( fd.tags.contains( "ERROR" ), fd.tags.toString() );

		assertEquals( "Encountered error: java.lang.RuntimeException: kaboom",
				fd.logs.get( 0 ).message.split( "\n" )[0].trim(),
				"First line of logged error"
		// the stacktrace is logged, but we'll not assert on that
		);
	}

	/**
	 * Shows that flows where we are supplied with unparseable bytes are tagged and
	 * logged correctly in the report
	 */
	@Test
	void parseFailureTagging() {
		TestFlocessor tf = new TestFlocessor( "parseFailureTagging", TestModel.abcWithParseFailures() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					assrt.actual().response( new byte[] { 0 } );
				} );
		tf.execute();

		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		assertTrue( ie.tags.contains( "ERROR" ), ie.tags.toString() );
		FlowData fd = r.detail( ie );
		assertTrue( fd.tags.contains( "ERROR" ), fd.tags.toString() );

		assertEquals(
				"java.lang.IllegalArgumentException: Failed to parse response message from actual data",
				fd.logs.get( 0 ).message.split( "\n" )[0].trim(),
				"First line of logged error"
		// the stacktrace is logged, but we'll not assert on that
		);
	}

	/**
	 * Shows that flows that are skipped are tagged as such in the report
	 */
	@Test
	void skipTagging() {
		TestFlocessor tf = new TestFlocessor( "skipTagging", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					// no assertions made
				} );
		tf.execute();

		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
		FlowData fd = r.detail( ie );
		assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
	}

	/**
	 * Shows that the {@link Actor}s under test are recorded to the report
	 */
	@Test
	void exercised() {
		TestFlocessor tf = new TestFlocessor( "exercised", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					// no assertions made
				} );
		tf.execute();

		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		FlowData fd = r.detail( ie );
		assertEquals( "[B]", fd.exercised.toString() );
	}
}
