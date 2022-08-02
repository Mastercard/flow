package com.mastercard.test.flow.builder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Exercising {@link Sets} functionality
 */
@SuppressWarnings("static-method")
class SetsTest {

	/**
	 * Bumps pitest coverage
	 *
	 * @throws Exception reflection failure
	 */
	@Test
	void constructor() throws Exception {
		// pitest complains that we don't exercise the private constructor, so...
		Constructor<Sets> c = Sets.class.getDeclaredConstructor();
		assertTrue( Modifier.isPrivate( c.getModifiers() ) );
		c.setAccessible( true );
		c.newInstance();
		c.setAccessible( false );
	}

	/**
	 * Exercising {@link Sets#clear()}
	 */
	@Test
	void clear() {
		Set<String> set = new HashSet<>( Arrays.asList( "abc".split( "" ) ) );

		Assertions.assertEquals( "[a, b, c]", set.toString() );

		Sets.<String>clear().accept( set );

		Assertions.assertEquals( "[]", set.toString() );
	}

	/**
	 * Exercising {@link Sets#add(Object...)}
	 */
	@Test
	void add() {
		Set<String> set = new HashSet<>( Arrays.asList( "abc".split( "" ) ) );

		Assertions.assertEquals( "[a, b, c]", set.toString() );

		Sets.add( "d", "e" ).accept( set );

		Assertions.assertEquals( "[a, b, c, d, e]", set.toString() );
	}

	/**
	 * Exercising {@link Sets#remove(Object...)}
	 */
	@Test
	void remove() {
		Set<String> set = new HashSet<>( Arrays.asList( "abc".split( "" ) ) );

		Assertions.assertEquals( "[a, b, c]", set.toString() );

		Sets.remove( "a", "c" ).accept( set );

		Assertions.assertEquals( "[b]", set.toString() );
	}

	/**
	 * Exercising {@link Sets#set(Object...)}
	 */
	@Test
	void set() {
		Set<String> set = new HashSet<>( Arrays.asList( "abc".split( "" ) ) );

		Assertions.assertEquals( "[a, b, c]", set.toString() );

		Sets.set( "c", "d" ).accept( set );

		Assertions.assertEquals( "[c, d]", set.toString() );
	}
}
