package com.mastercard.test.flow.assrt.filter.gui;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.assertj.swing.exception.ComponentLookupException;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JButtonFixture;
import org.assertj.swing.fixture.JListFixture;
import org.assertj.swing.fixture.JTextComponentFixture;

import com.mastercard.test.flow.assrt.filter.Util;
import com.mastercard.test.flow.assrt.filter.cli.Cli;
import com.mastercard.test.flow.assrt.filter.cli.Corner;
import com.mastercard.test.flow.assrt.filter.cli.Line;
import com.mastercard.test.flow.assrt.filter.gui.FilterGuiHarness.FlowList;
import com.mastercard.test.flow.assrt.filter.gui.FilterGuiHarness.TagList;

/**
 * Utility for extracting the state of the gui as character art
 */
class GuiRender {
	private GuiRender() {
		// no instances
	}

	/**
	 * Extracts the gui state and renders it into text
	 *
	 * @param f The frame that holds the gui
	 * @return the gui state
	 */
	static String extract( FrameFixture f ) {
		int height = IntStream.of(
				tagHeight( f ),
				flowHeight( f ),
				3 )
				.max()
				.orElseThrow( IllegalStateException::new );

		String gui = hJoin(
				tagState( f, height ),
				flowState( f, height ),
				button( f, "run_button", 7, height ) );

		// add the filter textfield if it's non-empty
		JTextComponentFixture filter = f.textBox( "filter_textfield" );
		if( !filter.text().isEmpty() ) {
			gui = vJoin(
					box( width( gui ), Line.SINGLE, Corner.SINGLE, "Filter",
							midLines( 1, midText( filter.text(), width( gui ) - 2 ) ) ),
					gui );
		}
		return Util.copypasta( gui );
	}

	private static int tagHeight( FrameFixture f ) {
		return IntStream.of(
				12, // static minimum of 4 buttons
				listHeight( f, TagList.AVAILABLE.widgetName ) + 3,
				Math.max(
						listHeight( f, TagList.INCLUDED.widgetName ),
						listHeight( f, TagList.EXCLUDED.widgetName ) ) * 2
						+ 6 )
				.max()
				.orElseThrow( IllegalStateException::new );
	}

	private static String tagState( FrameFixture f, int height ) {
		String available = list( f, TagList.AVAILABLE.widgetName, 12, height - 3 );

		int th = (int) Math.ceil( (height - 6) / 2.0 );
		String swaps = vJoin(
				button( f, "avin_button", 5, th ),
				box( 5, Line.EMPTY, Corner.EMPTY, "", topLines( 1 ) ),
				button( f, "avex_button", 5, height - 6 - th ) );

		String inex = vJoin(
				list( f, TagList.INCLUDED.widgetName, 12, th ),
				button( f, "inex_button", 12, 3 ),
				list( f, TagList.EXCLUDED.widgetName, 12, height - 6 - th ) );

		String reset = button( f, "reset_tags_button", 29, 3 );

		return vJoin(
				hJoin( available, swaps, inex ),
				reset );
	}

	private static int flowHeight( FrameFixture f ) {
		return IntStream.of(
				6, // static minimum
				listHeight( f, FlowList.ENABLED.widgetName ),
				listHeight( f, FlowList.DISABLED.widgetName ) )
				.max()
				.orElseThrow( IllegalStateException::new );
	}

	private static String flowState( FrameFixture f, int height ) {
		try {
			f.button( "build_button" );
			return button( f, "build_button", 9, height );
		}
		catch( @SuppressWarnings("unused") ComponentLookupException cle ) {
			// expected
		}

		int dw = 14;
		int ew = 18;
		int listHeight = height - 3;

		String disabled = flowList( f, "disabled_flow_list", dw, listHeight );
		int hh = listHeight / 2;
		String buttons = vJoin(
				button( f, "include_button", 5, hh ),
				button( f, "exclude_button", 5, listHeight - hh ) );
		String enabled = flowList( f, "enabled_flow_list", ew, listHeight );

		String lists = hJoin( disabled, buttons, enabled );
		String reset = button( f, "reset_flows_button", dw + ew + 5, 3 );
		return vJoin( lists, reset );
	}

	private static int listHeight( FrameFixture f, String widgetName ) {
		try {
			return f.list( widgetName ).contents().length + 2;
		}
		catch( @SuppressWarnings("unused") ComponentLookupException cle ) {
			return 2;
		}
	}

	private static String button( FrameFixture f, String name, int width, int height ) {
		JButtonFixture jbf = f.button( name );
		// it'd be nice to use the text as-is, but strange characters can mess up text
		// layout in some editors which kind of ruins the point of rendering the gui
		// into text
		String text = jbf.text().replaceAll( "[^a-z A-Z0-9]", "_" );
		return box( width,
				jbf.isEnabled() ? Line.DOUBLE : Line.DASH,
				jbf.isEnabled() ? Corner.DOUBLE : Corner.SINGLE,
				"",
				midLines( height - 2, midText( text, width - 4 ) ) );
	}

	private static String list( FrameFixture f, String name, int width, int height ) {
		return box( width, Line.SINGLE, Corner.SINGLE, name,
				listContent( f.list( name ), width - 2, height - 2, v -> v ) );
	}

	private static String flowList( FrameFixture f, String name, int width, int height ) {
		return box( width, Line.SINGLE, Corner.SINGLE, name,
				listContent( f.list( name ), width - 2, height - 2, GuiRender::fromRendered ) );
	}

	private static Deque<String> listContent( JListFixture list, int width, int height,
			UnaryOperator<String> valueMutate ) {
		Deque<String> elements = topLines( height, list.contents() );
		Set<String> selection = Stream.of( list.selection() ).map( valueMutate ).collect( toSet() );
		return elements.stream()
				.map( valueMutate )
				.map( e -> {
					if( selection.contains( e ) ) {
						return "█" + e + repeat( '█', width - e.length() - 3 );
					}
					return " " + e;
				} )
				.collect( toCollection( ArrayDeque::new ) );
	}

	private static final Pattern FROM_RENDERED = Pattern.compile(
			"<html>(.*) <span style=\"color:gray\">(.*)</span></html>" );

	private static final String fromRendered( String flow ) {
		Matcher m = FROM_RENDERED.matcher( flow );
		if( m.find() ) {
			return String.format( "%s | %s", m.group( 1 ), m.group( 2 ) );
		}
		return flow;
	}

	private static String box( int width, Line line, Corner corner, String title,
			Deque<String> lines ) {
		String block = new Cli( width )
				.box( line, corner, title, b -> lines
						.forEach( b::line ) )
				.content().toString();
		return block.substring( 0, block.length() - 1 );
	}

	private static Deque<String> topLines( int height, String... contents ) {
		Deque<String> lines = new ArrayDeque<>();
		Stream.of( contents )
				.flatMap( text -> Stream.of( text.split( "\n" ) ) )
				.limit( height )
				.forEach( lines::add );
		while( lines.size() < height ) {
			lines.add( "" );
		}
		return lines;
	}

	private static Deque<String> midLines( int height, String... contents ) {
		Deque<String> lines = new ArrayDeque<>();
		Stream.of( contents )
				.flatMap( text -> Stream.of( text.split( "\n" ) ) )
				.limit( height )
				.forEach( lines::add );
		while( lines.size() < height ) {
			lines.add( "" );
			if( lines.size() < height ) {
				lines.addFirst( "" );
			}
		}
		return lines;
	}

	private static String midText( String text, int width ) {
		int s = width - text.length();
		if( s > 2 ) {
			return repeat( ' ', s / 2 ) + text;
		}
		return text;
	}

	private static String repeat( char c, int length ) {
		StringBuilder sb = new StringBuilder();
		for( int i = 0; i < length; i++ ) {
			sb.append( c );
		}
		return sb.toString();
	}

	private static String vJoin( String... blocks ) {
		int width = Stream.of( blocks )
				.filter( Objects::nonNull )
				.mapToInt( GuiRender::width ).max()
				.orElse( 0 );
		Stream.of( blocks )
				.filter( Objects::nonNull )
				.filter( b -> width( b ) != width )
				.findFirst()
				.ifPresent( b -> {
					throw new IllegalArgumentException( "Bad block width! Expected " + width + " on"
							+ Stream.of( blocks )
									.map( block -> "\n--- " + width( block ) + "\n" + block )
									.collect( joining() )
							+ "---" );
				} );
		return Stream.of( blocks )
				.filter( Objects::nonNull )
				.collect( joining( "\n" ) );
	}

	private static String hJoin( String... blocks ) {
		int height = Stream.of( blocks )
				.filter( Objects::nonNull )
				.mapToInt( GuiRender::height ).max().orElse( 0 );
		Stream.of( blocks )
				.filter( Objects::nonNull )
				.filter( b -> height( b ) != height )
				.findFirst().ifPresent( b -> {
					throw new IllegalArgumentException( "Bad block height! Expected " + height + " on"
							+ Stream.of( blocks )
									.map( block -> "\n--- " + height( block ) + "\n" + block )
									.collect( joining() )
							+ "---" );
				} );

		StringBuilder sb = new StringBuilder();
		List<Deque<String>> lines = Stream.of( blocks )
				.filter( Objects::nonNull )
				.map( block -> Stream.of( block.split( "\n" ) )
						.collect( toCollection( ArrayDeque::new ) ) )
				.collect( toList() );
		for( int i = 0; i < height; i++ ) {
			for( Deque<String> bl : lines ) {
				sb.append( bl.poll() );
			}
			sb.append( "\n" );
		}
		return sb.toString().substring( 0, sb.length() - 1 );
	}

	private static int height( String block ) {
		return block.split( "\n" ).length;
	}

	private static int width( String block ) {
		return Stream.of( block.split( "\n" ) )
				.mapToInt( String::length )
				.max().orElse( 0 );
	}
}
