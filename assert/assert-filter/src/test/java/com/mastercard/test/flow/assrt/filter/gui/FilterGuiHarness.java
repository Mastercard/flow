
package com.mastercard.test.flow.assrt.filter.gui;

import static com.mastercard.test.flow.assrt.filter.Util.copypasta;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JListFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.junit.jupiter.api.Assertions;

import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.Util;
import com.mastercard.test.flow.assrt.filter.mock.Mdl;

/**
 * Utility for exercising {@link FilterGui}
 */
public class FilterGuiHarness {

	static {
		FailOnThreadViolationRepaintManager.install();
	}

	private final Deque<BiConsumer<FrameFixture, Mdl>> interactions = new ArrayDeque<>();

	private TagList selection = null;
	private String results = "not executed!";

	/**
	 * The tag list widgets in the interface
	 */
	public enum TagList {

		/***/
		AVAILABLE,
		/***/
		INCLUDED,
		/***/
		EXCLUDED;

		/**
		 * The name by which we fetch the widget from the interface
		 */
		public final String widgetName;

		TagList() {
			widgetName = name().toLowerCase() + "_tag_list";
		}

		/**
		 * Gets name of the button that swaps tags between this list and another
		 *
		 * @param other The other list
		 * @return The button name
		 */
		public String swapName( TagList other ) {
			if( other.ordinal() < ordinal() ) {
				return other.swapName( this );
			}
			return name().substring( 0, 2 ).toLowerCase()
					+ other.name().substring( 0, 2 ).toLowerCase()
					+ "_button";
		}
	}

	/**
	 * Sets the filter field content
	 *
	 * @param regex The filter content
	 * @return <code>this</code>
	 */
	public FilterGuiHarness filter( String regex ) {
		interactions.add( ( f, m ) -> {
			JTextComponentFixture jctf = f.textBox( "filter_textfield" );
			if( !jctf.text().isEmpty() ) {
				jctf.deleteText();
			}
			jctf.enterText( regex );
		} );

		return this;
	}

	/**
	 * Closes the filter gui window
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness close() {
		interactions.add( ( f, m ) -> f.close() );
		return this;
	}

	/**
	 * Selects tags in one of the lists
	 *
	 * @param in   The list to select in
	 * @param tags The tags to select
	 * @return <code>this</code>
	 */
	public FilterGuiHarness selectTags( TagList in, String... tags ) {
		interactions.add( ( f, m ) -> {
			selection = in;
			JListFixture list = f.list( in.widgetName );
			list.selectItems( tags );
		} );
		return this;
	}

	/**
	 * Double-clicks on a list item
	 *
	 * @param in  The list that the item resides in
	 * @param tag The item to double-click
	 * @return <code>this</code>
	 */
	public FilterGuiHarness doubleClick( TagList in, String tag ) {
		interactions.add( ( f, m ) -> {
			f.list( in.widgetName ).item( tag ).doubleClick();
		} );
		return this;
	}

	/**
	 * Clicks one of the tags swap buttons
	 *
	 * @param to The list that we want to swap to
	 * @return <code>this</code>
	 */
	public FilterGuiHarness moveTo( TagList to ) {
		interactions.add( ( f, m ) -> f.button( selection.swapName( to ) ).click() );
		return this;
	}

	/**
	 * Clicks the reset button under the tag panes
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness resetTags() {
		interactions.add( ( f, m ) -> f.button( "reset_tags_button" ).click() );
		return this;
	}

	/**
	 * Clicks the build flows button
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness buildFlows() {
		interactions.add( ( f, m ) -> f.button( "build_button" ).click() );
		return this;
	}

	/**
	 * The flow list widgets in the interface
	 */
	public enum FlowList {

		/***/
		DISABLED,
		/***/
		ENABLED;

		/**
		 * The name by which we fetch the widget from the interface
		 */
		public final String widgetName;

		FlowList() {
			widgetName = name().toLowerCase() + "_flow_list";
		}

	}

	/**
	 * Selects flows from one of the lists
	 *
	 * @param list  The list to select in
	 * @param flows The flows
	 * @return <code>this</code>
	 */
	public FilterGuiHarness selectFlows( FlowList list, String... flows ) {
		interactions.add( ( f, m ) -> f.list( list.widgetName )
				.selectItems( Stream.of( flows )
						.map( FilterGuiHarness::toRendered )
						.toArray( String[]::new ) ) );
		return this;
	}

	/**
	 * Double-clicks on a list item
	 *
	 * @param in   The list that the item resides in
	 * @param flow The item to double-click
	 * @return <code>this</code>
	 */
	public FilterGuiHarness doubleClick( FlowList in, String flow ) {
		interactions.add( ( f, m ) -> {
			f.list( in.widgetName ).item( FilterGuiHarness.toRendered( flow ) ).doubleClick();
		} );
		return this;
	}

	private static final Pattern TO_RENDERED = Pattern.compile(
			"(.*) \\| (.*)" );

	private static final String toRendered( String flow ) {
		Matcher m = TO_RENDERED.matcher( flow );
		if( m.find() ) {
			return String.format(
					"<html>%s <span style=\"color:gray\">%s</span></html>",
					m.group( 1 ), m.group( 2 ) )
					.replaceAll( " ", "&nbsp;" );
		}
		return flow;
	}

	/**
	 * Clicks the enable flows button
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness enable() {
		interactions.add( ( f, m ) -> f.button( "include_button" ).click() );
		return this;
	}

	/**
	 * Clicks the disable flows button
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness disable() {
		interactions.add( ( f, m ) -> f.button( "exclude_button" ).click() );
		return this;
	}

	/**
	 * Clicks the reset button below the flow lists
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness resetFlows() {
		interactions.add( ( f, m ) -> f.button( "reset_flows_button" ).click() );
		return this;
	}

	/**
	 * Clicks on the "Fails" button below the flow lists
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness loadFailures() {
		// it's the same button
		return resetFlows();
	}

	/**
	 * Clicks on the run button
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness run() {
		interactions.add( ( f, m ) -> f.button( "run_button" ).click() );
		return this;
	}

	/**
	 * Hits the enter key on the keyboard
	 *
	 * @return <code>this</code>
	 */
	public FilterGuiHarness hitEnter() {
		interactions.add( ( f, m ) -> f.pressAndReleaseKeys( KeyEvent.VK_ENTER ) );
		return this;
	}

	/**
	 * Execute the compiled steps against a model
	 *
	 * @param model The model to filter
	 * @return The flows that pass the eventual filter
	 */
	public FilterGuiHarness on( Mdl model ) {
		Filter filter = new Filter( model );
		FilterGui gui = new FilterGui( filter );

		// it is suprisingly disruptive to have the mouse pointer left somewhere
		// unexpected, so let's make an effort to put it back where we found it
		Point initialMousePosition = MouseInfo.getPointerInfo().getLocation();

		// block in another thread
		Thread guith = new Thread( () -> {
			gui.blockForInput();
		}, "gui runner" );
		guith.setDaemon( true );
		guith.start();

		FrameFixture ff = WindowFinder
				.findFrame( "flow_filter_frame" )
				.withTimeout( 3000 )
				.using( BasicRobot.robotWithCurrentAwtHierarchy() );

		try {
			while( !interactions.isEmpty() ) {
				interactions.poll().accept( ff, model );
			}

			try {
				guith.join( 5000 );
			}
			catch( InterruptedException e ) {
				e.printStackTrace();
			}

			Assertions.assertFalse( guith.isAlive(), "cli thread should have stopped!" );
		}
		finally {
			ff.robot().moveMouse( initialMousePosition );
			ff.cleanUp();
		}

		results = copypasta( filter.flows()
				.map( f -> f.meta().id() ) );
		return this;
	}

	/**
	 * Asserts on the state of the interface
	 *
	 * @param explanation To accompany the assertion
	 * @param gui         Expected state
	 * @return <code>this</code>
	 */
	public FilterGuiHarness expect( String explanation, String... gui ) {
		interactions
				.add( ( f, m ) -> assertEquals( Util.copypasta( gui ), GuiRender.extract( f ),
						explanation ) );
		return this;
	}

	/**
	 * Asserts on flow construction
	 *
	 * @param explanation To accompany the assertion
	 * @param accessed    <code>true</code> if we expect flows to have been
	 *                    constructed, <code>false</code> if we do not
	 * @return <code>this</code>
	 */
	public FilterGuiHarness expectFlowAccess( String explanation, boolean accessed ) {
		interactions.add( ( f, m ) -> assertEquals( accessed, m.flowAccess(), explanation ) );
		return this;
	}

	/**
	 * Assert on the flows that pass the filter
	 *
	 * @param flows The expected flows
	 */
	public void expectFilterResults( String... flows ) {
		Assertions.assertEquals(
				copypasta( flows ),
				results,
				"filtered flow output" );
	}
}
