package com.mastercard.test.flow.assrt.filter.gui;

import java.awt.GraphicsEnvironment;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import com.mastercard.test.flow.assrt.filter.gui.FilterGuiHarness.FlowList;
import com.mastercard.test.flow.assrt.filter.gui.FilterGuiHarness.TagList;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;
import com.mastercard.test.flow.report.QuietFiles;
import com.mastercard.test.flow.report.Writer;

/**
 * Test cases that focus on {@link FlowPanel} behaviours
 */
@Tag("gui")
@SuppressWarnings("static-method")
@DisabledIf("isHeadless")
public class FlowPanelTest {

	/**
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
	 * Enabling and disabling flows
	 */
	@Test
	void toggle() {
		Mdl mdl = new Mdl().withFlows(
				"first [foo, bar]",
				"second [bar, baz]",
				"third [baz, oof]" );

		new FilterGuiHarness()
				.expectFlowAccess( "No flows built yet", false )
				.buildFlows()
				.expectFlowAccess( "Flows constructed", true )
				.expect( "all flows enabled",
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

				.selectFlows( FlowList.ENABLED, "second | bar baz" )
				.expect( "enabled flow selected",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐╔═══╗┌─ enabled_flo… ─┐╔═════╗",
						"│  bar     │┊ _ ┊│          ││            │║ _ ║│  first | bar … │║     ║",
						"│  baz     │└┈┈┈┘└──────────┘│            │║   ║│ █second | bar… │║     ║",
						"│  foo     │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │╚═══╝│  third | baz … │║     ║",
						"│  oof     │     ┊    _     ┊│            │╔═══╗│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │║   ║│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │║ _ ║│                │║     ║",
						"│          │┊ _ ┊│          ││            │║   ║│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘╚═══╝└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║     ║",
						"┊           Reset           ┊┊               Reset               ┊║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═════╝" )
				.enable()
				.expect( "selected flow retained",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  bar     │┊ _ ┊│          ││  first | … │┊ _ ┊│  second | bar… │║     ║",
						"│  baz     │└┈┈┈┘└──────────┘│  third | … │┊   ┊│                │║     ║",
						"│  foo     │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│  oof     │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Reset               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )

				.selectFlows( FlowList.DISABLED, "third | baz oof" )
				.expect( "disabled flow selected",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐╔═══╗┌─ enabled_flo… ─┐╔═════╗",
						"│  bar     │┊ _ ┊│          ││  first | … │║ _ ║│  second | bar… │║     ║",
						"│  baz     │└┈┈┈┘└──────────┘│ █third | … │║   ║│                │║     ║",
						"│  foo     │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │╚═══╝│                │║     ║",
						"│  oof     │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Reset               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )
				.enable()
				.expect( "selected flow enabled",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  bar     │┊ _ ┊│          ││  first | … │┊ _ ┊│  second | bar… │║     ║",
						"│  baz     │└┈┈┈┘└──────────┘│            │┊   ┊│  third | baz … │║     ║",
						"│  oof     │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│  foo     │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Reset               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )

				.selectFlows( FlowList.ENABLED, "second | bar baz" )
				.disable()
				.expect( "selected flow disabled",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  baz     │┊ _ ┊│          ││  first | … │┊ _ ┊│  third | baz … │║     ║",
						"│  oof     │└┈┈┈┘└──────────┘│  second |… │┊   ┊│                │║     ║",
						"│  bar     │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│  foo     │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Reset               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )

				.resetFlows()
				.expect( "all flows enabled",
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
	 * Tags in the include filter are not displayed on flows
	 */
	@Test
	void tagDisplay() {
		Mdl mdl = new Mdl().withFlows(
				"1 [a, b, c]" );

		new FilterGuiHarness()
				.selectTags( TagList.AVAILABLE, "a" )
				.moveTo( TagList.INCLUDED )
				.expectFlowAccess( "No flows built yet", false )
				.buildFlows()
				.expectFlowAccess( "Flows constructed", true )
				.expect( "a is not display against the flow",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  b       │┊ _ ┊│  a       ││            │┊ _ ┊│  1 | b c       │║     ║",
						"│  c       │└┈┈┈┘└──────────┘│            │┊   ┊│                │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"╔═══════════════════════════╗┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║     ║",
						"║           Reset           ║┊               Reset               ┊║     ║",
						"╚═══════════════════════════╝└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═════╝" )
				.close()
				.on( mdl );
	}

	/**
	 * Tag edits after flow build can cause more flows to be built
	 */
	@Test
	void rebuild() {
		Mdl mdl = new Mdl().withFlows(
				"1 [b, c, d]",
				"2 [a, b, e]" );

		new FilterGuiHarness()
				.selectTags( TagList.AVAILABLE, "d" )
				.moveTo( TagList.INCLUDED )
				.expectFlowAccess( "No flows built yet", false )
				.buildFlows()
				.expectFlowAccess( "Flows constructed", true )
				.expect( "only 1 flow displayed. Tags not on that"
						+ " flow are deprioritised in the available list",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  b       │┊ _ ┊│  d       ││            │┊ _ ┊│  1 | b c       │║     ║",
						"│  c       │└┈┈┈┘└──────────┘│            │┊   ┊│                │║     ║",
						"│  a       │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│  e       │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"╔═══════════════════════════╗┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║     ║",
						"║           Reset           ║┊               Reset               ┊║     ║",
						"╚═══════════════════════════╝└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═════╝" )
				.resetTags()
				.expect( "both flows displayed",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│  a       │┊ _ ┊│          ││            │┊ _ ┊│  1 | b c d     │║     ║",
						"│  b       │└┈┈┈┘└──────────┘│            │┊   ┊│  2 | a b e     │║     ║",
						"│  c       │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│  d       │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│  e       │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐║     ║",
						"┊           Reset           ┊┊               Reset               ┊║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═════╝" )
				.close()
				.on( mdl );
	}

	/**
	 * Shows how the filter input shuffles the tag and flow lists
	 */
	@Test
	void filter() {

		Mdl mdl = new Mdl().withFlows(
				"bcd []",
				"fga []",
				"jkl []",
				"mne []" );

		new FilterGuiHarness()
				.expectFlowAccess( "No flows built yet", false )
				.buildFlows()
				.expectFlowAccess( "Flows constructed", true )
				.selectFlows( FlowList.ENABLED, "bcd | ", "fga | " )
				.enable()
				.expect( "unfiltered",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│          │┊ _ ┊│          ││  jkl |     │┊ _ ┊│  bcd |         │║     ║",
						"│          │└┈┈┈┘└──────────┘│  mne |     │┊   ┊│  fga |         │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Reset               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )
				.filter( "[aeiou]" )
				.expect( "flows sorted",
						"┌─ Filter ──────────────────────────────────────────────────────────────┐",
						"│                                 [aeiou]                               │",
						"└───────────────────────────────────────────────────────────────────────┘",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│          │┊ _ ┊│          ││  mne |     │┊ _ ┊│  fga |         │║     ║",
						"│          │└┈┈┈┘└──────────┘│  jkl |     │┊   ┊│  bcd |         │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│                │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Reset               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )
				.close()
				.on( mdl );
	}

	/**
	 * Shows how the GUI allows historic failures to be selected
	 */
	@Test
	void failures() {

		Mdl mdl = new Mdl().withFlows(
				"bcd []",
				"fga []",
				"jkl []",
				"mne []" );
		// generate a report that shows jkl to be passing
		Deque<String> resultTags = new ArrayDeque<>( Arrays.asList(
				Writer.ERROR_TAG,
				Writer.FAIL_TAG,
				Writer.PASS_TAG,
				Writer.SKIP_TAG ) );
		Writer w = new Writer( "model", "test",
				Paths.get( "target", "mctf", "FlowPanelTest", "failures" ) );
		mdl.flows().forEach( f -> w.with( f, fd -> fd.tags.add( resultTags.removeFirst() ) ) );

		new FilterGuiHarness()
				.buildFlows()
				.expect( "Fails button available",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│          │┊ _ ┊│          ││            │┊ _ ┊│  bcd |         │║     ║",
						"│          │└┈┈┈┘└──────────┘│            │┊   ┊│  fga |         │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│  jkl |         │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│  mne |         │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Fails               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )
				.loadFailures()
				.expect( "failing flows selected",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│          │┊ _ ┊│          ││  jkl |     │┊ _ ┊│  bcd |         │║     ║",
						"│          │└┈┈┈┘└──────────┘│            │┊   ┊│  fga |         │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│  mne |         │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│                │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Reset               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )
				.resetFlows()
				.expect( "Fails button shown when reset would be disabled",
						"┌─ avail… ─┐┌┈┈┈┐┌─ inclu… ─┐┌─ disable… ─┐┌┈┈┈┐┌─ enabled_flo… ─┐╔═════╗",
						"│          │┊ _ ┊│          ││            │┊ _ ┊│  bcd |         │║     ║",
						"│          │└┈┈┈┘└──────────┘│            │┊   ┊│  fga |         │║     ║",
						"│          │     ┌┈┈┈┈┈┈┈┈┈┈┐│            │└┈┈┈┘│  jkl |         │║     ║",
						"│          │     ┊    _     ┊│            │┌┈┈┈┐│  mne |         │║     ║",
						"│          │     └┈┈┈┈┈┈┈┈┈┈┘│            │┊   ┊│                │║ Run ║",
						"│          │┌┈┈┈┐┌─ exclu… ─┐│            │┊ _ ┊│                │║     ║",
						"│          │┊ _ ┊│          ││            │┊   ┊│                │║     ║",
						"└──────────┘└┈┈┈┘└──────────┘└────────────┘└┈┈┈┘└────────────────┘║     ║",
						"┌┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┐╔═══════════════════════════════════╗║     ║",
						"┊           Reset           ┊║               Fails               ║║     ║",
						"└┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┘╚═══════════════════════════════════╝╚═════╝" )
				.close()
				.on( mdl );
	}
}
