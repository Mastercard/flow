package com.mastercard.test.flow.util;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Functions for working with the {@link String} {@link Set}s used to hold tag
 * data
 */
public class Tags {

	private Tags() {
		// no instances
	}

	/**
	 * Builds the empty set of tags
	 *
	 * @return The empty set
	 */
	public static Set<String> empty() {
		return tags();
	}

	/**
	 * Builds a set of tags
	 *
	 * @param tags tag values
	 * @return A {@link Set} of those values
	 */
	public static Set<String> tags( String... tags ) {
		return Stream.of( tags ).collect( Collectors.toCollection( TreeSet::new ) );
	}

	/**
	 * Set intersection test
	 *
	 * @param a A set
	 * @param b A set
	 * @return <code>true</code> if the two sets have at least one member in common
	 */
	public static boolean intersects( Set<String> a, Set<String> b ) {
		for( String t : a ) {
			if( b.contains( t ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determines if a tagged item passes an include/exclude tag filter
	 *
	 * @param item    The set of tags on an item
	 * @param include The set of tags that items must have
	 * @param exclude The set of tags that items must not have
	 * @return <code>true</code> if the item passes the filter
	 */
	public static boolean filter( Set<String> item, Set<String> include, Set<String> exclude ) {
		return !Tags.intersects( item, exclude ) && item.containsAll( include );
	}

	/**
	 * Extracts portions of tags in a set
	 *
	 * @param tags   Some tags
	 * @param prefix A prefix
	 * @return The remainder of those tags that start with the prefix
	 */
	public static Stream<String> suffices( Set<String> tags, String prefix ) {
		return tags.stream()
				.filter( t -> t.startsWith( prefix ) )
				.map( t -> t.substring( prefix.length() ) );
	}

	/**
	 * Extracts a portion of the first matching tag in a set
	 *
	 * @param tags   Some tags
	 * @param prefix A prefix
	 * @return The remainder of the first tag that starts with the prefix
	 */
	public static Optional<String> suffix( Set<String> tags, String prefix ) {
		return suffices( tags, prefix ).findFirst();
	}

	/**
	 * Builds an operation to empty a tag set
	 *
	 * @return Empties the tags
	 */
	public static Consumer<Set<String>> clear() {
		return Set::clear;
	}

	/**
	 * Builds an operation to add to a tag set
	 *
	 * @param tags The values to add
	 * @return Adds the supplied tags
	 */
	public static Consumer<Set<String>> add( String... tags ) {
		return s -> Collections.addAll( s, tags );
	}

	/**
	 * Builds an operation to remove from a tag set
	 *
	 * @param tags The values to remove
	 * @return Removes the supplied tags
	 */
	public static Consumer<Set<String>> remove( String... tags ) {
		return s -> Stream.of( tags ).forEach( s::remove );
	}

	/**
	 * Builds an operation to define a tag set
	 *
	 * @param tags The values to set
	 * @return Sets the set membership to be exactly the supplied tags
	 */
	public static Consumer<Set<String>> set( String... tags ) {
		return clear().andThen( add( tags ) );
	}
}
