package com.mastercard.test.flow.report.duct;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Exercises {@link Duct}
 */
class DuctTest {

	@BeforeAll
	static void setup() {
		// make sure there is no existing instance
		DuctTestUtil.ensureDeath();
		// delete all reports and index files
		QuietFiles.recursiveDelete( Duct.SERVED_DIRECTORY );

		// clear out our local report dir
		Path root = Paths.get( "target", "DuctTest", "serve" );
		QuietFiles.recursiveDelete( root );

		Path first = DuctTestUtil.createReport( root.resolve( "first" ),
				"first", "report", Instant.ofEpochSecond( 10 ),
				"abc,def,PASS", "ghi,FAIL,jkl", "SKIP,mno", "pqr,ERROR" );
		Duct.serve( first );

		DuctTestUtil.waitForLife();
	}

	@Test
	void serve() {
		Path report = DuctTestUtil.createReport( Paths.get( "target", "DuctTest", "serve" ),
				"model", "title", Instant.ofEpochSecond( 10 ),
				"abc,def,PASS", "ghi,FAIL,jkl", "SKIP,mno", "pqr,ERROR" );

		Duct.serve( report );

		DuctTestUtil.waitForLife();

		System.out.println( "it lives!" );
	}
}
