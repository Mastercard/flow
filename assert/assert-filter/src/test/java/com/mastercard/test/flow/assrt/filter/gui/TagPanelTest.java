package com.mastercard.test.flow.assrt.filter.gui;

import java.awt.GraphicsEnvironment;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import com.mastercard.test.flow.assrt.filter.gui.FilterGuiHarness.TagList;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Test cases that focus on {@link TagPanel} behaviours
 */
@Tag("gui")
@SuppressWarnings("static-method")
@DisabledIf("isHeadless")
class TagPanelTest {

	/**
	 * @return true if we're running in an environment where these tests are
	 *         unlikely to thrive
	 */
	static boolean isHeadless() {
		return GraphicsEnvironment.isHeadless();
	}

	/**
	 * Shows that the tag list selections are mutually-exclusive, and selecting on a
	 * list enables the appropriate swap buttons
	 */
	@Test
	void selection() {

		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]" );

		new FilterGuiHarness()
				.selectTags( TagList.AVAILABLE, "bar" )
				.moveTo( TagList.INCLUDED )
				.selectTags( TagList.AVAILABLE, "baz" )
				.moveTo( TagList.EXCLUDED )

				.selectTags( TagList.AVAILABLE, "foo" )
				.expect( "we've just clicked it, so it's selected",
						"┌─ avail… ─┐╔═══╗┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│ █foo████ │║ _ ║│  bar     │║       ║┊     ┊",
						"│  oof     │╚═══╝└──────────┘║       ║┊     ┊",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"│          │     ┊    _     ┊║       ║┊     ┊",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘║ Build ║┊ Run ┊",
						"│          │╔═══╗┌─ exclu… ─┐║       ║┊     ┊",
						"│          │║ _ ║│  baz     │║       ║┊     ┊",
						"└──────────┘╚═══╝└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )

				.selectTags( TagList.INCLUDED, "bar" )
				.expect( "av-> in, previous selection cleared",
						"┌─ avail… ─┐╔═══╗┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  foo     │║ _ ║│ █bar████ │║       ║┊     ┊",
						"│  oof     │╚═══╝└──────────┘║       ║┊     ┊",
						"│          │     ╔══════════╗║       ║┊     ┊",
						"│          │     ║    _     ║║       ║┊     ┊",
						"│          │     ╚══════════╝║ Build ║┊ Run ┊",
						"│          │┌┈┈┈┐┌─ exclu… ─┐║       ║┊     ┊",
						"│          │┊ _ ┊│  baz     │║       ║┊     ┊",
						"└──────────┘└┈┈┈┘└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )

				.selectTags( TagList.EXCLUDED, "baz" )
				.expect( "in -> ex, previous selection cleared",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  foo     │┊ _ ┊│  bar     │║       ║┊     ┊",
						"│  oof     │└┈┈┈┘└──────────┘║       ║┊     ┊",
						"│          │     ╔══════════╗║       ║┊     ┊",
						"│          │     ║    _     ║║       ║┊     ┊",
						"│          │     ╚══════════╝║ Build ║┊ Run ┊",
						"│          │╔═══╗┌─ exclu… ─┐║       ║┊     ┊",
						"│          │║ _ ║│ █baz████ │║       ║┊     ┊",
						"└──────────┘╚═══╝└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )

				.selectTags( TagList.AVAILABLE, "foo" )
				.expect( "ex -> av, previous selection cleared",
						"┌─ avail… ─┐╔═══╗┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│ █foo████ │║ _ ║│  bar     │║       ║┊     ┊",
						"│  oof     │╚═══╝└──────────┘║       ║┊     ┊",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"│          │     ┊    _     ┊║       ║┊     ┊",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘║ Build ║┊ Run ┊",
						"│          │╔═══╗┌─ exclu… ─┐║       ║┊     ┊",
						"│          │║ _ ║│  baz     │║       ║┊     ┊",
						"└──────────┘╚═══╝└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )

				.selectTags( TagList.EXCLUDED, "baz" )
				.expect( "av -> ex, previous selection cleared",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  foo     │┊ _ ┊│  bar     │║       ║┊     ┊",
						"│  oof     │└┈┈┈┘└──────────┘║       ║┊     ┊",
						"│          │     ╔══════════╗║       ║┊     ┊",
						"│          │     ║    _     ║║       ║┊     ┊",
						"│          │     ╚══════════╝║ Build ║┊ Run ┊",
						"│          │╔═══╗┌─ exclu… ─┐║       ║┊     ┊",
						"│          │║ _ ║│ █baz████ │║       ║┊     ┊",
						"└──────────┘╚═══╝└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )

				.selectTags( TagList.INCLUDED, "bar" )
				.expect( "ex -> in, previous selection cleared",
						"┌─ avail… ─┐╔═══╗┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  foo     │║ _ ║│ █bar████ │║       ║┊     ┊",
						"│  oof     │╚═══╝└──────────┘║       ║┊     ┊",
						"│          │     ╔══════════╗║       ║┊     ┊",
						"│          │     ║    _     ║║       ║┊     ┊",
						"│          │     ╚══════════╝║ Build ║┊ Run ┊",
						"│          │┌┈┈┈┐┌─ exclu… ─┐║       ║┊     ┊",
						"│          │┊ _ ┊│  baz     │║       ║┊     ┊",
						"└──────────┘└┈┈┈┘└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )

				.selectTags( TagList.AVAILABLE, "foo" )
				.expect( "in -> av, previous selection cleared",
						"┌─ avail… ─┐╔═══╗┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│ █foo████ │║ _ ║│  bar     │║       ║┊     ┊",
						"│  oof     │╚═══╝└──────────┘║       ║┊     ┊",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"│          │     ┊    _     ┊║       ║┊     ┊",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘║ Build ║┊ Run ┊",
						"│          │╔═══╗┌─ exclu… ─┐║       ║┊     ┊",
						"│          │║ _ ║│  baz     │║       ║┊     ┊",
						"└──────────┘╚═══╝└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )
				.expectFlowAccess( "None of that resulted in flows being built",
						false )
				.close()
				.on( mdl );
	}

	/**
	 * Exercises the tag reset button
	 */
	@Test
	void reset() {
		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]" );

		new FilterGuiHarness()
				.selectTags( TagList.AVAILABLE, "bar" )
				.moveTo( TagList.INCLUDED )
				.selectTags( TagList.AVAILABLE, "baz" )
				.moveTo( TagList.EXCLUDED )

				.expect( "tags all over the place, reset enabled",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  foo     │┊ _ ┊│  bar     │║       ║┊     ┊",
						"│  oof     │└┈┈┈┘└──────────┘║       ║┊     ┊",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"│          │     ┊    _     ┊║       ║┊     ┊",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘║ Build ║┊ Run ┊",
						"│          │┌┈┈┈┐┌─ exclu… ─┐║       ║┊     ┊",
						"│          │┊ _ ┊│  baz     │║       ║┊     ┊",
						"└──────────┘└┈┈┈┘└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )

				.resetTags()

				.expect( "back in the box, reset disabled",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  bar     │┊ _ ┊│          │║       ║┊     ┊",
						"│  baz     │└┈┈┈┘└──────────┘║       ║┊     ┊",
						"│  foo     │     ┌┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"│  oof     │     ┊    _     ┊║       ║┊     ┊",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘║ Build ║┊ Run ┊",
						"│          │┌┈┈┈┐┌─ exclu… ─┐║       ║┊     ┊",
						"│          │┊ _ ┊│          │║       ║┊     ┊",
						"└──────────┘└┈┈┈┘└──────────┘║       ║┊     ┊",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"┊           Reset           ┊║       ║┊     ┊",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════╝└┈┈┈┈┈┘" )
				.expectFlowAccess( "None of that resulted in flows being built",
						false )
				.close()
				.on( mdl );
	}

	/**
	 * Shows how the filter input shuffles the tag and flow lists
	 */
	@Test
	void filter() {

		Mdl mdl = new Mdl().withFlows(
				"de [d, e]",
				"hi [h, i]",
				"no [n, o]" );

		new FilterGuiHarness()
				.selectTags( TagList.AVAILABLE, "h", "i" )
				.moveTo( TagList.INCLUDED )
				.selectTags( TagList.AVAILABLE, "n", "o" )
				.moveTo( TagList.EXCLUDED )
				.expect( "unfiltered",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  d       │┊ _ ┊│  h       │║       ║┊     ┊",
						"│  e       │┊   ┊│  i       │║       ║┊     ┊",
						"│          │└┈┈┈┘└──────────┘║       ║┊     ┊",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"│          │     ┊    _     ┊║       ║┊     ┊",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘║ Build ║┊ Run ┊",
						"│          │┌┈┈┈┐┌─ exclu… ─┐║       ║┊     ┊",
						"│          │┊ _ ┊│  n       │║       ║┊     ┊",
						"│          │┊   ┊│  o       │║       ║┊     ┊",
						"└──────────┘└┈┈┈┘└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )
				.filter( "[aeiou]" )
				.expect( "available tags sorted",
						"┌─ Filter ──────────────────────────────────┐",
						"│                   [aeiou]                 │",
						"└───────────────────────────────────────────┘",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐╔═══════╗┌┈┈┈┈┈┐",
						"│  e       │┊ _ ┊│  i       │║       ║┊     ┊",
						"│  d       │┊   ┊│  h       │║       ║┊     ┊",
						"│          │└┈┈┈┘└──────────┘║       ║┊     ┊",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐║       ║┊     ┊",
						"│          │     ┊    _     ┊║       ║┊     ┊",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘║ Build ║┊ Run ┊",
						"│          │┌┈┈┈┐┌─ exclu… ─┐║       ║┊     ┊",
						"│          │┊ _ ┊│  o       │║       ║┊     ┊",
						"│          │┊   ┊│  n       │║       ║┊     ┊",
						"└──────────┘└┈┈┈┘└──────────┘║       ║┊     ┊",
						"╔═══════════════════════════╗║       ║┊     ┊",
						"║           Reset           ║║       ║┊     ┊",
						"╚═══════════════════════════╝╚═══════╝└┈┈┈┈┈┘" )
				.expectFlowAccess( "None of that resulted in flows being built",
						false )
				.close()
				.on( mdl );
	}
}
