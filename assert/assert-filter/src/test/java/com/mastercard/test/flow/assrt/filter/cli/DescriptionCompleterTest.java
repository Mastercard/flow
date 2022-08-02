package com.mastercard.test.flow.assrt.filter.cli;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.jline.reader.Candidate;
import org.jline.reader.impl.completer.ArgumentCompleter.ArgumentLine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Exercises the bits of {@link DescriptionCompleter} that are difficult to
 * reach via the dumb terminal that we use for most testing
 */
@Tag("cli")
@SuppressWarnings("static-method")
class DescriptionCompleterTest extends AbstractFilterTest {

	/**
	 * Shows that when you trigger completion on an empty line, <code>/</code> and
	 * <code>?</code> are offered.
	 */
	@Test
	void fromEmpty() {
		Model mdl = new Mdl().withFlows( "a [f, g, h]" );
		Filter filter = new Filter( mdl );
		DescriptionCompleter tc = new DescriptionCompleter( filter );

		test( tc, "", "/ ?" );
	}

	/**
	 * Shows that descriptions are offered on a <code>/</code> and <code>?</code>
	 */
	@Test
	void fromPrefix() {

		Model mdl = new Mdl().withFlows(
				"abc []", "bcd []", "cde []" );
		Filter filter = new Filter( mdl );
		DescriptionCompleter tc = new DescriptionCompleter( filter );

		test( tc, "/", "/abc /bcd /cde" );
		test( tc, "?", "?abc ?bcd ?cde" );
	}

	/**
	 * Shows that only descriptions already in the included set are suggested
	 */
	@Test
	void alreadyFiltered() {

		Model mdl = new Mdl().withFlows( "abc [pre_inc]", "bcd []", "cde []" );
		Filter filter = new Filter( mdl )
				.includedTags( singleton( "pre_inc" ) );
		DescriptionCompleter tc = new DescriptionCompleter( filter );

		test( tc, "/", "/abc" );
		test( tc, "?", "?abc" );
	}

	/**
	 * Shows that completions are offered in a sensible order - matches closer to
	 * the start of the description are preferred. Jline ignores this and presents a
	 * sorted list, but, hey, at least we tried.
	 */
	@Test
	void order() {
		Model mdl = new Mdl().withFlows(
				"abc []", "bcd []", "cde []" );
		Filter filter = new Filter( mdl );
		DescriptionCompleter tc = new DescriptionCompleter( filter );

		test( tc, "/c", "/cde /bcd /abc" );
		test( tc, "?c", "?cde ?bcd ?abc" );
	}

	/**
	 * Shows that regexes can be used
	 */
	@Test
	void regex() {
		Model mdl = new Mdl().withFlows(
				"abc []", "a1c []" );
		Filter filter = new Filter( mdl );
		DescriptionCompleter tc = new DescriptionCompleter( filter );

		test( tc, "/a[0-9]c", "/a1c" );
		test( tc, "?a[0-9]c", "?a1c" );
	}

	/**
	 * Explores matching behaviour
	 */
	@Test
	void offer() {
		BiConsumer<String, String> test = ( in, out ) -> {
			Model mdl = new Mdl().withFlows(
					"abc []", "bcd []", "cde []" );
			Filter filter = new Filter( mdl );
			ArrayList<String> errors = new ArrayList<>();
			assertTrue( DescriptionCompleter.offer( in, filter, errors ) );
			assertEquals( out, filter.indices().toString(), "for " + in );
		};

		test.accept( "/a", "[0]" );
		test.accept( "/b", "[0, 1]" );
		test.accept( "/c", "[]" ); // like Buddy Pine says: when everyone is special, no-one is
		test.accept( "/d", "[1, 2]" );
		test.accept( "/e", "[2]" );

		test.accept( "?a", "[1, 2]" );
		test.accept( "?b", "[2]" );
		test.accept( "?c", "[]" );
		test.accept( "?d", "[0]" );
		test.accept( "?e", "[0, 1]" );
	}

	/**
	 * Matching is stateful - only those flows that are currently selected are
	 * searched
	 */
	@Test
	void refine() {
		Model mdl = new Mdl().withFlows(
				"abc []", "bcd []", "cde []" );
		Filter filter = new Filter( mdl );
		ArrayList<String> errors = new ArrayList<>();
		assertEquals( "[]", filter.indices().toString(), "initial" );
		DescriptionCompleter.offer( "/b", filter, errors );
		assertEquals( "[0, 1]", filter.indices().toString(), "after /b" );
		DescriptionCompleter.offer( "/d", filter, errors );
		assertEquals( "[1]", filter.indices().toString(), "after /d" );
		DescriptionCompleter.offer( "?a", filter, errors );
		assertEquals( "[1]", filter.indices().toString(), "after ?a" );
		DescriptionCompleter.offer( "?b", filter, errors );
		assertEquals( "[]", filter.indices().toString(), "after ?b" );
	}

	/**
	 * Shows that malformed regular expressions are ignored
	 */
	@Test
	void badRegex() {
		Model mdl = new Mdl().withFlows(
				"abc []", "bcd []", "cde []" );
		Filter filter = new Filter( mdl );
		DescriptionCompleter tc = new DescriptionCompleter( filter );

		test( tc, "/[}", "" );
		test( tc, "?[}", "" );
	}

	private static void test( DescriptionCompleter tc, String input, String expected ) {
		List<Candidate> candidates = new ArrayList<>();
		tc.complete( null, new ArgumentLine( input, 0 ), candidates );

		assertEquals( expected, candidates.stream()
				.map( Candidate::displ )
				.collect( joining( " " ) ) );
	}
}
