package com.mastercard.test.flow.assrt.filter;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.filter.mock.Flw;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;
import com.mastercard.test.flow.report.QuietFiles;
import com.mastercard.test.flow.report.Writer;

/**
 * Exercises {@link Filter}
 */
@SuppressWarnings("static-method")
class FilterTest {

	/**
	 * Clears the system properties
	 */
	@BeforeEach
	@AfterEach
	public void clearProperties() {
		FilterOptions.INCLUDE_TAGS.clear();
		FilterOptions.EXCLUDE_TAGS.clear();
		FilterOptions.INDICES.clear();
		FilterOptions.FILTER_REPEAT.clear();
	}

	/**
	 * Loading filters from properties
	 */
	@Test
	void propertyLoad() {
		FilterOptions.INCLUDE_TAGS.set( "a,b, c" );
		FilterOptions.EXCLUDE_TAGS.set( " d, e   " );
		FilterOptions.INDICES.set( "1, 2, foo, 3  " );

		Filter filter = new Filter( null );

		assertEquals( "[a, b, c]", filter.includedTags().toString() );
		assertEquals( "[d, e]", filter.excludedTags().toString() );
		assertEquals( "[1, 2, 3]", filter.indices().toString() );
	}

	/**
	 * Saving and loading to/from storage
	 *
	 * @throws IOException If our file deletion fails
	 */
	@Test
	void persistence() throws IOException {
		// clean out historic config
		Files.deleteIfExists( Paths.get(
				FilterOptions.ARTIFACT_DIR.value(), "filters.json" ) );

		Model mdl = new Mdl().withFlows(
				new Flw( "b [b]" ),
				new Flw( "abc [a, b, c]" ),
				new Flw( "bcd [b, c, d]" ),
				new Flw( "cde [c, d, e]" ),
				new Flw( "c [c]" ) );

		Filter filter = new Filter( mdl )
				.includedTags( Collections.singleton( "c" ) )
				.excludedTags( Collections.singleton( "a" ) )
				.indices( Stream.of( 1, 3 ).collect( toSet() ) )
				.save();

		assertEquals( "c [c]",
				filter.flows()
						.map( f -> f.meta().id() )
						.collect( joining( ", " ) ) );

		filter = new Filter( mdl ).load();
		assertEquals( "abc [a, b, c], b [b], bcd [b, c, d], c [c], cde [c, d, e]",
				filter.flows()
						.map( f -> f.meta().id() )
						.collect( joining( ", " ) ),
				"system property not set - nothing loaded" );

		FilterOptions.FILTER_REPEAT.set( "true" );

		filter = new Filter( mdl ).load();
		assertEquals( "c [c]",
				filter.flows()
						.map( f -> f.meta().id() )
						.collect( joining( ", " ) ),
				"system property set, last saved filter repeated" );
	}

	/**
	 * Illustrates setting filters from execution report failures
	 */
	@Test
	void failures() {
		Model mdl = new Mdl().withFlows(
				new Flw( "abc [a, b, c]" ),
				new Flw( "bcd [b, c, d]" ),
				new Flw( "cde [c, d, e]" ),
				new Flw( "def [d, e, f]" ),
				new Flw( "efg [e, f, g]" ) );

		// ensure there are no reports
		Path reportDir = Paths.get( "target", "mctf", "FilterTest", "failures" );
		QuietFiles.recursiveDelete( reportDir );

		new FltrTst( mdl )
				.loadFailuresIndices()
				.expectIndices( "No latest report to load, so no indices" )
				.expectFlows( "All flows",
						"abc", "bcd", "cde", "def", "efg" );

		FltrTst tst = new FltrTst( mdl );
		Path badPath = Paths.get( "target", "no_such_report" );
		assertEquals( "Invalid report directory 'target\\no_such_report'",
				assertThrows( IllegalArgumentException.class,
						() -> tst.loadFailuresIndices( badPath ) )
								.getMessage(),
				"Loading a specific non-existent report fails noisily" );

		// generate three reports

		Path noResults = reportDir.resolve( "noResults" );
		Writer nrw = new Writer( "model", "noResults",
				noResults );
		mdl.flows().forEach( nrw::with );

		Deque<String> resultTags = new ArrayDeque<>( Arrays.asList(
				Writer.ERROR_TAG,
				Writer.FAIL_TAG,
				Writer.PASS_TAG,
				Writer.SKIP_TAG,
				"do_not_tag" ) );

		Path earlier = reportDir.resolve( "earlier" );
		Writer ew = new Writer( "model", "earlier",
				earlier );
		// this time we add result tags, flow cde should get PASS
		mdl.flows().forEach( f -> ew.with( f, fd -> {
			String t = resultTags.removeFirst();
			if( !"do_not_tag".equals( t ) ) {
				fd.tags.add( t );
			}
			resultTags.addLast( t );
		} ) );

		// cycle the tag list...
		resultTags.addLast( resultTags.removeFirst() );

		Path later = reportDir.resolve( "later" );
		Writer lw = new Writer( "model", "later",
				later );
		// . .. so that this time flow bcd gets PASS
		mdl.flows().forEach( f -> lw.with( f, fd -> {
			String t = resultTags.removeFirst();
			if( !"do_not_tag".equals( t ) ) {
				fd.tags.add( t );
			}
			resultTags.addLast( t );
		} ) );

		new FltrTst( mdl )
				.loadFailuresIndices( noResults )
				.expectFlows( "All flows",
						"abc", "bcd", "cde", "def", "efg" );

		new FltrTst( mdl )
				.loadFailuresIndices( earlier )
				.expectFlows( "cde passed, so it's not selected for execution",
						"abc", "bcd", "def", "efg" );

		new FltrTst( mdl )
				.loadFailuresIndices( later )
				.expectFlows( "bcd passed, so it's not selected for execution",
						"abc", "cde", "def", "efg" );

		new FltrTst( mdl )
				.loadFailuresIndices()
				.expectFlows( "'later' report is selected, so same results",
						"abc", "cde", "def", "efg" );

		try {
			FilterOptions.FILTER_FAILS.set( "FilterTest/failures/earlier" );
			new FltrTst( mdl )
					.load()
					.expectFlows( "We target the earlier report based on the system property",
							"abc", "bcd", "def", "efg" );
		}
		finally {
			FilterOptions.FILTER_FAILS.clear();
		}
	}

	/**
	 * Explores the interplay of tag and index filters
	 */
	@Test
	void indexUpdates() {
		Model mdl = new Mdl().withFlows(
				new Flw( "abc [a, b, c]" ),
				new Flw( "bcd [b, c, d]" ),
				new Flw( "cde [c, d, e]" ) );

		new FltrTst( mdl )
				.includeIndex( 0, 1 )
				.expectIndices( "indices stay as what we just supplied",
						0, 1 )
				.expectFlows( "Unsurprisingly, we get the first two flows",
						"abc", "bcd" )
				.includeTag( "b" )
				.expectFlows( "We still get the first two flows",
						"abc", "bcd" )
				.expectIndices(
						"But the tag filter is just as effective as "
								+ "the index filter, so the indices are cleared" );

		new FltrTst( mdl )
				.includeIndex( 0, 1 )
				.expectIndices( "indices stay as what we just supplied",
						0, 1 )
				.expectFlows( "Unsurprisingly, we get the first two flows",
						"abc", "bcd" )
				.excludeTag( "e" )
				.expectFlows( "We still get the first two flows",
						"abc", "bcd" )
				.expectIndices(
						"But the tag filter is just as effective as "
								+ "the index filter, so the indices are cleared" );

		new FltrTst( mdl )
				.includeIndex( 2 )
				.expectIndices( "Unsurprising",
						2 )
				.expectFlows( "Unsurprising",
						"cde" )
				.includeTag( "d" )
				.expectIndices( "Index has changed to account for the tag filter",
						1 )
				.expectFlows( "Still get the same flow",
						"cde" );

		new FltrTst( mdl )
				.includeIndex( 2 )
				.expectIndices( "Unsurprising",
						2 )
				.expectFlows( "Unsurprising",
						"cde" )
				.excludeTag( "a" )
				.expectIndices( "Index has changed to account for the tag filter",
						1 )
				.expectFlows( "Still get the same flow",
						"cde" );
	}

	private static class FltrTst {
		private final Filter filter;

		public FltrTst( Model model ) {
			filter = new Filter( model );
		}

		public FltrTst includeTag( String tag ) {
			Set<String> ts = filter.includedTags();
			ts.add( tag );
			filter.includedTags( ts );
			return this;
		}

		public FltrTst excludeTag( String tag ) {
			Set<String> ts = filter.excludedTags();
			ts.add( tag );
			filter.excludedTags( ts );
			return this;
		}

		public FltrTst includeIndex( int... idx ) {
			Set<Integer> ts = filter.indices();
			for( Integer i : idx ) {
				ts.add( i );
			}
			filter.indices( ts );
			return this;
		}

		public FltrTst load() {
			Filter ret = filter.load();
			assertSame( filter, ret );
			return this;
		}

		public FltrTst loadFailuresIndices() {
			Filter ret = filter.loadFailureIndices();
			assertSame( filter, ret );
			return this;
		}

		public FltrTst loadFailuresIndices( Path report ) {
			Filter ret = filter.loadFailureIndices( report );
			assertSame( filter, ret );
			return this;
		}

		public FltrTst expectFlows( String description, String... flows ) {
			assertEquals(
					new TreeSet<>( Arrays.asList( flows ) ),
					filter.flows()
							.map( f -> f.meta().description() )
							.collect( toCollection( TreeSet::new ) ),
					description );
			return this;
		}

		public FltrTst expectIndices( String description, int... idxs ) {
			Set<Integer> expect = new TreeSet<>();
			for( Integer i : idxs ) {
				expect.add( i );
			}
			assertEquals( expect, filter.indices(), description );
			return this;
		}
	}
}
