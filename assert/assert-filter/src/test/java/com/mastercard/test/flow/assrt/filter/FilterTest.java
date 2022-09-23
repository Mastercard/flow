
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
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
	void clearProperties() {
		FilterOptions.INCLUDE_TAGS.clear();
		FilterOptions.EXCLUDE_TAGS.clear();
		FilterOptions.INDICES.clear();
		FilterOptions.FILTER_REPEAT.clear();
	}

	/**
	 * Tag inclusion filtering behaviour - flows must have all of the included tags
	 */
	@Test
	void tagInclude() {
		Model mdl = new Mdl().withFlows(
				"abc [a, b, c]",
				"bcd [b, c, d]",
				"cde [c, d, e]",
				"def [d, e, f]",
				"efg [e, f, g]" );
		BiConsumer<String, String> test = ( in, out ) -> assertEquals( out,
				new Filter( mdl )
						.includedTags( Stream.of( in.split( "," ) )
								.filter( s -> !s.isEmpty() )
								.collect( toSet() ) )
						.flows()
						.map( f -> f.meta().description() )
						.collect( joining( "," ) ),
				"for " + in );

		test.accept( "", "abc,bcd,cde,def,efg" );
		test.accept( "a", "abc" );
		test.accept( "b", "abc,bcd" );
		test.accept( "c", "abc,bcd,cde" );
		test.accept( "d", "bcd,cde,def" );

		test.accept( "a,b", "abc" );
	}

	/**
	 * Tag exclusion behaviour - flows must have none of the excluded tags
	 */
	@Test
	void tagExclude() {
		Model mdl = new Mdl().withFlows(
				"abc [a, b, c]",
				"bcd [b, c, d]",
				"cde [c, d, e]",
				"def [d, e, f]",
				"efg [e, f, g]" );
		BiConsumer<String, String> test = ( in, out ) -> assertEquals( out,
				new Filter( mdl )
						.excludedTags( Stream.of( in.split( "," ) )
								.filter( s -> !s.isEmpty() )
								.collect( toSet() ) )
						.flows()
						.map( f -> f.meta().description() )
						.collect( joining( "," ) ),
				"for " + in );

		test.accept( "", "abc,bcd,cde,def,efg" );
		test.accept( "a", "bcd,cde,def,efg" );
		test.accept( "b", "cde,def,efg" );
		test.accept( "c", "def,efg" );
		test.accept( "d", "abc,efg" );

		test.accept( "a,g", "bcd,cde,def" );
	}

	/**
	 * Combining include and exclude filters
	 */
	@Test
	void tagFilter() {
		Model mdl = new Mdl().withFlows(
				"abc [a, b, c]",
				"bcd [b, c, d]",
				"cde [c, d, e]",
				"def [d, e, f]",
				"efg [e, f, g]" );
		BiConsumer<String, String> test = ( in, out ) -> assertEquals( out,
				new Filter( mdl )
						.includedTags( Stream.of( in.split( "," ) )
								.filter( s -> !s.isEmpty() && s.startsWith( "+" ) )
								.map( s -> s.substring( 1 ) )
								.collect( toSet() ) )
						.excludedTags( Stream.of( in.split( "," ) )
								.filter( s -> !s.isEmpty() && s.startsWith( "-" ) )
								.map( s -> s.substring( 1 ) )
								.collect( toSet() ) )
						.flows()
						.map( f -> f.meta().description() )
						.collect( joining( "," ) ),
				"for " + in );

		test.accept( "", "abc,bcd,cde,def,efg" );
		test.accept( "+a", "abc" );
		test.accept( "+b", "abc,bcd" );
		test.accept( "-a", "bcd,cde,def,efg" );
		test.accept( "-b", "cde,def,efg" );
		test.accept( "+a,-b", "" );
		test.accept( "-a,+b", "bcd" );
		test.accept( "+d,-b", "cde,def" );
	}

	/**
	 * Index filtering
	 */
	@Test
	void indexFilter() {
		Model mdl = new Mdl().withFlows(
				"abc [a, b, c]",
				"bcd [b, c, d]",
				"cde [c, d, e]",
				"def [d, e, f]",
				"efg [e, f, g]" );
		BiConsumer<String, String> test = ( in, out ) -> assertEquals( out,
				new Filter( mdl )
						.indices( Stream.of( in.split( "," ) )
								.filter( s -> !s.isEmpty() )
								.map( Integer::valueOf )
								.collect( toSet() ) )
						.flows()
						.map( f -> f.meta().description() )
						.collect( joining( "," ) ),
				"for " + in );

		test.accept( "", "abc,bcd,cde,def,efg" );
		test.accept( "0", "abc" );
		test.accept( "1", "bcd" );
		test.accept( "4", "efg" );
		test.accept( "2,3", "cde,def" );
	}

	/**
	 * Combining tag and index filtering
	 */
	@Test
	void filter() {
		Model mdl = new Mdl().withFlows(
				"abc [a, b, c]",
				"bcd [b, c, d]",
				"cde [c, d, e]",
				"def [d, e, f]",
				"efg [e, f, g]" );
		BiConsumer<String, String> test = ( in, out ) -> assertEquals( out,
				new Filter( mdl )
						.includedTags( Stream.of( in.split( "," ) )
								.filter( s -> !s.isEmpty() && s.startsWith( "+" ) )
								.map( s -> s.substring( 1 ) )
								.collect( toSet() ) )
						.excludedTags( Stream.of( in.split( "," ) )
								.filter( s -> !s.isEmpty() && s.startsWith( "-" ) )
								.map( s -> s.substring( 1 ) )
								.collect( toSet() ) )
						.indices( Stream.of( in.split( "," ) )
								.filter( s -> s.matches( "\\d+" ) )
								.map( Integer::valueOf )
								.collect( toSet() ) )
						.flows()
						.map( f -> f.meta().description() )
						.collect( joining( "," ) ),
				"for " + in );

		test.accept( "", "abc,bcd,cde,def,efg" );
		test.accept( "+c", "abc,bcd,cde" );
		test.accept( "+c,-a", "bcd,cde" );
		test.accept( "+c,-a,0", "bcd" );
		test.accept( "+c,-a,1", "cde" );
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
				"abc [a, b, c]",
				"bcd [b, c, d]",
				"cde [c, d, e]",
				"def [d, e, f]",
				"efg [e, f, g]" );

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
		assertEquals( "Invalid report directory 'target/no_such_report'",
				assertThrows( IllegalArgumentException.class,
						() -> tst.loadFailuresIndices( badPath ) )
								.getMessage().replace( '\\', '/' ),
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
								+ "the index filter, so the indices are cleared" )
				.expectListenerEvents( 2 );

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
								+ "the index filter, so the indices are cleared" )
				.expectListenerEvents( 2 );

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
						"cde" )
				.expectListenerEvents( 2 );

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
						"cde" )
				.expectListenerEvents( 2 );
	}

	private static class FltrTst {

		private final Filter filter;
		private AtomicInteger listenerEvents = new AtomicInteger( 0 );

		public FltrTst( Model model ) {
			filter = new Filter( model );
			assertSame( filter,
					filter.listener( f -> {
						assertSame( filter, f );
						listenerEvents.incrementAndGet();
					} ) );
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

		public FltrTst expectListenerEvents( int count ) {
			assertEquals( count, listenerEvents.get() );
			return this;
		}
	}

	/**
	 * Exercises {@link Filter#parseIndices(String)}
	 */
	@Test
	void parseIndices() {
		BiConsumer<String, String> test = ( in, out ) -> assertEquals(
				out, Filter.parseIndices( in ).toString(), "for " + in );

		test.accept( null, "[]" );
		test.accept( "", "[]" );
		test.accept( "-1", "[]" );
		test.accept( "foobar", "[]" );
		test.accept( "foo1bar", "[]" );

		test.accept( "1", "[1]" );
		test.accept( "   1\t", "[1]" );
		test.accept( "1234", "[1234]" );
		test.accept( "1,2,3", "[1, 2, 3]" );
		test.accept( " 1 ,\n2,3\t", "[1, 2, 3]" );
		test.accept( "3,2,1", "[1, 2, 3]" );
		test.accept( "0-4", "[0, 1, 2, 3, 4]" );
		test.accept( "0-2,5,blah,8-9", "[0, 1, 2, 5, 8, 9]" );
	}

	/**
	 * Tests index property access
	 */
	@Test
	void indexProperty() {
		BiConsumer<String, String> test = ( in, out ) -> {
			Mdl mdl = new Mdl();
			Stream.of( "abcdefghijklmnopqrstuvwxyz".split( "" ) )
					.forEach( a -> mdl.withFlows( a + " []" ) );
			Filter f = new Filter( mdl );
			f.indices( Stream.of( in.split( "," ) )
					.filter( s -> !s.isEmpty() )
					.map( Integer::valueOf )
					.collect( toSet() ) );
			assertEquals(
					out,
					f.property( FilterOptions.INDICES ),
					"for " + in );
		};

		test.accept( "", "" );
		test.accept( "1", "1" );
		test.accept( "1,2", "1,2" );
		test.accept( "1,3", "1,3" );
		test.accept( "1,2,3", "1-3" );
		test.accept( "1,2,3,4,7,8,9", "1-4,7-9" );
		test.accept( "1,2,3,4,6,8,9,10,11,12", "1-4,6,8-12" );
	}

	/**
	 * Tests tag property access
	 */
	@Test
	void tagProperty() {
		Filter f = new Filter( new Mdl() );
		assertEquals( null, f.property( FilterOptions.INCLUDE_TAGS ) );
		assertEquals( null, f.property( FilterOptions.EXCLUDE_TAGS ) );

		f.includedTags( Stream.of( "abc", "def" ).collect( toSet() ) );
		f.excludedTags( Stream.of( "ghi", "jkl" ).collect( toSet() ) );

		assertEquals( "abc,def", f.property( FilterOptions.INCLUDE_TAGS ) );
		assertEquals( "ghi,jkl", f.property( FilterOptions.EXCLUDE_TAGS ) );
	}
}
