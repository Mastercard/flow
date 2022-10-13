package com.mastercard.test.flow.util;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Exercises the tagset-manipulation methods
 */
@SuppressWarnings("static-method")
class TagsTest {

	/**
	 * Bumps pitest coverage
	 *
	 * @throws Exception reflection failure
	 */
	@Test
	void constructor() throws Exception {
		// pitest complains that we don't exercise the private constructor, so...
		Constructor<Tags> c = Tags.class.getDeclaredConstructor();
		assertTrue( Modifier.isPrivate( c.getModifiers() ) );
		c.setAccessible( true );
		c.newInstance();
		c.setAccessible( false );
	}

	/**
	 * Shows that {@link Tags#empty()} returns an empty mutable set
	 */
	@Test
	void empty() {
		Set<String> tags = Tags.empty();
		Assertions.assertEquals( "[]", tags.toString() );
		tags.add( "foo" );
		Assertions.assertEquals( "[foo]", tags.toString() );
	}

	/**
	 * Shows that {@link Tags#tags(String...)} returns a sorted mutable set
	 */
	@Test
	void tags() {
		Set<String> tags = Tags.tags( "b", "a", "c" );
		Assertions.assertEquals( "[a, b, c]", tags.toString() );
		tags.add( "1" );
		tags.add( "x" );
		Assertions.assertEquals( "[1, a, b, c, x]", tags.toString() );
	}

	/**
	 * Tests set intersection detection
	 */
	@Test
	void intersects() {
		Set<String> abc = Tags.tags( "a", "b", "c" );
		Set<String> cd = Tags.tags( "c", "d" );
		Set<String> def = Tags.tags( "d", "e", "f" );

		assertTrue( Tags.intersects( abc, cd ) );
		assertTrue( Tags.intersects( cd, abc ) );

		assertTrue( Tags.intersects( cd, def ) );
		assertTrue( Tags.intersects( def, cd ) );

		assertFalse( Tags.intersects( abc, def ) );
		assertFalse( Tags.intersects( def, abc ) );
	}

	/**
	 * Tests tag filtering test
	 */
	@Test
	void filter() {
		BiConsumer<String, Boolean> test = ( in, out ) -> {
			Matcher m = Pattern.compile( "(.*)\\|(.*)\\|(.*)" ).matcher( in );
			assertTrue( m.matches(), "bad data " + in );

			List<Set<String>> tags = IntStream.of( 1, 2, 3 )
					.mapToObj( i -> m.group( i ).chars()
							.mapToObj( c -> String.valueOf( (char) c ) )
							.collect( Collectors.toSet() ) )
					.collect( Collectors.toList() );

			assertEquals( out, Tags.filter( tags.get( 0 ), tags.get( 1 ), tags.get( 2 ) ),
					String.format( "\nitem    %s\ninclude %s\nexclude %s",
							tags.get( 0 ), tags.get( 1 ), tags.get( 2 ) ) );
		};

		// input data in "item|include|exclude" format
		test.accept( "||", true );
		test.accept( "abc||", true );

		test.accept( "abc|a|", true );
		test.accept( "abc|b|", true );
		test.accept( "abc|c|", true );
		test.accept( "abc|abc|", true );
		test.accept( "abc|d|", false );

		test.accept( "abc||a", false );
		test.accept( "abc||b", false );
		test.accept( "abc||c", false );
		test.accept( "abc||abc", false );
		test.accept( "abc||d", true );

		test.accept( "abc|abc|def", true );
	}

	/**
	 * Tests the tag-set manipulation functions
	 */
	@Test
	void functions() {
		BiConsumer<Consumer<Set<String>>, String> test = ( in, out ) -> {
			Set<String> tags = Tags.tags( "a", "b", "c" );
			in.accept( tags );
			assertEquals( out, tags.toString() );
		};

		test.accept( Tags.clear(), "[]" );
		test.accept( Tags.add( "d", "e" ), "[a, b, c, d, e]" );
		test.accept( Tags.remove( "b", "e" ), "[a, c]" );
		test.accept( Tags.set( "b", "e" ), "[b, e]" );
	}

	/**
	 * Tests {@link Tags#suffices(Set, String)}
	 */
	@Test
	void suffices() {
		Set<String> abc = Tags.tags( "foo:a", "foo:b", "bar:c" );
		Assertions.assertEquals( "a,b",
				Tags.suffices( abc, "foo:" ).collect( joining( "," ) ) );
		Assertions.assertEquals( "c",
				Tags.suffices( abc, "bar:" ).collect( joining( "," ) ) );
		Assertions.assertEquals( "",
				Tags.suffices( abc, "rab:" ).collect( joining( "," ) ) );
	}

	/**
	 * Tests {@link Tags#suffices(Set, String)}
	 */
	@Test
	void suffix() {
		Set<String> abc = Tags.tags( "foo:a", "foo:b", "bar:c" );
		Assertions.assertEquals( "a", Tags.suffix( abc, "foo:" ).orElse( "not found!" ) );
		Assertions.assertEquals( "c", Tags.suffix( abc, "bar:" ).orElse( "not found!" ) );
		Assertions.assertEquals( "not found!", Tags.suffix( abc, "rab:" ).orElse( "not found!" ) );
	}
}
