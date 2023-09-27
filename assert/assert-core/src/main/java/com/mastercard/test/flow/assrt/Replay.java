
package com.mastercard.test.flow.assrt;

import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.InteractionData;

/**
 * Utility for asserting a system model against data extracted from an execution
 * report.
 * <p>
 * The execution report produced by a test run contains the data extracted from
 * the system under test. If we know that the system is in a good state and we
 * just want to get our system model to match it accurately, we can re-use the
 * data in an execution report as we iterate updates to the model. This is
 * probably going to be more efficient than iterating against the actual system.
 * </p>
 */
public class Replay {

	/**
	 * This will appear in the names of execution reports where the source of data
	 * was itself an execution report
	 */
	public static final String REPLAYED_SUFFIX = "_replay";

	/**
	 * The value to supply to {@link AssertionOptions#REPLAY} to replay from the
	 * latest local execution report
	 */
	public static final String LATEST = "latest";

	/**
	 * Determines if replay mode is active. This is useful to avoid test setup and
	 * teardown actions that would be pointless when we're replaying historic data
	 * rather than hitting an actual system
	 *
	 * @return <code>true</code> if the test will be replaying historical data
	 */
	public static boolean isActive() {
		return AssertionOptions.REPLAY.value() != null;
	}

	/**
	 * Works out the appropriate data source based on system properties
	 *
	 * @param path The elements of the path from the Flow artifact directory to
	 *             where the test writes reports
	 * @return The path to the report that should be used as a source of data, or
	 *         <code>null</code> if there is no such report
	 */
	public static String source( String... path ) {
		String src = AssertionOptions.REPLAY.value();
		if( LATEST.equals( src ) ) {
			src = mostRecent( path );
		}
		return src;
	}

	private final Reader reader;
	private final Index index;

	/**
	 * @param source The path to the report to use as a source of data, or
	 *               <code>null</code> for an empty {@link Replay}
	 * @see #source(String...)
	 */
	public Replay( String source ) {
		reader = source != null ? new Reader( Paths.get( source ) ) : null;
		index = reader != null ? reader.read() : null;

		if( source != null && index == null ) {
			throw new IllegalStateException( "No index data found in " + source );
		}
	}

	/**
	 * Determines if we have data to replay
	 *
	 * @return <code>true</code> if there is data
	 */
	public boolean hasData() {
		return index != null;
	}

	/**
	 * Extracts actual data from the report
	 *
	 * @param t A assertion for which we want actual data
	 * @return The reason why we didn't find any data in the replayed report
	 */
	public String populate( Assertion t ) {
		// find the index entry that matches the flow
		Entry ie = index.entries.stream()
				.filter( e -> Reader.matches( e, t.flow() ) )
				.findFirst()
				.orElse( null );
		if( ie == null ) {
			return "No matching index entry for " + t.flow().meta().id();
		}

		// read the indexed flow data
		FlowData fd = reader.detail( ie );
		if( fd == null ) {
			return String.format( "No file for %s %s, expected it at detail/%s.html",
					ie.description, ie.tags, ie.detail );
		}

		// Check the interactions match
		if( !matches( t.expected(), fd.root ) ) {
			return String.format( "No data for interaction %s -> %s %s",
					t.expected().requester().name(),
					t.expected().responder().name(),
					t.expected().tags() );
		}

		// populate the assert with the actuals from the report
		populate( t, fd.root );
		return null;
	}

	/**
	 * Searches the artifact dir (typically target/mctf or build/mctf) for the most
	 * recent execution report
	 *
	 * @return The most recent execution report
	 */
	private static final String mostRecent( String... path ) {
		Path report = Reader.mostRecent(
				// searching in the test's report directory
				Stream.concat(
						Stream.of( AssertionOptions.ARTIFACT_DIR.value() ),
						Stream.of( path ) )
						.filter( Objects::nonNull )
						.collect( joining( "/" ) ),
				// let's stick to primary sources of data
				p -> !p.getFileName().toString().endsWith( REPLAYED_SUFFIX ) );
		if( report == null ) {
			throw new IllegalStateException(
					"Failed to find execution report in " + AssertionOptions.ARTIFACT_DIR.value() );
		}
		return report.toString();
	}

	/**
	 * @param ntr An interaction from the model
	 * @param id  An interaction from an execution report
	 * @return <code>true</code> if the requester,responder and tags match
	 */
	private static boolean matches( Interaction ntr, InteractionData id ) {
		return id != null
				&& ntr.requester().name().equals( id.requester )
				&& ntr.responder().name().equals( id.responder )
				&& ntr.tags().equals( id.tags );
	}

	/**
	 * <p>
	 * Populates a system assertion with actual data taken from a historic execution
	 * report.
	 * </p>
	 * <p>
	 * Note that the two objects are assumed to have returned <code>true</code> from
	 * {@link #matches(Interaction, InteractionData)}
	 * </p>
	 *
	 * @param asrt An assertion on the system model
	 * @param id   Data from an execution report
	 */
	private static void populate( Assertion asrt, InteractionData id ) {
		// A report on disk is on the other side of a trust boundary, so we'll
		// proceed with caution
		Optional.ofNullable( id )
				.map( o -> o.request )
				.map( o -> o.full )
				.map( o -> o.actualBytes )
				.ifPresent( asrt.actual()::request );
		Optional.ofNullable( id )
				.map( o -> o.response )
				.map( o -> o.full )
				.map( o -> o.actualBytes )
				.ifPresent( asrt.actual()::response );

		// the flocessor will supply the entrypoint interactions into the system under
		// test, but it's perfectly possible to add actual data for downstream
		// interactions (e.g.: you might be able to capture the data that comes out of
		// the system). Hence we need to recurse down the interaction structure and
		// populate the whole tree underneath
		Optional.ofNullable( id )
				.map( f -> f.children )
				.map( List::stream )
				.ifPresent( s -> s
						.forEach( child -> asrt
								.assertChildren( i -> matches( i, child ) )
								.forEach( ca -> populate( ca, child ) ) ) );
	}
}
