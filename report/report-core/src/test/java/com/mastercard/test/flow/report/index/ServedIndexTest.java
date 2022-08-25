package com.mastercard.test.flow.report.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;

import com.mastercard.test.flow.report.seq.Browser;

/**
 * Exercises the report index as browsed over http
 */
@ExtendWith(Browser.class)
class ServedIndexTest extends AbstractIndexTest {

	private static final Path LOG_FILE_PATH = Paths.get( "target/test_log.txt" );

	/***/
	ServedIndexTest() {
		super( report.url() );
	}

	/**
	 * Checks that the expected log file has been created
	 * <p>
	 * Spark logs some stuff on startup via slf4j. If an appropriate logger
	 * implementation isn't available slf4j will complain on stdout. We don't like
	 * that, so we've included the slf4j-simple implementation on the classpath to
	 * keep slf4j happy. It's pretty easy to create a dependency version mismatch
	 * that breaks that behaviour though, so this test will assert that the log file
	 * is being created as expected, which implies that we're avoiding the stdout
	 * noise that we don't want.
	 * </p>
	 */
	@AfterAll
	static void checkLogs() {
		Assertions.assertTrue( Files.exists( LOG_FILE_PATH ),
				"logging behaviour broken!" );
	}
}
