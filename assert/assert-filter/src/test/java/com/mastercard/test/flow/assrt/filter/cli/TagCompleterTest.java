package com.mastercard.test.flow.assrt.filter.cli;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.impl.completer.ArgumentCompleter.ArgumentLine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Exercises tag completion behaviour.
 */
@Tag("cli")
@SuppressWarnings("static-method")
class TagCompleterTest extends AbstractFilterTest {

	/**
	 * Shows that tags can be tab-completed
	 */
	@Test
	void tagCompletion() {

		Mdl mdl = new Mdl().withFlows(
				"first flow [foo, bar]",
				"second flow [bar, baz]",
				"third flow [baz, oof]" );

		// run the cli
		new FilterCliHarness()
				.expect( " tags are displayed", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz foo oof                                                              │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "+f\t\n" )
				.expect( "foo has been tab-completed and included", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz oof                                                                  │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ foo                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "-o\t\n" )
				.expect( "oof has been tab-completed and excluded", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz                                                                      │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ foo                                                                          │\n"
						+ "├─ Excluded ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ oof                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "\n\n" )
				.expectFlowConstruction( "no flow access at all",
						false )
				.on( mdl )
				.expectResults(
						"first flow [bar, foo]" );
	}

	/**
	 * Shows that when you trigger completion on an empty line, <code>+</code> and
	 * <code>-</code> are offered.
	 */
	@Test
	void fromEmpty() {
		Model mdl = new Mdl().withFlows( "a [f, g, h]" );
		Filter filter = new Filter( mdl );
		TagCompleter tc = new TagCompleter( filter );

		test( tc, "", "+ -" );
	}

	/**
	 * Shows that tags are offered on a <code>+</code>
	 */
	@Test
	void fromPlus() {

		Model mdl = new Mdl().withFlows( "a [f, g, h]" );
		Filter filter = new Filter( mdl );
		TagCompleter tc = new TagCompleter( filter );

		test( tc, "+", "+f +g +h" );
	}

	/**
	 * Shows that tags that are already in the filters are not offered for
	 * completion
	 */
	@Test
	void alreadyFiltered() {

		Model mdl = new Mdl().withFlows( "a [pre_inc, pre_exc, pre_pst]" );
		Filter filter = new Filter( mdl )
				.includedTags( singleton( "pre_inc" ) )
				.excludedTags( singleton( "pre_exc" ) );
		TagCompleter tc = new TagCompleter( filter );

		test( tc, "+pre_", "+pre_exc +pre_pst" );
		test( tc, "-pre_", "-pre_inc -pre_pst" );
	}

	private static void test( TagCompleter tc, String input, String expected ) {

		List<Candidate> candidates = new ArrayList<>();
		tc.complete( null, new ArgumentLine( input, 0 ), candidates );

		assertEquals( expected, candidates.stream()
				.map( Candidate::displ )
				.collect( joining( " " ) ) );
	}
}
