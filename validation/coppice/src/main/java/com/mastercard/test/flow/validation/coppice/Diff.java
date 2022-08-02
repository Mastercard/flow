/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Utility methods for comparing strings
 */
public class Diff {

	private Diff() {
		// no instances
	}

	/**
	 * @param from A string
	 * @param to   Another string
	 * @return The number of lines of difference between the two
	 */
	public static int diffDistance( String from, String to ) {

		return DiffUtils.diff(
				Arrays.asList( from.split( "\n" ) ),
				Arrays.asList( to.split( "\n" ) ), false ).getDeltas()
				.stream()
				.mapToInt( delta -> {
					switch( delta.getType() ) {
						case DELETE:
							return delta.getSource().getLines().size();
						case INSERT:
							return delta.getTarget().getLines().size();
						case CHANGE:
							return Math.max(
									delta.getSource().getLines().size(),
									delta.getTarget().getLines().size() );
						default:
							return 0;
					}
				} )
				.sum();

	}

	/**
	 * @param from A string
	 * @param to   Another string
	 * @return Styled HTML that shows the differences between the strings
	 */
	public static String diffHTML( String from, String to ) {
		StringBuilder diff = new StringBuilder( ""
				+ "<html><head><style>\n"
				+ ".del { background-color: #FFAAAA; }\n"
				+ ".ins { background-color: #AAFFAA; }\n"
				+ "</style></head><body><pre>\n" );

		Patch<String> patch = DiffUtils.diff(
				Arrays.asList( from.split( "\n" ) ),
				Arrays.asList( to.split( "\n" ) ), true );

		format( diff, patch, "\n" );

		diff.append( "</pre></body></html>" );
		return diff.toString();
	}

	private static void format( StringBuilder diff, Patch<String> patch, String joint ) {
		for( AbstractDelta<String> delta : patch.getDeltas() ) {
			switch( delta.getType() ) {
				case DELETE:
					diff.append( "<span class=\"del\">" )
							.append( escapeHTML(
									delta.getSource().getLines().stream()
											.collect( joining( joint ) ) ) )
							.append( "</span>" )
							.append( joint );
					break;
				case INSERT:
					diff.append( "<span class=\"ins\">" )
							.append( escapeHTML(
									delta.getTarget().getLines().stream()
											.collect( joining( joint ) ) ) )
							.append( "</span>" )
							.append( joint );
					break;
				case CHANGE:
					diff.append( "<span class=\"del\">" )
							.append( escapeHTML(
									delta.getSource().getLines().stream()
											.collect( joining( joint ) ) ) )
							.append( "</span>" )
							.append( joint )
							.append( "<span class=\"ins\">" )
							.append( escapeHTML(
									delta.getTarget().getLines().stream()
											.collect( joining( joint ) ) ) )
							.append( "</span>" )
							.append( joint );
					break;
				case EQUAL:
				default:
					diff.append( escapeHTML(
							delta.getSource().getLines().stream()
									.collect( joining( joint ) ) ) )
							.append( joint );
					break;
			}
		}
	}

	/**
	 * A map from character to the equivalent HTML entity
	 */
	static final Map<Character, String> HTML_CHARS = Collections.unmodifiableMap( Stream.of(
			"< &lt;", "> &gt;", "& &amp;", "\" &quot;", "£ &pound;" )
			.collect( Collectors.toMap( k -> k.charAt( 0 ), v -> v.substring( 2 ) ) ) );

	/**
	 * Renders a string suitable for inclusion in a HTML page
	 *
	 * @param s a string
	 * @return The same string, but with characters replaced with HTML entities
	 */
	public static String escapeHTML( String s ) {
		StringBuilder out = new StringBuilder( Math.max( 16, s.length() ) );
		for( int i = 0; i < s.length(); i++ ) {
			char c = s.charAt( i );
			if( HTML_CHARS.containsKey( c ) ) {
				out.append( HTML_CHARS.get( c ) );
			}
			else if( c > 127 ) {
				out.append( "&#" )
						.append( (int) c )
						.append( ';' );
			}
			else {
				out.append( c );
			}
		}
		return out.toString();
	}
}
