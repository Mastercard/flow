package com.mastercard.test.flow.msg.http;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Combinator}
 */
@SuppressWarnings("static-method")
class CombinatorTest {

	/**
	 * Demonstrates the combinations of zero items
	 */
	@Test
	void empty() {
		test( "[]" );
	}

	/**
	 * Demonstrates the combinations of zero items
	 */
	@Test
	void single() {
		test( "[]\n"
				+ "[a]",
				"a" );
	}

	/**
	 * Demonstrates the combinations of zero items
	 */
	@Test
	void pair() {
		test( "[]\n"
				+ "[a]\n"
				+ "[b]\n"
				+ "[a, b]",
				"a", "b" );
	}

	/**
	 * Demonstrates the combinations of three items
	 */
	@Test
	void triple() {
		test( ""
				+ "[]\n"
				+ "[a]\n"
				+ "[b]\n"
				+ "[a, b]\n"
				+ "[c]\n"
				+ "[a, c]\n"
				+ "[b, c]\n"
				+ "[a, b, c]",
				"a", "b", "c" );
	}

	private static void test( String expected, String... items ) {
		assertEquals(
				expected,
				new Combinator<>( TreeSet::new, items )
						.stream()
						.map( String::valueOf )
						.collect( joining( "\n" ) ),
				"Combinations of " + Arrays.toString( items ) );
	}
}
