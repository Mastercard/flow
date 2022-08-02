package com.mastercard.test.flow.assrt.filter.cli;

import java.util.List;
import java.util.Set;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.mastercard.test.flow.assrt.filter.Filter;

/**
 * Offers completion suggestions for tag inclusion and exclusion
 */
class TagCompleter implements Completer {

	private final Filter filter;

	/**
	 * @param filter The filter that holds the tags
	 */
	public TagCompleter( Filter filter ) {
		this.filter = filter;
	}

	@Override
	public void complete( LineReader reader, ParsedLine line, List<Candidate> candidates ) {
		Set<String> allTags = filter.allTags();
		Set<String> includedTags = filter.includedTags();
		Set<String> excludedTags = filter.excludedTags();

		if( line.word().isEmpty() ) {
			candidates.add( new Candidate(
					"+", "+", "tag", "Inclusive filter", null, null, false ) );
			candidates.add( new Candidate(
					"-", "-", "tag", "Exclusive filter", null, null, false ) );
		}
		else if( line.word().startsWith( "+" ) ) {
			allTags.stream()
					.filter( t -> !includedTags.contains( t ) )
					.forEach( t -> candidates.add( new Candidate( "+" + t ) ) );
		}
		else if( line.word().startsWith( "-" ) ) {
			allTags.stream()
					.filter( t -> !excludedTags.contains( t ) )
					.forEach( t -> candidates.add( new Candidate( "-" + t ) ) );
		}
	}
}
