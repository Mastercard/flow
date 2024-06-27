package com.mastercard.test.flow.assrt.filter.cli;

import java.awt.GraphicsEnvironment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.FilterOptions;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Exercises the {@link FilterCli}
 */
@Tag("cli")
@SuppressWarnings("static-method")

class FilterCliTest extends AbstractFilterTest {

	/**
	 * Exercises the conditions under which the CLI will be activated
	 */
	@Test
	void requested() {
		try {
			FilterOptions.FILTER_UPDATE.set( "foo" );
			Assertions.assertFalse( FilterCli.requested(), "unrecognised value" );

			FilterOptions.FILTER_UPDATE.set( "cli" );
			Assertions.assertTrue( FilterCli.requested(), "specifically provoked" );

			FilterOptions.FILTER_UPDATE.set( "false" );
			Assertions.assertFalse( FilterCli.requested(), "updates disabled" );

			// Ideally we'd be asserting against a static value, but we don't control the
			// environment that the test runs in and mocking a static call feels a bit
			// drastic for what we're trying to show
			FilterOptions.FILTER_UPDATE.set( "true" );
			Assertions.assertEquals( GraphicsEnvironment.isHeadless(), FilterCli.requested(),
					"When update is generally requested, cli is invoked when"
							+ " the environment does not allow a gui" );
		}
		finally {
			FilterOptions.FILTER_UPDATE.clear();
		}
	}

	/**
	 * We accept the empty filter and so end up with all {@link Flow}s
	 */
	@Test
	void noop() {
		Mdl mdl = new Mdl().withFlows(
				"first flow [foo, bar]",
				"second flow [bar, baz]",
				"third flow [baz, oof]" );

		// run the cli
		new FilterCliHarness()
				.expect( "tags are displayed", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz foo oof                                                              │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "No flow access yet",
						false )
				// proceed to index phase
				.input( "\n" )
				.expect( "flows and tags are displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow bar foo                                                         │\n"
						+ "│ 2 second flow bar baz                                                        │\n"
						+ "│ 3 third flow baz oof                                                         │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz foo oof                                                              │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "we're now displaying flows, so we have to construct them",
						true )
				// enter nothing
				.input( "\n" )
				.on( mdl )
				.expectResults(
						"first flow [bar, foo]",
						"second flow [bar, baz]",
						"third flow [baz, oof]" );
	}

	/**
	 * We filter for flows that have a tag
	 */
	@Test
	void tagInclude() {
		Mdl mdl = new Mdl().withFlows(
				"first flow [foo, bar]",
				"second flow [bar, baz]",
				"third flow [baz, oof]" );

		// run the cli
		new FilterCliHarness()
				.expect( "tags are displayed", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar baz foo oof                                                              │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "+bar\n" )
				.expect( "bar is displayed in include set", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz foo oof                                                                  │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "No flow access yet",
						false )
				// proceed to index phase
				.input( "\n" )
				.expect( "flows and tags are displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow foo                                                             │\n"
						+ "│ 2 second flow baz                                                            │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz foo                                                                      │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "we're now displaying flows, so we have to construct them",
						true )
				// enter nothing
				.input( "\n" )
				.on( mdl )
				.expectResults(
						"first flow [bar, foo]",
						"second flow [bar, baz]" );
	}

	/**
	 * We filter for flows that don't have a tag
	 */
	@Test
	void tagExclude() {
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
				.input( "-bar\n" )
				.expect( "bar is displayed in exclude set", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz foo oof                                                                  │\n"
						+ "├─ Excluded ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "No flow access yet",
						false )
				// proceed to index phase
				.input( "\n" )
				.expect( "flows and tags are displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 third flow baz oof                                                         │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz oof                                                                      │\n"
						+ "├─ Excluded ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "we're now displaying flows, so we have to construct them",
						true )
				.input( "\n" )
				.on( mdl )
				.expectResults(
						"third flow [baz, oof]" );
	}

	/**
	 * We filter for flows that do and don't have a tag
	 */
	@Test
	void tagInExclude() {

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
				.input( "+bar\n" )
				.expect( "bar is displayed in include set", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz foo oof                                                                  │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "-baz\n" )
				.expect( "baz is displayed in exclude set", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ foo oof                                                                      │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "├─ Excluded ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ baz                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "No flow access yet",
						false )
				// proceed to index phase
				.input( "\n" )
				.expect( "flows and tags are displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow foo                                                             │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ foo                                                                          │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "├─ Excluded ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ baz                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "we're now displaying flows, so we have to construct them",
						true )
				.input( "\n" )
				.on( mdl )
				.expectResults(
						"first flow [bar, foo]" );
	}

	/**
	 * We filter for a tag, then an index, then we change the tags which clears the
	 * index
	 */
	@Test
	void tagIndextag() {

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
				.input( "+bar\n" )
				.expect( "bar is displayed in include set", ""
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz foo oof                                                                  │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "No flow access yet",
						false )
				// proceed to index phase
				.input( "\n" )
				.expect( "two flows are displayed", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 first flow foo                                                             │\n"
						+ "│ 2 second flow baz                                                            │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz foo                                                                      │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.expectFlowConstruction( "we're now displaying flows, so we have to construct them",
						true )
				.input( "+1\n" )
				.expect( "one flow is chosen", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "├─ Disabled ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ 2 second flow baz                                                            │\n"
						+ "├─ Enabled ────────────────────────────────────────────────────────────────────┤\n"
						+ "│ 1 first flow foo                                                             │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ baz foo                                                                      │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ bar                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "-bar +baz\n" )
				.expect( "index choice is cleared", ""
						+ "┌─ Flows ──────────────────────────────────────────────────────────────────────┐\n"
						+ "│ 1 second flow bar                                                            │\n"
						+ "│ 2 third flow oof                                                             │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘\n"
						+ "┌─ Tags ───────────────────────────────────────────────────────────────────────┐\n"
						+ "│ bar oof                                                                      │\n"
						+ "├─ Included ───────────────────────────────────────────────────────────────────┤\n"
						+ "│ baz                                                                          │\n"
						+ "└──────────────────────────────────────────────────────────────────────────────┘",
						"> " )
				.input( "\n" )
				.on( mdl )
				.expectResults(
						"second flow [bar, baz]",
						"third flow [baz, oof]" );
	}

	/**
	 * Throws up a cli instance for you to fiddle with for manual testing
	 */
	@Test()
	@EnabledIfSystemProperty(named = "manual", matches = "true")
	void manual() {
		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]",
				"fourth [foo, bar]" );
		Filter filter = new Filter( mdl );
		FilterCli cli = new FilterCli( filter );
		cli.blockForInput();
	}
}
