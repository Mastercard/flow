package com.mastercard.test.flow.report.duct;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Exercises {@link Search}
 */
class SearchTest {
	private static final Path ROOT = Paths.get( "target", "SearchTest", "find" );

	@BeforeAll
	static void createReports() {
		// create a bunch of reports
		QuietFiles.recursiveDelete( ROOT );
		Stream.of(
				"obvious",
				"lightly/obscured",
				"lightly/obscured/with/a/matryoshka/report",
				"buried/a/few/levels/deep",
				"src/main/java/no_thanks",
				"lightly/src/test/java/nor/this",
				"buried/in/node_modules/absolutely/not" )
				.forEach( p -> DuctTestUtil.createReport(
						ROOT.resolve( p ), "model", "title", Instant.now() ) );
	}

	/**
	 * Exercises {@link Search#find(java.nio.file.Path)}
	 */
	@Test
	void find() {
		// search for reports from various start points, see which we find
		BiConsumer<String, String> test = ( in, out ) -> assertEquals(
				out,
				Search.find( ROOT.resolve( in ) )
						.map( p -> ROOT.relativize( p ).toString().replace( '\\', '/' ) )
						.sorted()
						.collect( joining( "\n" ) ),
				"searching from " + in );

		// searching from the root. Nested reports and those in source trees are not
		// found
		test.accept( "", ""
				+ "buried/a/few/levels/deep\n"
				+ "lightly/obscured\n"
				+ "obvious" );

		// searching directly in a report
		test.accept( "obvious", ""
				+ "obvious" );

		// the search stops when it hits a report, but if you start the search deeper it
		// will continue
		test.accept( "lightly/obscured", ""
				+ "lightly/obscured" );
		test.accept( "lightly/obscured/with", ""
				+ "lightly/obscured/with/a/matryoshka/report" );

		// similarly, you can search inside a forbidden dir
		test.accept( "buried/in/node_modules/absolutely", ""
				+ "buried/in/node_modules/absolutely/not" );
	}
}
