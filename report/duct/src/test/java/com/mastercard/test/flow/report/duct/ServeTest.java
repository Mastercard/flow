package com.mastercard.test.flow.report.duct;

import static com.mastercard.test.flow.report.duct.DuctTestUtil.copypasta;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Exercises the {@link Duct#serve(Path)} entrypoint
 */
@SuppressWarnings("static-method")
class ServeTest {

	private static final Path REPORT_ROOT = Paths.get( "target", "ServeTest" );
	private static final Path VALID_REPORT = REPORT_ROOT.resolve( "valid" );
	private static final Path NOT_A_REPORT = REPORT_ROOT.resolve( "not_a_report" );

	/**
	 * Builds some reports
	 */
	@BeforeAll
	static void createReports() {
		Duct.debuggingTo( LoggerFactory.getLogger( "ServeTest_Debug" )::error );

		// clear out our local report dir
		QuietFiles.recursiveDelete( REPORT_ROOT );

		DuctTestUtil.createReport( VALID_REPORT,
				"valid", "report", Instant.ofEpochSecond( 10 ),
				"abc,def,PASS", "ghi,FAIL,jkl", "SKIP,mno", "pqr,ERROR" );

		QuietFiles.createDirectories( NOT_A_REPORT );
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
	 * Exercises the process-launching behaviour of {@link Duct#serve(Path)}
	 */
	@Test
	void serve() {
		Duct.serve( NOT_A_REPORT );
		DuctTestUtil.waitForLife( Duct.PORT );

		assertEquals(
				"[ ]",
				DuctTestUtil.index( Duct.PORT ).body,
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
				"  'path' : '%maskedroot%_report_duct_target_ServeTest_valid/'",
				"} ]" ),
				copypasta( DuctTestUtil.index( Duct.PORT ).body
						// the path is absolute, and we obviously can't know where this project is
						// checked out
						.replaceFirst(
								"(\"path\" : \").*(_report_duct_target_ServeTest_valid/\")",
								"$1%maskedroot%$2" ) ),
				"the same call added the report to the existing instance" );
	}
}
