package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.Reporting.QUIETLY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.TestModel.Actors;
import com.mastercard.test.flow.report.QuietFiles;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;

/**
 * Exercises replay mode, wherein we use a historic report as the source of data
 * rather than the actual system
 */
@SuppressWarnings("static-method")
class ReplayTest {

	private static TestFlocessor build( String title, List<String> behaviourLog ) {
		return new TestFlocessor( title, TestModel.abc() )
				.system( State.LESS, Actors.B )
				.reporting( QUIETLY, title )
				.behaviour( assrt -> {
					behaviourLog.add( "exercising system with "
							+ assrt.expected().request().assertable() );
					assrt.actual().response( assrt.expected().response().content() );
					assrt.assertChildren( i -> true )
							.forEach( ca -> ca.actual().request( ca.expected().request().content() ) );
				} );
	}

	private static Path generateReport( String title ) {
		List<String> behaviourLog = new ArrayList<>();
		TestFlocessor tf = build( title, behaviourLog );
		tf.execute();

		// check behaviour
		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] request",
				" | B request to C | B request to C |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ),
				"direct mode events" );
		assertEquals(
				copypasta( "abc [] SUCCESS" ),
				copypasta( tf.results() ),
				"direct mode results" );
		assertEquals(
				copypasta( "exercising system with A request to B" ),
				copypasta( behaviourLog ),
				"direct mode behaviour" );

		return tf.report();
	}

	/**
	 * This will disrupt normal testing, so make sure it's cleared after each test
	 */
	@AfterEach
	void clearReplayProperty() {
		System.clearProperty( AssertionOptions.REPLAY.property() );
	}

	/**
	 * Shows that active replay mode can be detected
	 */
	@Test
	void isActive() {
		Assertions.assertFalse( Replay.isActive(), "before" );

		System.setProperty( AssertionOptions.REPLAY.property(), "foo" );

		Assertions.assertTrue( Replay.isActive(), "after" );
	}

	/**
	 * Shows what happens when we try to replay the latest report, but there
	 * <i>is</i> no latest report
	 */
	@Test
	void noLatest() {
		QuietFiles.recursiveDelete( Paths.get( AssertionOptions.ARTIFACT_DIR.value() ) );
		System.setProperty( AssertionOptions.REPLAY.property(), Replay.LATEST );

		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> build( "bar", null ) );
		assertEquals( "Failed to find execution report in " + AssertionOptions.ARTIFACT_DIR.value(),
				ise.getMessage() );

		QuietFiles.createDirectories( Paths.get( AssertionOptions.ARTIFACT_DIR.value() ) );

		ise = assertThrows( IllegalStateException.class,
				() -> build( "bar", null ) );
		assertEquals( "Failed to find execution report in " + AssertionOptions.ARTIFACT_DIR.value(),
				ise.getMessage() );
		assertEquals( null, ise.getCause() );
	}

	/**
	 * Shows that setting the replay property to "latest" will pick up the most
	 * recently-generated report
	 *
	 * @throws Exception If the file move files
	 */
	@Test
	void latest() throws Exception {
		try {
			QuietFiles.recursiveDelete( Paths.get( "target/mctf/latest" ) );

			// this one will be ignored as it's not the latest valid report
			AssertionOptions.REPORT_NAME.set( "older" );
			generateReport( "latest" );

			// This is the one that will be replayed
			AssertionOptions.REPORT_NAME.set( "replay_source" );
			Path dataSource = generateReport( "latest" );

			// this one was created later but is named as a replay report, so should be
			// ignored as a source of data
			AssertionOptions.REPORT_NAME.set( "later_replay" );
			generateReport( "latest" );

			// this one was also created later but lacks detail files, so should also be
			// ignored as a source of data
			AssertionOptions.REPORT_NAME.set( "no_detail" );
			Path noDetails = generateReport( "latest" );
			QuietFiles.recursiveDelete( noDetails.resolve( "detail" ) );

			// this one was also created later but lacks an index, so should also be
			// ignored as a source of data
			AssertionOptions.REPORT_NAME.set( "no_index" );
			Path noIndex = generateReport( "latest" );
			QuietFiles.recursiveDelete( noIndex.resolve( "index.html" ) );

			// activate replay mode
			System.setProperty( AssertionOptions.REPLAY.property(), Replay.LATEST );

			// run again
			TestFlocessor tf = build( "latest", new ArrayList<>() );
			tf.execute();

			assertTrue( tf.report().toString().endsWith( "_replay" ),
					"reports from replay runs (e.g.: " + tf.report()
							+ ") have an obvious filename suffix" );
			// read the report generated by the replay run
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			assertEquals( "latest (replay)",
					index.meta.testTitle,
					"reports from replay runs are titled as such" );
			FlowData fd = r.detail( index.entries.get( 0 ) );

			String msg = fd.logs.get( 0 ).message;
			assertEquals( "Replaying data from " + dataSource, msg,
					"flow log first entry shows the data source" );

		}
		finally {
			AssertionOptions.REPORT_NAME.clear();
		}
	}

	/**
	 * Everything works as intended: we make the same assertions based on data from
	 * a report rather than hitting the system again
	 */
	@Test
	void happy() {
		Path p = generateReport( "happy" );

		// activate replay mode
		System.setProperty( AssertionOptions.REPLAY.property(), p.toString() );

		// run again
		List<String> behaviourLog = new ArrayList<>();
		TestFlocessor tf = build( "blah", behaviourLog );
		tf.execute();

		// check behaviour:
		// we have the same comparisons being made...
		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] request",
				" | B request to C | B request to C |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ),
				"replay mode events" );
		// ...with the same results...
		assertEquals(
				copypasta( "abc [] SUCCESS" ),
				copypasta( tf.results() ),
				"replay mode results" );
		// but look! Our test behaviour has *not* been executed!
		assertEquals(
				copypasta(),
				copypasta( behaviourLog ),
				"replay mode behaviour" );
	}

	/**
	 * Shows what happens when we can't find the root interaction in the report
	 *
	 * @throws Exception on IO problems
	 */
	@Test
	void missingInteraction() throws Exception {
		Path p = generateReport( "missingInteraction" );

		// edit the report to hide the interaction data - adding a tag does the trick
		Path detailPage = p.resolve( "detail" )
				.resolve( "56B672A62688DE11DE0F318C151A98B6.html" );
		String content = new String( Files.readAllBytes( detailPage ), UTF_8 );
		content = content.replaceAll(
				"\"tags\" : \\[ \\],", "\"tags\" : [ \"disguise\" ]," );
		Files.write( detailPage, content.getBytes( UTF_8 ) );

		System.setProperty( AssertionOptions.REPLAY.property(), p.toString() );

		// run again
		List<String> behaviourLog = new ArrayList<>();
		TestFlocessor tf = build( "blah", behaviourLog );
		tf.execute();

		// check behaviour:
		assertEquals( copypasta(
				"SKIP No data for interaction A -> B []" ),
				copypasta( tf.events() ),
				"replay mode events" );
		assertEquals(
				copypasta( "abc [] SKIP" ),
				copypasta( tf.results() ),
				"replay mode results" );
		assertEquals(
				copypasta(),
				copypasta( behaviourLog ),
				"replay mode behaviour" );
	}

	/**
	 * Shows what happens when we can't find a child interaction in the report
	 *
	 * @throws Exception on IO problems
	 */
	@Test
	void missingChildInteraction() throws Exception {
		Path p = generateReport( "missingChildInteraction" );

		// edit the report to hide the interaction data - adding a tag does the trick
		Path detailPage = p.resolve( "detail" )
				.resolve( "56B672A62688DE11DE0F318C151A98B6.html" );
		String content = new String( Files.readAllBytes( detailPage ), UTF_8 );

		// this is hateful, but we're exploiting the indentation to target only the tags
		// element of the B-C interaction
		content = content.replaceAll(
				"      \"tags\" : \\[ \\],", "      \"tags\" : [ \"disguise\" ]," );
		Files.write( detailPage, content.getBytes( UTF_8 ) );

		System.setProperty( AssertionOptions.REPLAY.property(), p.toString() );

		// run again
		List<String> behaviourLog = new ArrayList<>();
		TestFlocessor tf = build( "blah", behaviourLog );
		tf.execute();

		// check behaviour:
		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ),
				"replay mode events" );
		assertEquals(
				copypasta( "abc [] SUCCESS" ),
				copypasta( tf.results() ),
				"replay mode results" );
		assertEquals(
				copypasta(),
				copypasta( behaviourLog ),
				"replay mode behaviour" );
	}

	/**
	 * Shows what happens when we can't find the flow data file
	 *
	 * @throws Exception on IO problems
	 */
	@Test
	void missingFlowData() throws Exception {
		Path p = generateReport( "missingFlowData" );

		// edit the report to delete the flow data
		Path detailPage = p.resolve( "detail" )
				.resolve( "56B672A62688DE11DE0F318C151A98B6.html" );
		Files.delete( detailPage );

		System.setProperty( AssertionOptions.REPLAY.property(), p.toString() );

		// run again
		List<String> behaviourLog = new ArrayList<>();
		TestFlocessor tf = build( "blah", behaviourLog );
		tf.execute();

		// check behaviour:
		assertEquals( copypasta(
				"SKIP No file for abc [PASS], expected it at detail/56B672A62688DE11DE0F318C151A98B6.html" ),
				copypasta( tf.events() ),
				"replay mode events" );
		assertEquals(
				copypasta( "abc [] SKIP" ),
				copypasta( tf.results() ),
				"replay mode results" );
		assertEquals(
				copypasta(),
				copypasta( behaviourLog ),
				"replay mode behaviour" );
	}

	/**
	 * Shows what happens when we can't find the pointer to the flow data file
	 *
	 * @throws Exception on IO problems
	 */
	@Test
	void missingIndexEntry() throws Exception {
		Path p = generateReport( "missingIndexEntry" );

		// edit the index to disguise the flow entry - changing the description will do
		// the trick
		Path indexPage = p.resolve( "index.html" );
		String content = new String( Files.readAllBytes( indexPage ), UTF_8 );
		content = content.replaceAll(
				"\"description\" : \"abc\"", "\"description\" : \"hidden\"" );
		Files.write( indexPage, content.getBytes( UTF_8 ) );

		System.setProperty( AssertionOptions.REPLAY.property(), p.toString() );

		// run again
		List<String> behaviourLog = new ArrayList<>();
		TestFlocessor tf = build( "blah", behaviourLog );
		tf.execute();

		// check behaviour:
		assertEquals( copypasta(
				"SKIP No matching index entry for abc []" ),
				copypasta( tf.events() ),
				"replay mode events" );
		assertEquals(
				copypasta( "abc [] SKIP" ),
				copypasta( tf.results() ),
				"replay mode results" );
		assertEquals(
				copypasta(),
				copypasta( behaviourLog ),
				"replay mode behaviour" );
	}

	/**
	 * Shows what happens when we can't find the report index
	 *
	 * @throws Exception on IO problems
	 */
	@Test
	void missingIndex() throws Exception {
		Path p = generateReport( "missingIndexEntry" );

		// delete the report index
		Files.delete( p.resolve( "index.html" ) );

		System.setProperty( AssertionOptions.REPLAY.property(), p.toString() );

		// try to run again
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> build( "blah", null ) );
		assertEquals( String.format(
				"No index data found in %s/missingIndexEntry/masked_timestamp",
				AssertionOptions.ARTIFACT_DIR.value() ),
				ise.getMessage()
						.replace( '\\', '/' )
						.replaceAll( "/\\d{6}-\\d{6}$", "/masked_timestamp" ) );
	}
}
