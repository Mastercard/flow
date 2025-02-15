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
				.expect( "tags are displayed", """
						┌─ Tags ───────────────────────────────────────────────────────────────────────┐
						│ bar foo                                                                      │
						└──────────────────────────────────────────────────────────────────────────────┘""",
						"> " )
				// autocomplete the help command
				.input( "he\t\n" )
				.expect( "tag-phase help is displayed", """
						┌─ Tags ───────────────────────────────────────────────────────────────────────┐
						│ bar foo                                                                      │
						└──────────────────────────────────────────────────────────────────────────────┘
						┌─ Help: Tag choice ───────────────────────────────────────────────────────────┐
						│ Add tag filters to minimise the construction of flows that you don't want to │
						│ exercise                                                                     │
						│  +<tag> : Adds an inclusive tag filter                                       │
						│  -<tag> : Adds an exclusive tag filter                                       │
						│   reset : Clear all filters                                                  │
						│ Only those flows that bear all of the included tags and none of the excluded │
						│ tags will be exercised.                                                      │
						│ Provide no command to build the tagged flows and proceed                     │
						└──────────────────────────────────────────────────────────────────────────────┘""",
						"> " )
				// add tag filter
				.input( "+foo\n" )
				.expect( "help text goes away again", """
						┌─ Tags ───────────────────────────────────────────────────────────────────────┐
						│ bar                                                                          │
						├─ Included ───────────────────────────────────────────────────────────────────┤
						│ foo                                                                          │
						└──────────────────────────────────────────────────────────────────────────────┘""",
						"> " )
				.expectFlowConstruction( "No flow access yet",
						false )
				// proceed to index phase
				.input( "\n" )
				.expect( "flow is displayed", """
						┌─ Flows ──────────────────────────────────────────────────────────────────────┐
						│ 1 first flow bar                                                             │
						└──────────────────────────────────────────────────────────────────────────────┘
						┌─ Tags ───────────────────────────────────────────────────────────────────────┐
						│ bar                                                                          │
						├─ Included ───────────────────────────────────────────────────────────────────┤
						│ foo                                                                          │
						└──────────────────────────────────────────────────────────────────────────────┘""",
						"> " )
				.expectFlowConstruction( "we're now displaying flows, so we have to construct them",
						true )
				// autocomplete the help command
				.input( "he\t\n" )
				.expect( "tag-phase help is displayed", """
						┌─ Flows ──────────────────────────────────────────────────────────────────────┐
						│ 1 first flow bar                                                             │
						└──────────────────────────────────────────────────────────────────────────────┘
						┌─ Tags ───────────────────────────────────────────────────────────────────────┐
						│ bar                                                                          │
						├─ Included ───────────────────────────────────────────────────────────────────┤
						│ foo                                                                          │
						└──────────────────────────────────────────────────────────────────────────────┘
						┌─ Help: Flow choice ──────────────────────────────────────────────────────────┐
						│ Add tag and flow filters to control which flows are exercised. Flows can be  │
						│ selected based on their index or description text.                           │
						│       +<tag_name> : Adds an inclusive tag filter                             │
						│       -<tag_name> : Adds an exclusive tag filter                             │
						│          +<index> : Selects a single flow                                    │
						│          -<index> : Deselects a single flow                                  │
						│  +<index>-<index> : Selects a range of flows                                 │
						│  -<index>-<index> : Deselects a range of flows                               │
						│          /<regex> : Selects flows whose descriptions match the expression    │
						│          ?<regex> : Deselects flows whose descriptions match the expression  │
						│             reset : Clear all filters                                        │
						│         tag_reset : Clear tag choices                                        │
						│        flow_reset : Clear flow choices                                       │
						│             fails : Exercise historic failures                               │
						│ Provide no command to exercise the selected flows                            │
						└──────────────────────────────────────────────────────────────────────────────┘""",
						"> " )
				// choose a flow
				.input( "+1\t\n" )
				.expect( "help text is hidden again", """
						┌─ Flows ──────────────────────────────────────────────────────────────────────┐
						│ 1 first flow                                                                 │
						└──────────────────────────────────────────────────────────────────────────────┘
						┌─ Tags ───────────────────────────────────────────────────────────────────────┐
						├─ Included ───────────────────────────────────────────────────────────────────┤
						│ bar foo                                                                      │
						└──────────────────────────────────────────────────────────────────────────────┘""",
						"> " )
				.input( "\n\n" )
				.on( mdl );
	}
}
