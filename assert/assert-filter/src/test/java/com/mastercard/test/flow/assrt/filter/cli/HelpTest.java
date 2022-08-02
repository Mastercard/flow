package com.mastercard.test.flow.assrt.filter.cli;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Exercises the help provision behaviour
 */
@SuppressWarnings("static-method")
class HelpTest extends AbstractFilterTest {

	/**
	 * We trigger the help text in both phases
	 */
	@Test
	void help() {
		Mdl mdl = new Mdl().withFlows(
				"first flow [foo, bar]" );

		// run the cli
		new FilterCliHarness()
				.expect( "tags are displayed", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar foo                                                                      │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				// autocomplete the help command
				.input( "he\t\n" )
				.expect( "tag-phase help is displayed", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar foo                                                                      │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Help: Tag choice ───────────────────────────────────────────────────────────┐\n"
						+ "│ Add tag filters to minimise the construction of flows that you don't want to │\n"
						+ "│ exercise                                                                     │\n"
						+ "│  +<tag> : Adds an inclusive tag filter                                       │\n"
						+ "│  -<tag> : Adds an exclusive tag filter                                       │\n"
						+ "│   reset : Clear all filters                                                  │\n"
						+ "│ Only those flows that bear all of the included tags and none of the excluded │\n"
						+ "│ tags will be exercised.                                                      │\n"
						+ "│ Provide no command to build the tagged flows and proceed                     │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				// add tag filter
				.input( "+foo\n" )
				.expect( "help text goes away again", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar                                                                          │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ foo                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "No flow access yet",
						false )
				// proceed to index phase
				.input( "\n" )
				.expect( "flow is displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow bar                                                             │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar                                                                          │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ foo                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "we're now displaying flows, so we have to construct them",
						true )
				// autocomplete the help command
				.input( "he\t\n" )
				.expect( "tag-phase help is displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow bar                                                             │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar                                                                          │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ foo                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Help: Flow choice ──────────────────────────────────────────────────────────┐\n"
						+ "│ Add tag and flow filters to control which flows are exercised. Flows can be  │\n"
						+ "│ selected based on their index or description text.                           │\n"
						+ "│       +<tag_name> : Adds an inclusive tag filter                             │\n"
						+ "│       -<tag_name> : Adds an exclusive tag filter                             │\n"
						+ "│          +<index> : Selects a single flow                                    │\n"
						+ "│          -<index> : Deselects a single flow                                  │\n"
						+ "│  +<index>-<index> : Selects a range of flows                                 │\n"
						+ "│  -<index>-<index> : Deselects a range of flows                               │\n"
						+ "│          /<regex> : Selects flows whose descriptions match the expression    │\n"
						+ "│          ?<regex> : Deselects flows whose descriptions match the expression  │\n"
						+ "│             reset : Clear all filters                                        │\n"
						+ "│         tag_reset : Clear tag choices                                        │\n"
						+ "│        flow_reset : Clear flow choices                                       │\n"
						+ "│             fails : Exercise historic failures                               │\n"
						+ "│ Provide no command to exercise the selected flows                            │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				// choose a flow
				.input( "+1\t\n" )
				.expect( "help text is hidden again", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow                                                                 │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar foo                                                                      │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "\n\n" )
				.on( mdl );
	}
}
