package com.mastercard.test.flow.report.duct;

import static com.mastercard.test.flow.report.duct.DuctTestUtil.copypasta;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

		URL url = duct.add( VALID_REPORT );

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

		assertEquals( "http://localhost:2276/target_DuctTest_valid/", url.toString() );

		expectServedMeta( "The report is being served",
				url,
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

		URL not = duct.add( NOT_A_REPORT );

		assertEquals( null, not,
				"empty directory ignored" );

		URL bad = duct.add( BAD_INDEX );

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
	 * @throws InterruptedException
	 */
	@Test
	void reindex() throws InterruptedException {
		Duct duct = new Duct();
		duct.start();

		expectIndex( "index is empty", duct,
				"[ ]" );

		// add the report - it is added to the index immediately
		URL url = duct.add( VALID_REPORT );

		expectServedMeta( "The report is being served",
				url,
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
				url,
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
	 * Shows that requests to non-loopback addresses are 403-rejected
	 */
	@Test
	void nonLocal() throws Exception {
		InetAddress localhost = InetAddress.getLocalHost();
		List<String> nonLoop = Stream.of( InetAddress
				.getAllByName( localhost.getCanonicalHostName() ) )
				.filter( a -> !a.isLoopbackAddress() )
				.map( InetAddress::getHostAddress )
				.collect( toList() );

		assertTrue( nonLoop.size() > 0, "Expected at least 1 non-loopback address" );

		Duct duct = new Duct();
		duct.start();

		Stream.of( "127.0.0.1", "localhost" )
				.forEach( ip -> assertEquals(
						200,
						DuctTestUtil.heartbeat( ip ).code,
						"for " + ip ) );

		nonLoop.stream()
				.forEach( ip -> assertEquals(
						403,
						DuctTestUtil.heartbeat( ip ).code,
						"for " + ip ) );
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
				"  'path' : '/C_home_flowspace_flow_report_duct_target_DuctTest_valid/'",
				"} ]" ),
				copypasta( DuctTestUtil.index().body ),
				"the same call added the report to the existing instance" );
	}

	private static void expectIndex( String comment, Duct duct, String... expect ) {
		assertEquals(
				copypasta( expect ),
				toJson( duct.index() ),
				comment );
	}

	private static void expectServedMeta( String comment, URL url, String... expect ) {
		try {
			Meta meta = new Reader( url.toURI() ).read().meta;
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
