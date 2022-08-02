package com.mastercard.test.flow.assrt.filter.gui;

import java.awt.GraphicsEnvironment;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import com.mastercard.test.flow.assrt.filter.gui.FilterGuiHarness.FlowList;
import com.mastercard.test.flow.assrt.filter.gui.FilterGuiHarness.TagList;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;
import com.mastercard.test.flow.report.QuietFiles;

/**
 * Exercises {@link FilterGui}
 */
@Tag("gui")
@SuppressWarnings("static-method")
@DisabledIf("isHeadless")
class FilterGuiTest {

	/**
	 * Called by the {@link DisabledIf} annotation on the class
	 *
	 * @return true if we're running in an environment where these tests are
	 *         unlikely to thrive
	 */
	static boolean isHeadless() {
		return GraphicsEnvironment.isHeadless();
	}

	/**
	 * The filter gui changes a button when there are historic reports in the
	 * artifact directory, so let's reset to a known state before testing
	 */
	@BeforeEach
	void deleteReports() {
		QuietFiles.recursiveDelete( Paths.get( "target", "mctf" ) );
	}

	/**
	 * You can abort filter editing by closing the window
	 */
	@Test
	void earlyExit() {
		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]" );

		new FilterGuiHarness()
				.expect( "interface is shown",
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
				.close()
				.expectFlowAccess( "No flows displayed in gui", false )
				.on( mdl )
				.expectFilterResults(
						"first [bar, foo]",
						"second [bar, baz]",
						"third [baz, oof]" );
	}

	/**
	 * Buttons are configured such that you can just hit enter twice
	 */
	@Test
	void keyboard() {
		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]" );

		new FilterGuiHarness()
				.expect( "interface is shown",
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
				.expectFlowAccess( "No flows displayed in gui yet", false )
				.hitEnter()
				.expectFlowAccess( "Flows displayed", true )
				.expect( "Flows are built, run is enabled",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  bar     │┊ _ ┊│          ││            │┊ _ ┊│  first | bar … │║     ║",
						"│  baz     │└┈┈┈┘└──────────┘│            │┊   ┊│  second | bar… │║     ║",
						"│  foo     │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│  third | baz … │║     ║",
						"│  oof     │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║     ║",
						"┊           Reset           ┊┊               Reset               ┊║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═════╝" )
				.hitEnter()
				.on( mdl )
				.expectFilterResults(
						"first [bar, foo]",
						"second [bar, baz]",
						"third [baz, oof]" );
	}

	/**
	 * A straight path through the interface by clicking the buttons
	 */
	@Test
	void noOp() {
		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]" );

		new FilterGuiHarness()
				.expect( "interface is shown",
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
				.expectFlowAccess( "No flows displayed in gui yet", false )
				.buildFlows()
				.expectFlowAccess( "Flows displayed", true )
				.expect( "Flows are built, run is enabled",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  bar     │┊ _ ┊│          ││            │┊ _ ┊│  first | bar … │║     ║",
						"│  baz     │└┈┈┈┘└──────────┘│            │┊   ┊│  second | bar… │║     ║",
						"│  foo     │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│  third | baz … │║     ║",
						"│  oof     │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║     ║",
						"┊           Reset           ┊┊               Reset               ┊║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═════╝" )
				.run()
				.on( mdl )
				.expectFilterResults(
						"first [bar, foo]",
						"second [bar, baz]",
						"third [baz, oof]" );
	}

	/**
	 * Putting it all together
	 */
	@Test
	void combined() {
		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]",
				"fourth [foo, bar]" );

		new FilterGuiHarness()
				.selectTags( TagList.AVAILABLE, "bar" )
				.moveTo( TagList.INCLUDED )
				.selectTags( TagList.AVAILABLE, "baz" )
				.moveTo( TagList.EXCLUDED )
				.expect( "tag state",
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
				.expectFlowAccess( "No flows displayed in gui yet", false )
				.buildFlows()
				.expectFlowAccess( "Flows displayed", true )
				.expect( "flow population",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  foo     │┊ _ ┊│  bar     ││            │┊ _ ┊│  first | foo   │║     ║",
						"│  oof     │└┈┈┈┘└──────────┘│            │┊   ┊│  fourth | foo  │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│  baz     ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"╔═══════════════════════════╗┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║     ║",
						"║           Reset           ║┊               Reset               ┊║     ║",
						"╚═══════════════════════════╝└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═════╝" )
				.selectFlows( FlowList.ENABLED, "first | foo" )
				.disable()
				.expect( "final state",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  foo     │┊ _ ┊│  bar     ││  first | … │┊ _ ┊│  fourth | foo  │║     ║",
						"│  oof     │└┈┈┈┘└──────────┘│            │┊   ┊│                │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│  baz     ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"╔═══════════════════════════╗╔═══════════════════════════════════╗║     ║",
						"║           Reset           ║║               Reset               ║║     ║",
						"╚═══════════════════════════╝╚═══════════════════════════════════╝╚═════╝" )
				.run()
				.on( mdl )
				.expectFilterResults(
						"fourth [bar, foo]" );
	}
}
