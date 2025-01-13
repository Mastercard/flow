package com.mastercard.test.flow.report.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
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
	 * Checks that the interaction diagram for all flows is show as expected
	 */
	@Test
	@DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless",
			disabledReason = "mysterious failure in CI")
	@Disabled
	void interactions() {
		iseq.hasInteractionSummary( "2 interactions between 3 actors" )
				.expandInteractions()
				.hasInteractions(
						"Nodes:",
						"  AVA",
						"  BEN",
						"  CHE",
						"Edges:",
						"  AVA normal solid BEN",
						"  AVA normal solid CHE" );
	}

	/**
	 * Checks that the interaction diagram for a filtered flow list is show as
	 * expected
	 */
	@Test
	@DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless",
			disabledReason = "mysterious failure in CI")
	void filteredInteractions() {
		iseq.clickTag( "PASS" )
				.hasInteractionSummary( "1 interactions between 2 actors" )
				.expandInteractions()
				.hasInteractions(
						"Nodes:",
						"  AVA",
						"  BEN",
						"  CHE",
						"Edges:",
						"  AVA normal solid BEN",
						"  AVA normal solid CHE <INVISIBLE>" );

		iseq.toggleFilteredActorHide()
				.hasInteractions(
						"Nodes:",
						"  AVA",
						"  BEN",
						"Edges:",
						"  AVA normal solid BEN" );
	}

	/**
	 * Checks that the user can extract the mermaid markup
	 */
	@Test
	@DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless",
			disabledReason = "no clipboard")
	@Disabled
	void mermaidMarkup() {
		iseq.expandInteractions()
				.hasMermaidMarkup(
						"graph LR",
						"  AVA --> BEN",
						"  AVA --> CHE" );

		iseq.diagramOrientation( "TD" )
				.hasMermaidMarkup(
						"graph TD",
						"  AVA --> BEN",
						"  AVA --> CHE" );

		iseq.clickTag( "PASS" )
				.hasMermaidMarkup(
						"graph TD",
						"  AVA --> BEN",
						"  AVA ~~~ CHE" );

		iseq.toggleFilteredActorHide()
				.hasMermaidMarkup(
						"graph TD",
						"  AVA --> BEN" );
	}

	/**
	 * Checks that the interaction diagram highlights the hovered flow as expected
	 */
	@Test
	@DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless",
			disabledReason = "mysterious failure in CI")
	@Disabled
	void hoveredInteractions() {
		iseq
				.expandInteractions()
				.hoverEntry( "basis       [PASS, abc, def]" )
				.hasInteractionSummary( "1 interactions between 2 actors" )
				.hasInteractions(
						"Nodes:",
						"  AVA",
						"  BEN",
						"  CHE",
						"Edges:",
						"  AVA thick solid BEN",
						"  AVA normal dotted CHE" );
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
