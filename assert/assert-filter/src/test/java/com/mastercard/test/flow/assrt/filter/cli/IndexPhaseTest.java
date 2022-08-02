package com.mastercard.test.flow.assrt.filter.cli;

import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Exercises details of the index phase of the command line interface
 */
@Tag("cli")
@SuppressWarnings("static-method")
class IndexPhaseTest extends AbstractFilterTest {

	/**
	 * Explores valid expressions of index ranges and description searches
	 */
	@Test
	void input() {
		Model mdl = new Mdl().withFlows(
				"a []", "b []", "c []", "d []", "e []" );
		Filter filter = new Filter( mdl );
		IndexPhase idxp = new IndexPhase( filter );

		BiConsumer<String, String> test = ( in, out ) -> {
			// reset the filter
			filter.indices( Collections.emptySet() );
			// process input
			idxp.next( in );
			// check filtered flows
			Assertions.assertEquals( out,
					filter.flows()
							.map( f -> f.meta().description() )
							.collect( joining( " " ) ),
					"For " + in );
		};

		test.accept( "", "a b c d e" );
		test.accept( "1", "a" );
		test.accept( "+2", "b" );
		test.accept( "-3", "a b d e" );
		test.accept( "1-3", "a b c" );
		test.accept( "-1-3", "d e" );
		test.accept( "3-", "c d e" );
		test.accept( "-3-", "a b" );
		test.accept( "3-1", "a b c" );
		test.accept( "3-1 reset", "a b c d e" );

		test.accept( "-2 +2", "a b c d e" );
		test.accept( "-2 +2 +2", "b" );
		test.accept( "+2 -2", "a b c d e" );
		test.accept( "+2 -2 -2", "a c d e" );

		test.accept( "8", "a b c d e" );
		test.accept( "8-9", "a b c d e" );
		test.accept( "3-9", "c d e" );

		test.accept( "/a", "a" );
		test.accept( "/[a-c]", "a b c" );
		test.accept( "/[^a-c]", "d e" );

		test.accept( "?a", "b c d e" );
		test.accept( "?[a-c]", "d e" );
		test.accept( "?[^a-c]", "a b c" );
	}

	/**
	 * Explores flow display
	 */
	@Test
	void output() {

		Model mdl = new Mdl().withFlows(
				"abc [a, b, c]", "cdef [c, d, e, f]", "ghijk [g, h, i, j, k]" );
		Filter filter = new Filter( mdl );
		IndexPhase idxp = new IndexPhase( filter );

		BiConsumer<String, String> test = ( in, out ) -> {
			// reset the filter
			filter.indices( Collections.emptySet() );
			// process input
			idxp.next( in );

			// check display
			Cli cli = new Cli( 40 );
			idxp.render( cli );
			Assertions.assertEquals( out,
					cli.content().toString().trim(),
					"For " + in );
		};

		test.accept( "", ""
				+ "┌─ Flows ──────────────────────────────┐\n"
				+ "│ 1 abc a b c                          │\n"
				+ "│ 2 cdef c d e f                       │\n"
				+ "│ 3 ghijk g h i j k                    │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ a b c d e f g h i j k                │\n"
				+ "└──────────────────────────────────────┘" );

		test.accept( "+1", ""
				+ "┌─ Flows ──────────────────────────────┐\n"
				+ "├─ Disabled ───────────────────────────┤\n"
				+ "│ 2 cdef c d e f                       │\n"
				+ "│ 3 ghijk g h i j k                    │\n"
				+ "├─ Enabled ────────────────────────────┤\n"
				+ "│ 1 abc a b c                          │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ a b c d e f g h i j k                │\n"
				+ "└──────────────────────────────────────┘" );

		test.accept( "-1", ""
				+ "┌─ Flows ──────────────────────────────┐\n"
				+ "├─ Disabled ───────────────────────────┤\n"
				+ "│ 1 abc a b c                          │\n"
				+ "├─ Enabled ────────────────────────────┤\n"
				+ "│ 2 cdef c d e f                       │\n"
				+ "│ 3 ghijk g h i j k                    │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ a b c d e f g h i j k                │\n"
				+ "└──────────────────────────────────────┘" );

		test.accept( "-3", ""
				+ "┌─ Flows ──────────────────────────────┐\n"
				+ "├─ Disabled ───────────────────────────┤\n"
				+ "│ 3 ghijk g h i j k                    │\n"
				+ "├─ Enabled ────────────────────────────┤\n"
				+ "│ 1 abc a b c                          │\n"
				+ "│ 2 cdef c d e f                       │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ a b c d e f g h i j k                │\n"
				+ "└──────────────────────────────────────┘" );

		test.accept( "blah", ""
				+ "┌─ Flows ──────────────────────────────┐\n"
				+ "│ 1 abc a b c                          │\n"
				+ "│ 2 cdef c d e f                       │\n"
				+ "│ 3 ghijk g h i j k                    │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ a b c d e f g h i j k                │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Error ──────────────────────────────┐\n"
				+ "│ Unrecognised input 'blah'            │\n"
				+ "│ Invoke 'help' to see available       │\n"
				+ "│ commands                             │\n"
				+ "└──────────────────────────────────────┘" );
	}

	/**
	 * Exercises the display of wrapped tags
	 */
	@Test
	void wrappedTags() {
		Model mdl = new Mdl().withFlows(
				"abc [this, flow, has, so, many, tags, more, than, will, fit, on, one, "
						+ "line, of, the, interface, hence, they, will, be, wrapped, onto, multiple, lines]" );
		Filter filter = new Filter( mdl );
		IndexPhase idxp = new IndexPhase( filter );
		Cli cli = new Cli( 40 );
		idxp.render( cli );
		Assertions.assertEquals( ""
				+ "┌─ Flows ──────────────────────────────┐\n"
				+ "│ 1 abc be fit flow has hence          │\n"
				+ "│          interface line lines many   │\n"
				+ "│          more multiple of on one onto │\n"
				+ "│          so tags than the they this  │\n"
				+ "│          will wrapped                │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ be fit flow has hence interface line │\n"
				+ "│ lines many more multiple of on one   │\n"
				+ "│ onto so tags than the they this will │\n"
				+ "│ wrapped                              │\n"
				+ "└──────────────────────────────────────┘",
				cli.content().toString().trim() );
	}

	/**
	 * Shows that ridiculously long descriptions are ellipsised
	 */
	@Test
	void loquacious() {
		Model mdl = new Mdl().withFlows(
				"thisdescriptionisjuststupidimeanlookatititslongerthaneightycharacterstheyreonly"
						+ "supposedtobeawordortwotodisambiguateflowswiththesametags []",
				"thisisanotherreallyreallyreallylongdescriptionbutthistimetheflowalsohastags"
						+ " [these, will, appear, on, the next, line]" );
		Filter filter = new Filter( mdl );
		IndexPhase idxp = new IndexPhase( filter );
		Cli cli = new Cli( 40 );
		idxp.render( cli );
		Assertions.assertEquals( ""
				+ "┌─ Flows ──────────────────────────────┐\n"
				+ "│ 1 thisdescriptionisjuststupidimeanl… │\n"
				+ "│ 2 thisisanotherreallyreallyreallylo… │\n"
				+ "│          appear line on the next     │\n"
				+ "│          these will                  │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ appear line on the next these will   │\n"
				+ "└──────────────────────────────────────┘",
				cli.content().toString().trim() );
	}
}
