package com.mastercard.test.flow.assrt;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.Reporting.ALWAYS;
import static com.mastercard.test.flow.assrt.Reporting.FAILURES;
import static com.mastercard.test.flow.assrt.Reporting.NEVER;
import static com.mastercard.test.flow.assrt.Reporting.QUIETLY;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Exercises {@link Reporting} values
 */
@SuppressWarnings("static-method")
class ReportingTest {
	/**
	 * Empty prepared runs still publish a real final report, never a synthetic
	 * flow.
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void emptyFinalReportIsPublishedOnlyAtCompletion( boolean initialize, @TempDir Path directory )
			throws Exception {
		var diagnostic = new ByteArrayOutputStream();
		PrintStream original = System.err;
		try( var captured = new PrintStream( diagnostic, true, UTF_8 );
				Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "empty" );
				TestFlocessor runner = new TestFlocessor( "empty prepared run", TestModel.abc() )
						.reporting( QUIETLY ).exercising( flow -> false, ignored -> {
						} ).behaviour( a -> {
							throw new AssertionError( "filtered flow entered SUT" );
						} ) ) {
			System.setErr( captured );
			runner.finalOnlyReporting();
			try( var flows = runner.prepareFlows() ) {
				assertEquals( 0, flows.count() );
			}
			assertNull( runner.report() );
			if( initialize ) {
				runner.initializeReporting();
				assertEquals( directory.resolve( "empty" ), runner.report() );
			}
			assertFalse( Files.exists( directory.resolve( "empty" ).resolve( Writer.INDEX_FILE_NAME ) ) );
			runner.completeProcessing();
			Index index = new Reader( runner.report() ).read();
			assertEquals( "empty prepared run", index.meta.testTitle );
			assertTrue( index.entries.isEmpty() );
			runner.completeProcessing();
			assertEquals( "", diagnostic.toString( UTF_8 ),
					"repeated completion is not a report failure" );
		}
		finally {
			System.setErr( original );
		}
	}

	/**
	 * Ordinary creation/finalization faults must not become processing failures.
	 */
	@ParameterizedTest
	@CsvSource({ "creation,false", "creation,true", "completion,false", "completion,true" })
	void finalReportFaultPreservesProcessingOutcome( String phase, boolean sutFails,
			@TempDir Path directory ) throws Exception {
		Path root = directory.resolve( "reports" );
		if( "creation".equals( phase ) )
			Files.createFile( root );
		var diagnostic = new ByteArrayOutputStream();
		PrintStream original = System.err;
		var primary = new IllegalArgumentException( "original SUT failure" );
		int[] calls = { 0 };
		try( var captured = new PrintStream( diagnostic, true, UTF_8 );
				Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( root.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "run" );
				TestFlocessor runner = new TestFlocessor( "final report fault", TestModel.abc() )
						.system( State.LESS, B ).reporting( QUIETLY ).behaviour( a -> {
							calls[0]++;
							if( sutFails )
								throw primary;
							a.actual().response( "B response to A".getBytes( UTF_8 ) );
						} ) ) {
			System.setErr( captured );
			runner.finalOnlyReporting();
			Flow flow;
			try( var prepared = runner.prepareFlows() ) {
				flow = prepared.findFirst().orElseThrow();
			}
			runner.initializeReporting();
			if( sutFails )
				assertSame( primary,
						assertThrows( IllegalArgumentException.class, () -> runner.process( flow ) ) );
			else
				assertDoesNotThrow( () -> runner.process( flow ) );
			assertEquals( 1, calls[0] );
			if( "completion".equals( phase ) ) {
				Files.createDirectory( runner.report().resolve( Writer.INDEX_FILE_NAME ) );
				assertEquals( "", diagnostic.toString( UTF_8 ) );
			}
			assertDoesNotThrow( runner::completeProcessing );
			assertDoesNotThrow( runner::completeProcessing );
			assertThrows( IllegalStateException.class, () -> runner.process( flow ) );
			assertEquals( 1, calls[0], "report failure must not reopen processing" );
			assertEquals( 0, primary.getSuppressed().length );
			assertFalse( Files.isRegularFile( root.resolve( "run" ).resolve( Writer.INDEX_FILE_NAME ) ) );
			assertEquals( 1, diagnostic.toString( UTF_8 ).lines()
					.filter( line -> line.startsWith( "Flow: Report failed: " ) ).count() );
		}
		finally {
			System.setErr( original );
		}
	}

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
		try( TestFlocessor tf = new TestFlocessor( "successTagging", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} ) ) {
			tf.execute();

			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			assertTrue( ie.tags.contains( "PASS" ), ie.tags.toString() );
			FlowData fd = r.detail( ie );
			assertTrue( fd.tags.contains( "PASS" ), fd.tags.toString() );
		}
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
		try( TestFlocessor tf = new TestFlocessor( "failureReporting", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					assrt.actual()
							.request( "unexpected req content!".getBytes( UTF_8 ) )
							.response( "unexpected res content!".getBytes( UTF_8 ) );
				} ) ) {
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
	}

	/**
	 * Shows that flows that explode are tagged as such in the report and that the
	 * failures are logged
	 */
	@Test
	void errorTagging() {
		try( TestFlocessor tf = new TestFlocessor( "errorTagging", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					throw new RuntimeException( "kaboom" );
				} ) ) {
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
	}

	/**
	 * Shows that flows where we are supplied with unparseable bytes are tagged and
	 * logged correctly in the report
	 */
	@Test
	void parseFailureTagging() {
		try( TestFlocessor tf = new TestFlocessor( "parseFailureTagging",
				TestModel.abcWithParseFailures() )
						.system( State.FUL, B )
						.reporting( QUIETLY )
						.behaviour( assrt -> {
							assrt.actual().response( new byte[] { 0 } );
						} ) ) {
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
	}

	/**
	 * Shows that flows that are skipped are tagged as such in the report
	 */
	@Test
	void skipTagging() {
		try( TestFlocessor tf = new TestFlocessor( "skipTagging", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					// no assertions made
				} ) ) {
			tf.execute();

			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
			FlowData fd = r.detail( ie );
			assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
		}
	}

	/**
	 * Shows that the {@link Actor}s under test are recorded to the report
	 */
	@Test
	void exercised() {
		try( TestFlocessor tf = new TestFlocessor( "exercised", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> {
					// no assertions made
				} ) ) {
			tf.execute();

			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			FlowData fd = r.detail( ie );
			assertEquals( "[B]", fd.exercised.toString() );
		}
	}

	/**
	 * Completion retains its configured destination after temporary options end.
	 *
	 * @param name Report name beneath the configured artifact directory
	 * @param dir  Isolated artifact directory
	 */
	@ParameterizedTest
	@ValueSource(strings = { "report", "sub/path/report" })
	void completionAfterOptionsEnd( String name, @TempDir Path dir ) {
		TestFlocessor tf = new TestFlocessor( "publication", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY )
				.behaviour( assrt -> assrt.actual().response( assrt.expected().response().content() ) );
		try( tf ) {
			try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
					Temporary reportName = AssertionOptions.REPORT_NAME.temporarily( name ) ) {
				tf.execute();
				assertEquals( "abc [] SUCCESS", tf.results() );
				Reader reader = new Reader( tf.report() );
				Entry entry = reader.read().entries.get( 0 );
				assertTrue( entry.tags.contains( "PASS" ) );
				assertTrue( reader.detail( entry ).tags.contains( "PASS" ) );
			}
		}
		assertEquals( "publication", new Reader( dir.resolve( name ) ).read().meta.testTitle );
	}

	/**
	 * An ordinary latest file or directory belongs to the user, not publication.
	 *
	 * @param directory Whether latest is a directory containing a file
	 * @param dir       Isolated artifact directory
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void ordinaryLatest( boolean directory, @TempDir Path dir ) throws Exception {
		Path latest = dir.resolve( "latest" );
		Path retained = directory ? Files.createDirectory( latest ).resolve( "retained" ) : latest;
		Files.writeString( retained, "user content" );
		TestFlocessor tf = new TestFlocessor( "ordinary latest", TestModel.abc() )
				.system( State.FUL, B ).reporting( QUIETLY );
		try( tf ) {
			try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
					Temporary name = AssertionOptions.REPORT_NAME.temporarily( "sub/path/report" ) ) {
				tf.execute();
				assertEquals( "user content", Files.readString( retained ) );
			}
		}
		assertEquals( "user content", Files.readString( retained ) );
		assertFalse( Files.isSymbolicLink( latest ) );
		assertEquals( "ordinary latest", new Reader( tf.report() ).read().meta.testTitle );
	}

	/**
	 * Shows that a stably-named symlink is created that points to the latest report
	 *
	 * @param name Null for the default name, or a configured nested name
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = "sub/path/report")
	@DisabledOnOs(OS.WINDOWS) // requires special permissions to create symlinks
	void symlink( String name ) throws Exception {
		Path linkedPath = Paths.get( "target/mctf/symlink/latest" );
		if( Files.isSymbolicLink( linkedPath ) ) {
			Files.delete( linkedPath );
		}
		TestFlocessor tf = new TestFlocessor( "symlink", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( QUIETLY, "symlink" )
				.behaviour( assrt -> {
					// no assertions made
				} );
		try( tf ) {
			try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( "target/mctf" );
					Temporary t = AssertionOptions.REPORT_NAME.temporarily( name ) ) {
				tf.execute();
				assertEquals( 1, new Reader( tf.report() ).read().entries.size() );
			}
		}

		Path writtenPath = tf.report();

		if( name == null ) {
			assertEquals( linkedPath.getParent(), writtenPath.getParent() );
			assertTrue( writtenPath.getFileName().toString().matches( "\\d{6}-\\d{6}" ) );
		}
		else {
			assertEquals( "target/mctf/symlink/sub/path/report", writtenPath.toString() );
		}
		assertTrue( Files.exists( linkedPath, LinkOption.NOFOLLOW_LINKS ),
				"The expected symlink has been created" );
		assertTrue( Files.isSymbolicLink( linkedPath ),
				"It really is a symlink" );

		Reader dr = new Reader( writtenPath );
		Index direct = dr.read();

		Reader lr = new Reader( linkedPath );
		Index linked = lr.read();

		// reading either provides the same data
		assertEquals( direct.meta.modelTitle, linked.meta.modelTitle );
		assertEquals( direct.meta.testTitle, linked.meta.testTitle );
		assertEquals( direct.meta.timestamp, linked.meta.timestamp );
	}
}
