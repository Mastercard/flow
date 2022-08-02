package com.mastercard.test.flow.assrt.filter.cli;

import static com.mastercard.test.flow.assrt.filter.cli.CommandCompleter.Command.HELP;
import static com.mastercard.test.flow.assrt.filter.cli.CommandCompleter.Command.RESET;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jline.reader.Completer;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.utils.AttributedStyle;

import com.mastercard.test.flow.assrt.filter.Filter;

/**
 * Wherein the user specifies tag filters, but we haven't built any flows yet
 */
class TagPhase implements UiPhase {

	/**
	 * The filter to update in response to user input
	 */
	protected final Filter filter;
	private Completer completer;
	/**
	 * <code>true</code> if the help text has been requested
	 */
	protected boolean showHelp = false;
	/**
	 * Populate this with user-provoked errors
	 */
	protected List<String> errors = new ArrayList<>();

	/**
	 * @param filter The filter to update in response to user input
	 */
	public TagPhase( Filter filter ) {
		this.filter = filter;
		completer = new AggregateCompleter(
				new CommandCompleter( HELP, RESET ),
				new TagCompleter( filter ) );
	}

	@Override
	public Completer completer() {
		return completer;
	}

	@Override
	public void render( Cli cli ) {
		printTags( cli,
				// in this phase we want to display all tags in the model
				tag -> false );
		drawHelp( cli );
		printErrors( cli );
	}

	@Override
	public UiPhase next( String input ) {
		showHelp = false;
		errors.clear();

		if( input.isEmpty() ) {
			return new IndexPhase( filter );
		}

		updateTagFilters( input );

		return this;
	}

	/**
	 * Updates tag filters
	 *
	 * @param input user input
	 */
	protected void updateTagFilters( String input ) {
		Set<String> all = filter.allTags();
		Set<String> included = filter.includedTags();
		Set<String> excluded = filter.excludedTags();
		for( String word : input.split( "\\s+" ) ) {
			if( !word.isEmpty() ) {
				if( HELP.invokedBy( word ) ) {
					showHelp = true;
				}
				else if( RESET.invokedBy( word ) ) {
					included.clear();
					excluded.clear();
				}
				else if( word.startsWith( "+" ) || word.startsWith( "-" ) ) {
					boolean include = word.startsWith( "+" );
					String tag = word.substring( 1 );

					if( all.contains( tag ) ) {
						if( include ) {
							if( !excluded.remove( tag ) ) {
								included.add( tag );
							}
						}
						else if( !included.remove( tag ) ) {
							excluded.add( tag );
						}
					}
				}
				else {
					errors.add( "Unrecognised input '" + word + "'" );
				}
			}
		}

		filter
				.includedTags( included )
				.excludedTags( excluded );
	}

	/**
	 * Displays the current tag filters
	 *
	 * @param cli       The interface to append to
	 * @param allFilter Determines which tags are displayed in the availablity box -
	 *                  tags that return true for this predicate are not shown.
	 */
	protected void printTags( Cli cli, Predicate<String> allFilter ) {
		Set<String> all = filter.allTags();
		Set<String> included = filter.includedTags();
		Set<String> excluded = filter.excludedTags();
		all.removeAll( included );
		all.removeAll( excluded );
		all.removeIf( allFilter );

		cli.box( "Tags", b -> {
			b.words( AttributedStyle::faint, all );
			if( !included.isEmpty() ) {
				b.section( "Included" )
						.words( AttributedStyle::bold, included );
			}
			if( !excluded.isEmpty() ) {
				b.section( "Excluded" )
						.words( a -> a.faint().crossedOut(), excluded );
			}
		} );
	}

	/**
	 * Prints help text if requested
	 *
	 * @param cli Where to print to
	 */
	protected void drawHelp( Cli cli ) {
		if( showHelp ) {
			cli.box( "Help: Tag choice", h -> h
					.paragraph( "Add tag filters to minimise the construction"
							+ " of flows that you don't want to exercise" )
					.descriptionList( dl -> dl
							.item( "+<tag>", "Adds an inclusive tag filter" )
							.item( "-<tag>", "Adds an exclusive tag filter" )
							.item( RESET.syntax(), RESET.description ) )
					.paragraph( "Only those flows that bear all of the included tags and none of the "
							+ "excluded tags will be exercised." )
					.paragraph( "Provide no command to build the tagged flows and proceed" ) );
		}
	}

	/**
	 * Prints error content if any exists
	 *
	 * @param cli Where to print to
	 */
	protected void printErrors( Cli cli ) {
		if( !errors.isEmpty() ) {
			cli.box( "Error", b -> {
				errors.forEach( b::paragraph );
				b.paragraph( "Invoke 'help' to see available commands" );
			} );
		}
	}
}
