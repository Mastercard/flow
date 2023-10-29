package com.mastercard.test.flow.report.duct;

import static com.mastercard.test.flow.report.duct.DuctTestUtil.copypasta;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.report.QuietFiles;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Meta;

/**
 * Exercises {@link Duct}
 */
@SuppressWarnings("static-method")
class DuctTest {

	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	private static final Path REPORT_ROOT = Paths.get( "target", "DuctTest" );
	private static final Path VALID_REPORT = REPORT_ROOT.resolve( "valid" );
	private static final Path NOT_A_REPORT = REPORT_ROOT.resolve( "not_a_report" );
	private static final Path BAD_INDEX = REPORT_ROOT.resolve( "bad_index" );

	/**
	 * Builds some reports
	 */
	@BeforeAll
	static void createReports() {
		Duct.debuggingTo( LoggerFactory.getLogger( "DuctTest_Debug" )::error );

		// clear out our local report dir
		QuietFiles.recursiveDelete( REPORT_ROOT );

		DuctTestUtil.createReport( VALID_REPORT,
				"valid", "report", Instant.ofEpochSecond( 10 ),
				"abc,def,PASS", "ghi,FAIL,jkl", "SKIP,mno", "pqr,ERROR" );

		QuietFiles.createDirectories( NOT_A_REPORT );

		DuctTestUtil.createReport( BAD_INDEX,
				"second", "report", Instant.ofEpochSecond( 20 ),
				"abc,def,PASS", "ghi,FAIL,jkl", "PASS,mno", "pqr,PASS" );
		Path bdidx = BAD_INDEX.resolve( "index.html" );
		String index = new String( QuietFiles.readAllBytes( bdidx ), UTF_8 );
		index = index.replace( '{', '[' ); // mess up any json content
		QuietFiles.write( bdidx, index.getBytes( UTF_8 ) );
	}

	/**
	 * Makes sure there are no instances, then clears the saved index
	 */
	@BeforeEach
	void clearState() {
		DuctTestUtil.ensureDeath();
		DuctTestUtil.clearSavedIndex();
	}

	/**
	 * Exercises the expiry-related methods
	 */
	@Test
	void expiry() {
		Duct duct = new Duct();
		duct.start();

		Instant initial = duct.expiry();

		try {
			Thread.sleep( 1000 );
		}
		catch( InterruptedException e ) {
			throw new IllegalStateException( e );
		}

		Instant updated = duct.heartbeat();

		assertTrue( initial.isBefore( updated ),
				"heartbeat returns the new, postponed, expiry" );

		Instant after = duct.expiry();

		assertEquals( updated, after,
				"the new expiry is persisted" );
	}

	/**
	 * Exercises adding a report
	 */
	@Test
	void add() {
		Duct duct = new Duct();
		duct.start();

		expectIndex( "index is empty", duct,
				"[ ]" );

		String path = duct.add( VALID_REPORT );

		expectIndex( "report in the index", duct,
				"[ {",
				"  'meta' : {",
				"    'modelTitle' : 'valid',",
				"    'testTitle' : 'report',",
				"    'timestamp' : 10000",
				"  },",
				"  'counts' : {",
				"    'pass' : 1,",
				"    'fail' : 1,",
				"    'skip' : 1,",
				"    'error' : 1",
				"  },",
				"  'path' : '/target_DuctTest_valid/'",
				"} ]" );

		assertEquals( "/target_DuctTest_valid/", path );

		expectServedMeta( "The report is being served",
				path,
				"{",
				"  'modelTitle' : 'valid',",
				"  'testTitle' : 'report',",
				"  'timestamp' : 10000",
				"}" );
	}

	/**
	 * Shows that invalid report directories are ignored
	 */
	@Test
	void badReports() {
		Duct duct = new Duct();
		duct.start();

		expectIndex( "index is empty", duct,
				"[ ]" );

		String not = duct.add( NOT_A_REPORT );

		assertEquals( null, not,
				"empty directory ignored" );

		String bad = duct.add( BAD_INDEX );

		assertEquals( null, bad,
				"malformed index ignored" );

		expectIndex( "index is still empty", duct,
				"[ ]" );
	}

	/**
	 * Exercises adding a report and then clearing the index
	 */
	@Test
	void clearIndex() {
		Duct duct = new Duct();
		duct.start();

		expectIndex( "index is empty", duct,
				"[ ]" );

		duct.add( VALID_REPORT );

		expectIndex( "report in the index", duct,
				"[ {",
				"  'meta' : {",
				"    'modelTitle' : 'valid',",
				"    'testTitle' : 'report',",
				"    'timestamp' : 10000",
				"  },",
				"  'counts' : {",
				"    'pass' : 1,",
				"    'fail' : 1,",
				"    'skip' : 1,",
				"    'error' : 1",
				"  },",
				"  'path' : '/target_DuctTest_valid/'",
				"} ]" );

		duct.clearIndex();

		expectIndex( "index is empty again", duct,
				"[ ]" );
	}

	/**
	 * Shows that edits to served reports are reflected after reindexing
	 *
	 * @throws InterruptedException on error
	 */
	@Test
	void reindex() throws InterruptedException {
		Duct duct = new Duct();
		duct.start();

		expectIndex( "index is empty", duct,
				"[ ]" );

		// add the report - it is added to the index immediately
		String path = duct.add( VALID_REPORT );

		expectServedMeta( "The report is being served",
				path,
				"{",
				"  'modelTitle' : 'valid',",
				"  'testTitle' : 'report',",
				"  'timestamp' : 10000",
				"}" );

		// make a sneaky edit to that report
		Path p = VALID_REPORT.resolve( "index.html" );
		String updated = new String( QuietFiles.readAllBytes( p ) )
				.replaceAll( "valid", "updated" );
		QuietFiles.write( p, updated.getBytes( UTF_8 ) );

		expectServedMeta( "The update is reflected immediately in the served report",
				path,
				"{",
				"  'modelTitle' : 'updated',",
				"  'testTitle' : 'report',",
				"  'timestamp' : 10000",
				"}" );

		expectIndex( "The index is stale though", duct,
				"[ {",
				"  'meta' : {",
				"    'modelTitle' : 'valid',",
				"    'testTitle' : 'report',",
				"    'timestamp' : 10000",
				"  },",
				"  'counts' : {",
				"    'pass' : 1,",
				"    'fail' : 1,",
				"    'skip' : 1,",
				"    'error' : 1",
				"  },",
				"  'path' : '/target_DuctTest_valid/'",
				"} ]" );

		duct.reindex();

		expectIndex( "Index is now correct", duct,
				"[ {",
				"  'meta' : {",
				"    'modelTitle' : 'updated',",
				"    'testTitle' : 'report',",
				"    'timestamp' : 10000",
				"  },",
				"  'counts' : {",
				"    'pass' : 1,",
				"    'fail' : 1,",
				"    'skip' : 1,",
				"    'error' : 1",
				"  },",
				"  'path' : '/target_DuctTest_valid/'",
				"} ]" );
	}

	/**
	 * Exercises the main method
	 */
	@Test
	void main() {
		Duct.main(
				BAD_INDEX.toString(),
				NOT_A_REPORT.toString(),
				VALID_REPORT.toString() );

		assertEquals( copypasta(
				"[ {",
				"  'meta' : {",
				"    'modelTitle' : 'valid',",
				"    'testTitle' : 'report',",
				"    'timestamp' : 10000",
				"  },",
				"  'counts' : {",
				"    'pass' : 1,",
				"    'fail' : 1,",
				"    'skip' : 1,",
				"    'error' : 1",
				"  },",
				"  'path' : '%maskedroot%_flow_report_duct_target_DuctTest_valid/'",
				"} ]" ),
				copypasta( DuctTestUtil.index().body
						// the path is absolute, and we obviously can't know where this project is
						// checked out
						.replaceFirst(
								"(\"path\" : \").*(_flow_report_duct_target_DuctTest_valid/\")",
								"$1%maskedroot%$2" ) ),
				"the same call added the report to the existing instance" );
	}

	/**
	 * Exercises the process-launching behaviour of {@link Duct#serve(Path)}
	 */
	@Test
	void serve() {
		Duct.serve( NOT_A_REPORT );
		DuctTestUtil.waitForLife();

		assertEquals(
				"[ ]",
				DuctTestUtil.index().body,
				"duct is up, but the index is empty" );

		Duct.serve( VALID_REPORT );

		assertEquals( copypasta(
				"[ {",
				"  'meta' : {",
				"    'modelTitle' : 'valid',",
				"    'testTitle' : 'report',",
				"    'timestamp' : 10000",
				"  },",
				"  'counts' : {",
				"    'pass' : 1,",
				"    'fail' : 1,",
				"    'skip' : 1,",
				"    'error' : 1",
				"  },",
				"  'path' : '%maskedroot%_flow_report_duct_target_DuctTest_valid/'",
				"} ]" ),
				copypasta( DuctTestUtil.index().body
						// the path is absolute, and we obviously can't know where this project is
						// checked out
						.replaceFirst(
								"(\"path\" : \").*(_flow_report_duct_target_DuctTest_valid/\")",
								"$1%maskedroot%$2" ) ),
				"the same call added the report to the existing instance" );
	}

	private static void expectIndex( String comment, Duct duct, String... expect ) {
		assertEquals(
				copypasta( expect ),
				toJson( duct.index() ),
				comment );
	}

	private static void expectServedMeta( String comment, String path, String... expect ) {
		try {
			Meta meta = new Reader( new URI( "http://localhost:2276" + path ) ).read().meta;
			assertEquals(
					copypasta( expect ),
					toJson( meta ),
					comment );
		}
		catch( URISyntaxException e ) {
			throw new IllegalStateException( e );
		}
	}

	private static String toJson( Object o ) {
		try {
			return copypasta( JSON.writeValueAsString( o ) );
		}
		catch( JsonProcessingException e ) {
			throw new UncheckedIOException( e );
		}
	}
}
