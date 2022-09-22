
package com.mastercard.test.flow.assrt.filter.cli;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Exercises details of the tag-choosing phase of the command-line interface
 */
@Tag("cli")
@SuppressWarnings("static-method")
class TagPhaseTest extends AbstractFilterTest {

	/**
	 * Explores the behaviour of tag filtering
	 */
	@Test
	void input() {

		Model mdl = new Mdl().withFlows(
				"a [f, g, h]",
				"b [g, h, i]",
				"c [h, i, j]",
				"d [i, j, k]",
				"e [j, k, l]" );
		Filter filter = new Filter( mdl );
		TagPhase tp = new TagPhase( filter );

		BiConsumer<String, String> test = ( in, out ) -> {
			// reset the filter
			filter.includedTags( Collections.emptySet() )
					.excludedTags( Collections.emptySet() );
			// process input
			tp.next( in );
			// check filtered flows
			Assertions.assertEquals( out,
					filter.flows()
							.map( f -> f.meta().description() )
							.collect( joining( " " ) ),
					"For " + in );
		};

		test.accept( "", "a b c d e" );
		test.accept( "+f", "a" );
		test.accept( "+g", "a b" );
		test.accept( "-h", "d e" );
		test.accept( "+f +l", "" );
		test.accept( "+i -k", "b c" );

		test.accept( "+f -f", "a b c d e" );
		test.accept( "+f -f -f", "b c d e" );
		test.accept( "-f +f", "a b c d e" );
		test.accept( "-f +f +f", "a" );

		test.accept( "nosuchtag", "a b c d e" );
		test.accept( "+nosuchtag", "a b c d e" );
		test.accept( "-nosuchtag", "a b c d e" );
	}

	/**
	 * Explores tag display
	 */
	@Test
	void output() {

		Model mdl = new Mdl().withFlows(
				"a [f, g, h]",
				"b [g, h, i]",
				"c [h, i, j]",
				"d [i, j, k]",
				"e [j, k, l]" );
		Filter filter = new Filter( mdl );
		TagPhase tp = new TagPhase( filter );

		BiConsumer<String, String> test = ( in, out ) -> {
			// reset the filter
			filter.includedTags( Collections.emptySet() )
					.excludedTags( Collections.emptySet() );
			// process input
			tp.next( in );

			// check display
			Cli cli = new Cli( 40 );
			tp.render( cli );
			Assertions.assertEquals( out,
					cli.content().toString().trim(),
					"For " + in );
		};

		test.accept( "", ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ f g h i j k l                        │\n"
				+ "└──────────────────────────────────────┘" );
		test.accept( "+f", ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ g h i j k l                          │\n"
				+ "├─ Included ───────────────────────────┤\n"
				+ "│ f                                    │\n"
				+ "└──────────────────────────────────────┘" );
		test.accept( "+f +k", ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ g h i j l                            │\n"
				+ "├─ Included ───────────────────────────┤\n"
				+ "│ f k                                  │\n"
				+ "└──────────────────────────────────────┘" );
		test.accept( "-j", ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ f g h i k l                          │\n"
				+ "├─ Excluded ───────────────────────────┤\n"
				+ "│ j                                    │\n"
				+ "└──────────────────────────────────────┘" );
		test.accept( "+l -h", ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ f g i j k                            │\n"
				+ "├─ Included ───────────────────────────┤\n"
				+ "│ l                                    │\n"
				+ "├─ Excluded ───────────────────────────┤\n"
				+ "│ h                                    │\n"
				+ "└──────────────────────────────────────┘" );
		test.accept( "+l -h reset", ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ f g h i j k l                        │\n"
				+ "└──────────────────────────────────────┘" );
		test.accept( "foo", ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ f g h i j k l                        │\n"
				+ "└──────────────────────────────────────┘\n"
				+ "┌─ Error ──────────────────────────────┐\n"
				+ "│ Unrecognised input 'foo'             │\n"
				+ "│ Invoke 'help' to see available       │\n"
				+ "│ commands                             │\n"
				+ "└──────────────────────────────────────┘" );
	}

	/**
	 * Demonstrates rendering of the empty set
	 */
	@Test
	void empty() {
		Model mdl = new Mdl().withFlows( "a []" );
		Filter filter = new Filter( mdl );
		TagPhase tp = new TagPhase( filter );
		Cli cli = new Cli( 40 );
		tp.render( cli );
		assertEquals( ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "└──────────────────────────────────────┘",
				cli.content().toString().trim() );
	}

	/**
	 * Show that long lists of tags are line-wrapped
	 */
	@Test
	void wrapping() {
		Model mdl = new Mdl()
				.withFlows( "flw [ "
						+ IntStream.range( 1, 80 )
								.mapToObj( i -> String.format( "%02d", i ) )
								.collect( joining( ", " ) )
						+ "]" );

		Filter filter = new Filter( mdl );
		TagPhase tp = new TagPhase( filter );
		Cli cli = new Cli( 40 );
		tp.render( cli );
		assertEquals( ""
				+ "┌─ Tags ───────────────────────────────┐\n"
				+ "│ 01 02 03 04 05 06 07 08 09 10 11 12  │\n"
				+ "│ 13 14 15 16 17 18 19 20 21 22 23 24  │\n"
				+ "│ 25 26 27 28 29 30 31 32 33 34 35 36  │\n"
				+ "│ 37 38 39 40 41 42 43 44 45 46 47 48  │\n"
				+ "│ 49 50 51 52 53 54 55 56 57 58 59 60  │\n"
				+ "│ 61 62 63 64 65 66 67 68 69 70 71 72  │\n"
				+ "│ 73 74 75 76 77 78 79                 │\n"
				+ "└──────────────────────────────────────┘",
				cli.content().toString().trim() );
	}

}
