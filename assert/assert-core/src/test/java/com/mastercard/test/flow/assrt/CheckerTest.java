package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.mock.TestResidue;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.report.Reader;

/**
 * Exercises {@link Checker}
 */
@SuppressWarnings("static-method")
class CheckerTest {

	/**
	 * Shows that the residue type passed in the constructor is returned by
	 * {@link Checker#residueType()}
	 */
	@Test
	void residueType() {
		assertEquals( TestResidue.class, new TestChecker().residueType() );
	}

	/**
	 * Shows that residues are checked and that the results are recorded for
	 * failure-avoidance purposes
	 */
	@Test
	void unmasked() {
		TestFlocessor tf = new TestFlocessor( "", TestModel.withResidue() )
				.system( State.FUL, B )
				.checkers( new TestChecker() )
				.behaviour( assrt -> {
					// no message assertions
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE Residue 'TestResidue'",
				" | 1st residue | 5st residue |",
				"",
				"SKIP Ancestor failed" ),
				copypasta( tf.events() ) );
		assertEquals( copypasta(
				"abc [] UNEXPECTED",
				"def [] SKIP" ),
				copypasta( tf.results() ) );
	}

	/**
	 * Shows that masking operations are respected
	 */
	@Test
	void partlyMasked() {
		TestFlocessor tf = new TestFlocessor( "", TestModel.withResidue() )
				.system( State.FUL, B )
				.checkers( new TestChecker() )
				.masking( Nprdct.DIGITS )
				.behaviour( assrt -> {
					// no message assertions
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE Residue 'TestResidue'",
				" | ?st residue | ?st residue |",
				"",
				"COMPARE Residue 'TestResidue'",
				" | ?nd residue | ?st residue |" ),
				copypasta( tf.events() ) );
		assertEquals( copypasta(
				"abc [] SUCCESS",
				"def [] UNEXPECTED" ),
				copypasta( tf.results() ) );
	}

	/**
	 * Full success path, exercising reporting content
	 */
	@Test
	void fullyMasked() {
		TestFlocessor tf = new TestFlocessor( "", TestModel.withResidue() )
				.system( State.FUL, B )
				.checkers( new TestChecker() )
				.masking( Nprdct.DIGITS_AND_SUFFIX )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					// no message assertions
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE Residue 'TestResidue'",
				" | ?__ residue | ?__ residue |",
				"",
				"COMPARE Residue 'TestResidue'",
				" | ?__ residue | ?__ residue |" ),
				copypasta( tf.events() ) );
		assertEquals( copypasta(
				"abc [] SUCCESS",
				"def [] SUCCESS" ),
				copypasta( tf.results() ) );

		ObjectMapper JSON = new ObjectMapper().enable( SerializationFeature.INDENT_OUTPUT );
		Reader rd = new Reader( tf.report() );

		assertEquals( copypasta(
				"[ {",
				"  'name' : 'TestResidue',",
				"  'raw' : {",
				"    'value' : '1st residue'",
				"  },",
				"  'full' : {",
				"    'expect' : '1st residue',",
				"    'actual' : '5st residue'",
				"  },",
				"  'masked' : {",
				"    'expect' : '?__ residue',",
				"    'actual' : '?__ residue'",
				"  }",
				"} ]",
				"[ {",
				"  'name' : 'TestResidue',",
				"  'raw' : {",
				"    'value' : '2nd residue'",
				"  },",
				"  'full' : {",
				"    'expect' : '2nd residue',",
				"    'actual' : '5st residue'",
				"  },",
				"  'masked' : {",
				"    'expect' : '?__ residue',",
				"    'actual' : '?__ residue'",
				"  }",
				"} ]" ),
				copypasta(
						rd.read().entries.stream()
								.map( rd::detail )
								.map( d -> {
									try {
										return JSON.writeValueAsString( d.residue );
									}
									catch( IOException e ) {
										throw new UncheckedIOException( e );
									}
								} ) ) );
	}

	private enum Nprdct implements Unpredictable {
		DIGITS, DIGITS_AND_SUFFIX,
	}

	private static class TestChecker extends Checker<TestResidue> {
		public TestChecker() {
			super( TestResidue.class );
		}

		@Override
		public Message expected( TestResidue residue ) {
			return new Text( residue.value() )
					.masking( Nprdct.DIGITS, m -> m.replace( "\\d", "?" ) )
					.masking( Nprdct.DIGITS_AND_SUFFIX, m -> m.replace( "\\d+\\w{2}", "?__" ) );
		}

		@Override
		public byte[] actual( TestResidue residue, List<Assertion> behaviour ) {
			return "5st residue".getBytes( UTF_8 );
		}
	}
}
