package com.mastercard.test.flow.assrt.filter.cli;

import static java.util.stream.Collectors.toCollection;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.filter.Filter;

/**
 * Offers completion suggestions for flow description inclusion and exclusion
 */
class DescriptionCompleter implements Completer {

	private static final String GROUP = "description";

	private enum Inclusion {
		INCLUDE('/', true, "Include", "Inclusive"),
		EXCLUDE('?', false, "Exclude", "Exclusive");

		final char syntax;
		final boolean include;
		final String verb;

		final String adjective;

		Inclusion( char syntax, boolean include, String verb, String adjective ) {
			this.syntax = syntax;
			this.include = include;
			this.verb = verb;
			this.adjective = adjective;
		}

		public static Inclusion match( String word ) {
			if( !word.isEmpty() ) {
				for( Inclusion p : values() ) {
					if( word.charAt( 0 ) == p.syntax ) {
						return p;
					}
				}
			}
			return null;
		}

	}

	private final Filter filter;

	/**
	 * @param filter The filter that holds the tags
	 */
	public DescriptionCompleter( Filter filter ) {
		this.filter = filter;
	}

	/**
	 * Attempts to compile the supplied regex, but will fall back to a literal match
	 * if that fails
	 *
	 * @param regex The regular expression
	 * @return A best-attempt pattern
	 */
	private static Pattern compileOrLiteral( String regex ) {
		try {
			return Pattern.compile( regex );
		}
		catch( @SuppressWarnings("unused") PatternSyntaxException pse ) {
			return Pattern.compile( Pattern.quote( regex ) );
		}
	}

	@Override
	public void complete( LineReader reader, ParsedLine line, List<Candidate> candidates ) {
		Set<String> descriptions = filter.flows()
				.map( f -> f.meta().description() )
				.collect( toCollection( TreeSet::new ) );

		Inclusion inclusion = Inclusion.match( line.word() );
		if( inclusion != null ) {
			String fragment = line.word().substring( 1 );

			Pattern regex = compileOrLiteral( fragment );

			descriptions.stream()
					.filter( d -> regex.matcher( d ).find() )
					.sorted( ( a, b ) -> a.indexOf( fragment ) - b.indexOf( fragment ) )
					.forEach( d -> {
						String cmp = inclusion.syntax + d;
						candidates.add( new Candidate( cmp, cmp, GROUP,
								inclusion.verb + " flows named '" + d + "'",
								null, null, fragment.equals( d ) ) );
					} );
		}

		if( line.word().isEmpty() ) {
			for( Inclusion p : Inclusion.values() ) {
				candidates.add( new Candidate(
						String.valueOf( p.syntax ), String.valueOf( p.syntax ),
						GROUP, p.adjective + " filter",
						null, null, false ) );
			}
		}
	}

	/**
	 * Updates the filter based on a possible description filter command
	 *
	 * @param word   the command
	 * @param filter The filter to update
	 * @param errors Populate this if problems are encountered
	 * @return <code>true</code> if the command was a valid description filter
	 *         command
	 */
	public static boolean offer( String word, Filter filter, List<String> errors ) {
		Inclusion inclusion = Inclusion.match( word );
		if( inclusion == null ) {
			return false;
		}

		Pattern regex = compileOrLiteral( word.substring( 1 ) );

		Map<Integer, String> indexed = indexedDescriptions( filter );

		Set<Integer> retained = new TreeSet<>();

		indexed.forEach( ( index, description ) -> {
			if( inclusion.include == regex.matcher( description ).find() ) {
				retained.add( index );
			}
		} );

		filter.indices( retained );

		return true;
	}

	private static Map<Integer, String> indexedDescriptions( Filter filter ) {
		List<Flow> flows = filter.taggedFlows();
		Set<Integer> indices = filter.indices();
		if( indices.isEmpty() ) {
			IntStream.range( 0, flows.size() )
					.forEach( indices::add );
		}
		Map<Integer, String> idm = new TreeMap<>();
		indices.forEach( i -> idm.put( i, flows.get( i ).meta().description() ) );
		return idm;
	}
}
