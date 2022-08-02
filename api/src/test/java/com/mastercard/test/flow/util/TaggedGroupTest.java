package com.mastercard.test.flow.util;

import static com.mastercard.test.flow.util.Tags.tags;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link TaggedGroup}
 */
@SuppressWarnings("static-method")
class TaggedGroupTest {

	/**
	 * Exercising constructors
	 */
	@Test
	void construct() {
		TaggedGroup a = new TaggedGroup( Collections.singleton( "foo" ) ).union( "bar" );
		TaggedGroup b = new TaggedGroup( "foo" ).union( "bar" );
		assertEquals( "∩[foo]⋃[bar, foo]", a.toString() );
		assertEquals( a.toString(), b.toString() );
	}

	/**
	 * Exercising group copy constructor
	 */
	@Test
	void copy() {
		TaggedGroup a = new TaggedGroup( "a" ).union( "b" );
		assertEquals( "∩[a]⋃[a, b]", a.toString() );
		TaggedGroup b = new TaggedGroup( a );
		assertEquals( "∩[a]⋃[a, b]", b.toString() );
	}

	/**
	 * Exercises build and retrieving the tag union
	 */
	@Test
	void union() {
		TaggedGroup tg = new TaggedGroup( "b" );

		Exception e = assertThrows(
				IllegalArgumentException.class,
				() -> tg.union( "b" ) );
		assertEquals( "Tag 'b' is already in the intersection", e.getMessage() );

		tg.union( "a", "c" );
		assertEquals( "a,b,c", tg.union().collect( joining( "," ) ) );
	}

	/**
	 * Exercises building and retrieving the tag intersection
	 */
	@Test
	void intersection() {
		TaggedGroup tg = new TaggedGroup( "a" );
		assertEquals( "a", tg.intersection().collect( joining( "," ) ) );
	}

	/**
	 * Exercises matching against include and exclude sets
	 */
	@Test
	void matches() {
		TaggedGroup tg = new TaggedGroup( "b" ).union( "a", "c" );
		assertTrue( tg.matches( tags(), tags() ) );
		// inclusion
		assertTrue( tg.matches( tags( "a" ), tags() ) );
		assertTrue( tg.matches( tags( "b" ), tags() ) );
		assertTrue( tg.matches( tags( "c" ), tags() ) );

		// exclusion
		assertTrue( tg.matches( tags(), tags( "a" ) ) );
		assertTrue( tg.matches( tags(), tags( "c" ) ) );
		assertTrue( tg.matches( tags(), tags( "d" ) ) );

		assertFalse( tg.matches( tags(), tags( "b" ) ) );
		assertFalse( tg.matches( tags( "d" ), tags() ) );
	}

	/**
	 * Tests {@link TaggedGroup#isEmpty()}
	 */
	@Test
	void isEmpty() {
		assertTrue( new TaggedGroup().isEmpty() );
		assertFalse( new TaggedGroup( "a" ).isEmpty() );
		assertFalse( new TaggedGroup( "a" ).union( "b" ).isEmpty() );
		assertFalse( new TaggedGroup().union( "b" ).isEmpty() );
	}

	/**
	 * Exercises group combination
	 */
	@Test
	void combine() {
		TaggedGroup group = new TaggedGroup( "a", "b", "c" ).union( "d", "e" )
				.combine( new TaggedGroup( "b", "c", "d" ).union( "e", "f" ) );

		assertEquals( "∩[b, c]⋃[a, b, c, d, e, f]", group.toString() );
	}

	/**
	 * Exercises {@link TaggedGroup#contains(String)}
	 */
	@Test
	void contains() {
		TaggedGroup group = new TaggedGroup( "a", "b", "c" )
				.union( "d", "e" );

		Stream.of( "a", "b", "c", "d", "e" )
				.forEach( e -> Assertions.assertTrue( group.contains( e ), e ) );

		Stream.of( "f" )
				.forEach( e -> Assertions.assertFalse( group.contains( e ), e ) );
	}
}
