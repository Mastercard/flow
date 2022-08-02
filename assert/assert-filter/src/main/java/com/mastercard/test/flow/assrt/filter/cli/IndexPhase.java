package com.mastercard.test.flow.assrt.filter.cli;

import static com.mastercard.test.flow.assrt.filter.cli.CommandCompleter.Command.FAILS;
import static com.mastercard.test.flow.assrt.filter.cli.CommandCompleter.Command.FLOW_RESET;
import static com.mastercard.test.flow.assrt.filter.cli.CommandCompleter.Command.HELP;
import static com.mastercard.test.flow.assrt.filter.cli.CommandCompleter.Command.RESET;
import static com.mastercard.test.flow.assrt.filter.cli.CommandCompleter.Command.TAG_RESET;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.jline.reader.Completer;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.utils.AttributedStyle;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.cli.Cli.IndexedTaggedList;

/**
 * Wherein we build a list of flows, and the user can choose from them
 */
class IndexPhase extends TagPhase {

	private static final Pattern IDX_RANGE = Pattern.compile( "([+-]?)(\\d+)(-?)(\\d*)" );
	private Completer completer;

	/**
	 * @param filter The filter to update with user input
	 */
	public IndexPhase( Filter filter ) {
		super( filter );
		completer = new AggregateCompleter(
				new CommandCompleter( HELP, RESET, TAG_RESET, FLOW_RESET, FAILS ),
				new TagCompleter( filter ),
				new DescriptionCompleter( filter ) );
	}

	@Override
	public Completer completer() {
		return completer;
	}

	@Override
	public void render( Cli cli ) {
		printFlows( cli );

		Set<String> onFlows = filter.taggedFlows().stream()
				.flatMap( f -> f.meta().tags().stream() )
				.collect( toSet() );
		printTags( cli, tag -> !onFlows.contains( tag ) );
		drawHelp( cli );
		printErrors( cli );
	}

	@Override
	public UiPhase next( String input ) {
		showHelp = false;
		errors.clear();

		if( input.isEmpty() ) {
			return null;
		}

		for( String word : input.split( "\\s+" ) ) {
			if( RESET.invokedBy( word ) ) {
				filter.indices( Collections.emptySet() );
				filter.includedTags( Collections.emptySet() );
				filter.excludedTags( Collections.emptySet() );
			}
			else if( TAG_RESET.invokedBy( word ) ) {
				filter.includedTags( Collections.emptySet() );
				filter.excludedTags( Collections.emptySet() );
			}
			else if( FLOW_RESET.invokedBy( word ) ) {
				filter.indices( Collections.emptySet() );
			}
			else if( FAILS.invokedBy( word ) ) {
				// pitest shows no coverage of this line, but that's because the test that
				// exercises it causes pitest to hang so we've had to disable it via an
				// excludedTestClasses entry in the pom file
				filter.loadFailureIndices();
			}
			else if( updateIndices( word ) ) {
				// index filter
			}
			else if( DescriptionCompleter.offer( word, filter, errors ) ) {
				// description search filter
			}
			else {
				// it's not an index, it might be a tag command
				updateTagFilters( word );
			}

		}

		return this;
	}

	private boolean updateIndices( String word ) {
		Matcher m = IDX_RANGE.matcher( word );
		if( !m.matches() ) {
			return false;
		}
		int flowCount = filter.taggedFlows().size();
		boolean exclude = "-".equals( m.group( 1 ) );
		int low = Integer.parseInt( m.group( 2 ) );
		boolean range = "-".equals( m.group( 3 ) );
		String highStr = m.group( 4 );
		int high = range
				? highStr.isEmpty()
						? (int) filter.flows().count() // unbounded
						: Integer.parseInt( highStr ) // bounded
				: low; // single index

		// sort input
		int l = low;
		int h = high;
		low = Math.min( l, h );
		high = Math.max( l, h );

		// UI is 1-based, filter is 0-based
		low--;
		high--;

		// constrain to reality
		low = Math.max( 0, low );
		high = Math.min( high, flowCount );

		Set<Integer> indices = filter.indices();
		if( exclude ) {
			if( indices.isEmpty() ) {
				IntStream.range( 0, flowCount )
						.forEach( indices::add );
			}
			IntStream.rangeClosed( low, high )
					.forEach( indices::remove );
		}
		else {
			IntStream.rangeClosed( low, high )
					.forEach( indices::add );
		}
		filter.indices( indices );
		return true;
	}

	private void printFlows( Cli cli ) {
		List<Flow> flows = filter.taggedFlows();
		Set<Integer> indices = filter.indices();
		Set<String> includedTags = filter.includedTags();

		cli.box( "Flows", box -> {
			if( !indices.isEmpty() ) {
				box.section( "Disabled" )
						.indexedTaggedList( as -> as.crossedOut().faint(), list -> flowItems(
								list, flows, i -> !indices.contains( i ), includedTags ) )
						.section( "Enabled" );
			}
			box.indexedTaggedList( AttributedStyle::bold, list -> flowItems(
					list, flows, i -> indices.isEmpty() || indices.contains( i ), includedTags ) );
		} );
	}

	private static void flowItems( IndexedTaggedList<?> list, List<Flow> flows,
			IntPredicate show, Set<String> includedTags ) {
		for( int i = 0; i < flows.size(); i++ ) {
			if( show.test( i ) ) {
				Flow flow = flows.get( i );
				list.item( i + 1,
						flow.meta().description(),
						flow.meta().tags().stream()
								// all flows will bear the tags in the included set, so don't bother showing
								// them against the flow
								.filter( t -> !includedTags.contains( t ) )
								.collect( toCollection( TreeSet::new ) ) );
			}
		}
	}

	@Override
	protected void drawHelp( Cli cli ) {
		if( showHelp ) {
			cli.box( "Help: Flow choice", h -> h
					.paragraph( "Add tag and flow filters to control which flows "
							+ "are exercised. Flows can be selected based on their index or description text." )
					.descriptionList( dl -> dl
							.item( "+<tag_name>", "Adds an inclusive tag filter" )
							.item( "-<tag_name>", "Adds an exclusive tag filter" )
							.item( "+<index>", "Selects a single flow" )
							.item( "-<index>", "Deselects a single flow" )
							.item( "+<index>-<index>", "Selects a range of flows" )
							.item( "-<index>-<index>", "Deselects a range of flows" )
							.item( "/<regex>", "Selects flows whose descriptions match the expression" )
							.item( "?<regex>", "Deselects flows whose descriptions match the expression" )
							.item( RESET.syntax(), RESET.description )
							.item( TAG_RESET.syntax(), TAG_RESET.description )
							.item( FLOW_RESET.syntax(), FLOW_RESET.description )
							.item( FAILS.syntax(), FAILS.description ) )
					.paragraph( "Provide no command to exercise the selected flows" ) );
		}
	}
}
