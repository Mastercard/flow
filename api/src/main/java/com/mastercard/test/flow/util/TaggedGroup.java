package com.mastercard.test.flow.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Encapsulates information about a group of tagged items
 */
public class TaggedGroup {
	/**
	 * Tag values that at least one member of the group has
	 */
	private final Set<String> intersection = new TreeSet<>();
	/**
	 * Tag values that all members of the group hold
	 */
	private final Set<String> union = new TreeSet<>();

	/**
	 * @param intersection The tag values that all members of the group share
	 */
	public TaggedGroup( String... intersection ) {
		Collections.addAll( this.intersection, intersection );
		union.addAll( this.intersection );
	}

	/**
	 * @param intersection The tag values that all members of the group share
	 */
	public TaggedGroup( Collection<String> intersection ) {
		this.intersection.addAll( intersection );
		union.addAll( intersection );
	}

	/**
	 * Copy constructor
	 *
	 * @param basis the group to copy
	 */
	public TaggedGroup( TaggedGroup basis ) {
		intersection.addAll( basis.intersection );
		union.addAll( basis.union );
	}

	/**
	 * Adds values to the union set
	 *
	 * @param extra The tag values held by at least one member of the group
	 * @return <code>this</code>
	 */
	public TaggedGroup union( String... extra ) {
		for( String tag : extra ) {
			if( intersection.contains( tag ) ) {
				throw new IllegalArgumentException(
						String.format( "Tag '%s' is already in the intersection", tag ) );
			}
			union.add( tag );
		}
		return this;
	}

	/**
	 * Group compatibility query. Note that it's perfectly possible for a group to
	 * pass the filter while none of the members do.
	 *
	 * @param include The set of tags that items must have
	 * @param exclude The set of tags that items must not have
	 * @return <code>true</code> if the union contains <i>all</i> of the include
	 *         tags, and the intersection contains <i>none</i> of the exclude tags
	 */
	public boolean matches( Set<String> include, Set<String> exclude ) {
		return union.containsAll( include )
				&& !Tags.intersects( intersection, exclude );
	}

	/**
	 * Intersection set accessor
	 *
	 * @return A stream of all tag values held by every member of the group
	 */
	public Stream<String> intersection() {
		return intersection.stream();
	}

	/**
	 * Union set accessor
	 *
	 * @return A stream of all tag values held by any members of the group
	 */
	public Stream<String> union() {
		return union.stream();
	}

	/**
	 * Updates this {@link TaggedGroup} to include the other
	 *
	 * @param other The values to combine with
	 * @return <code>this</code>
	 */
	public TaggedGroup combine( TaggedGroup other ) {
		intersection.retainAll( other.intersection );
		union.addAll( other.union );
		return this;
	}

	/**
	 * Tests if union and intersection sets are empty
	 *
	 * @return <code>true</code> if this group contain no tags
	 */
	public boolean isEmpty() {
		return intersection.isEmpty() && union.isEmpty();
	}

	/**
	 * Tests if any member of the group bears the supplied tag
	 *
	 * @param tag A tag value
	 * @return <code>true</code> if the group contains that tag
	 */
	public boolean contains( String tag ) {
		return union.contains( tag );
	}

	@Override
	public String toString() {
		return "∩" + intersection.toString() + "⋃" + union.toString();
	}
}
