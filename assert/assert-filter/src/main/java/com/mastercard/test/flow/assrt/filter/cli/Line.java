package com.mastercard.test.flow.assrt.filter.cli;

import static java.util.stream.Collectors.toCollection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

/**
 * Horizontal and vertical strokes and text-formatting utilities
 */
public enum Line {
	/***/
	EMPTY(' ', ' '),
	/***/
	DASH('┈', '┊'),
	/***/
	SINGLE('─', '│'),
	/***/
	DOUBLE('═', '║');

	/**
	 * Horizontal line
	 */
	public final char h;
	/**
	 * Vertical line
	 */
	public final char v;

	Line( char h, char v ) {
		this.h = h;
		this.v = v;
	}

	/**
	 * As-a-string accessor
	 *
	 * @return The horizontal line character
	 */
	public String h() {
		return String.valueOf( h );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return The vertical line character
	 */
	public String v() {
		return String.valueOf( v );
	}

	/**
	 * Performs word wrapping
	 *
	 * @param width      The desired line length
	 * @param splitGrace When deciding whether or not to split a word, if either
	 *                   fragment is smaller than this value then we try to avoid
	 *                   the split
	 * @param words      The words to wrap onto lines
	 * @return The input words, split into lines of the appropriate length (assuming
	 *         a single character join)
	 * @see #words(String)
	 */
	public static Deque<List<String>> wrap( int width, int splitGrace, Deque<String> words ) {
		Deque<List<String>> lines = new ArrayDeque<>();
		while( !words.isEmpty() ) {
			int before = words.stream().mapToInt( String::length ).sum();
			lines.add( fillLine( width, splitGrace, words ) );
			int after = words.stream().mapToInt( String::length ).sum();
			if( after >= before ) {
				throw new IllegalArgumentException(
						"Failed to consume any characters "
								+ "while wrapping " + words + " into width " + width );
			}
		}
		return lines;
	}

	/**
	 * Consumes content from a list of words to fill available space
	 *
	 * @param width      The space to fill
	 * @param splitGrace When deciding whether or not to split a word, if either
	 *                   fragment is smaller than this value then we try to avoid
	 *                   the split
	 * @param words      The content to consume
	 * @return The content that fits in the space
	 */
	public static List<String> fillLine( int width, int splitGrace, Deque<String> words ) {

		if( width <= 0 ) {
			return Collections.emptyList();
		}

		final List<String> line = new ArrayList<>();
		int current = 0;

		while( !words.isEmpty() ) {
			String word = words.removeFirst();
			int space = width - current;

			if( space >= word.length() ) {
				// simple case: the word fits on the current line
				line.add( word );
				current += word.length() + 1;
			}
			else if( !line.isEmpty() && (space <= splitGrace + 1
					|| word.length() - space < splitGrace + 1) ) {
				// the word doesn't fit, and there's not enough space to justify splitting
				words.addFirst( word );
				return line;
			}
			else {
				// the word doesn't fit, and we're going to split it
				String start = word.substring( 0, space - 1 ) + "-";
				String end = "-" + word.substring( space - 1 );
				line.add( start );
				words.addFirst( end );
				return line;
			}
		}

		return line;
	}

	/**
	 * Performs word wrapping
	 *
	 * @param width      The desired line length
	 * @param splitGrace When deciding whether or not to split a word, if either
	 *                   fragment is smaller than this value then we try to avoid
	 *                   the split
	 * @param text       The text to split into lines
	 * @return The input text, split into lines of the appropriate length
	 */
	public static Deque<String> wrap( int width, int splitGrace, String text ) {
		return wrap( width, splitGrace, words( text ) )
				.stream()
				.map( ll -> String.join( " ", ll ) )
				.collect( toCollection( ArrayDeque::new ) );
	}

	/**
	 * Extracts the wrappable words from a string
	 *
	 * @param content The content to extract words from
	 * @return The words
	 * @see #wrap(int, int, Deque)
	 */
	public static Deque<String> words( String content ) {
		return Stream.of( content.split( " " ) )
				.map( w -> w.replace( "\t", "  " ) )
				.collect( toCollection( ArrayDeque::new ) );
	}

	/**
	 * Trims a string to fit within a width, marking the deletia with an ellipsis
	 *
	 * @param width The desired width
	 * @param text  The string to trim
	 * @return The supplied word, trimmed if required
	 */
	public static String ellipsise( int width, String text ) {
		if( text.length() > width ) {
			return text.substring( 0, width - 1 ) + "…";
		}
		return text;
	}
}
