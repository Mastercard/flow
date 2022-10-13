package com.mastercard.test.flow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.util.Option.Builder;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Exercises {@link Option}
 */
@SuppressWarnings("static-method")
class OptionTest {

	private static Option TSTOPT = new Option() {

		@Override
		public String property() {
			return "tstopt";
		}

		@Override
		public String name() {
			return "Test option";
		}

		@Override
		public String description() {
			return "Used to exercise the default implementations methods in unit tests";
		}
	};

	/**
	 * Removes the test property before each test
	 */
	@BeforeEach
	void reset() {
		TSTOPT.clear();
	}

	/**
	 * Exercises {@link Option#value()}
	 */
	@Test
	void value() {
		Assertions.assertEquals( null, TSTOPT.value() );
		TSTOPT.set( "foo" );
		Assertions.assertEquals( "foo", TSTOPT.value() );
	}

	/**
	 * Exercises {@link Option#clear()}
	 */
	@Test
	void clear() {
		TSTOPT.set( "foobar" );
		String old = TSTOPT.clear();

		Assertions.assertEquals( "foobar", old );
		Assertions.assertEquals( null, TSTOPT.value() );
	}

	/**
	 * Exercises {@link Option#set(String)}
	 */
	@Test
	void set() {
		String old = TSTOPT.set( "foobar" );
		Assertions.assertEquals( null, old, "pre-existing value" );
		Assertions.assertEquals( "foobar", TSTOPT.value(), "after first set" );

		old = TSTOPT.set( "raboof" );
		Assertions.assertEquals( "foobar", old, "first value" );
		Assertions.assertEquals( "raboof", TSTOPT.value(), "after second set" );

		old = TSTOPT.set( null );
		Assertions.assertEquals( "raboof", old, "second value" );
		Assertions.assertEquals( null, TSTOPT.value(), "after third set" );
	}

	/**
	 * Exercises {@link Option#orElse(java.util.function.Supplier)}
	 */
	@Test
	void orElse() {
		Assertions.assertEquals( "default", TSTOPT.orElse( () -> "default" ) );
		TSTOPT.set( "foo" );
		Assertions.assertEquals( "foo", TSTOPT.orElse( () -> "default" ) );
	}

	/**
	 * Exercises {@link Option#isTrue()}
	 */
	@Test
	void isTrue() {
		Assertions.assertEquals( false, TSTOPT.isTrue() );
		TSTOPT.set( "foo" );
		Assertions.assertEquals( false, TSTOPT.isTrue() );
		TSTOPT.set( "false" );
		Assertions.assertEquals( false, TSTOPT.isTrue() );
		TSTOPT.set( "TRUE" );
		Assertions.assertEquals( false, TSTOPT.isTrue() );
		TSTOPT.set( "true" );
		Assertions.assertEquals( true, TSTOPT.isTrue() );
	}

	/**
	 * Exercises {@link Option#asList()} and {@link Option#asList(String)}
	 */
	@Test
	void asList() {
		Assertions.assertEquals( "",
				TSTOPT.asList().collect( Collectors.joining( " " ) ) );

		TSTOPT.set( "a,b,c" );
		Assertions.assertEquals( "a b c",
				TSTOPT.asList().collect( Collectors.joining( " " ) ) );

		TSTOPT.set( "a|b|c" );
		Assertions.assertEquals( "a b c",
				TSTOPT.asList( "\\|" ).collect( Collectors.joining( " " ) ) );

		TSTOPT.set( "abcdefgh" );
		Assertions.assertEquals( " bcd fgh",
				TSTOPT.asList( "[aeiou]+" ).collect( Collectors.joining( " " ) ) );
		Assertions.assertEquals( "abcdefgh",
				TSTOPT.asList().collect( Collectors.joining( " " ) ) );
	}

	/**
	 * Exercises {@link Option#asInt()}
	 */
	@Test
	void asInt() {
		// valid value
		Option valid = new Builder().property( "tstopt" );
		valid.set( "5" );
		assertEquals( 5, valid.asInt() );

		// invalid value, fall back to valid default
		Option fallback = new Builder().property( "tstopt" ).defaultValue( "8" );
		fallback.set( "not an int" );
		assertEquals( 8, fallback.asInt() );

		// invalid value, no default
		Option noDefault = new Builder().property( "tstopt" );
		noDefault.set( "not an int" );
		assertEquals( "null",
				assertThrows( NumberFormatException.class, () -> noDefault.asInt() )
						.getMessage() );

		// invalid value, invalid default
		Option badDefault = new Builder().property( "tstopt" ).defaultValue( "not an int either" );
		badDefault.set( "not an int" );
		assertEquals( "For input string: \"not an int either\"",
				assertThrows( NumberFormatException.class, () -> badDefault.asInt() )
						.getMessage() );
	}

	/**
	 * Exercises {@link Option#temporarily(String)}
	 */
	@Test
	void temporarily() {
		assertEquals( null, TSTOPT.value(), "initial" );

		try( Temporary outer = TSTOPT.temporarily( "abc" ) ) {
			assertEquals( "abc", TSTOPT.value(), "before inner" );

			try( Temporary inner = TSTOPT.temporarily( "def" ) ) {
				assertEquals( "def", TSTOPT.value(), "inner" );
			}

			assertEquals( "abc", TSTOPT.value(), "after inner" );
		}

		assertEquals( null, TSTOPT.value(), "final" );
	}

	/**
	 * Exercises {@link Builder}
	 */
	@Test
	void builder() {
		Builder b = new Builder()
				.name( "name" )
				.description( "description" )
				.property( "property" )
				.defaultValue( "default" );
		Assertions.assertEquals( "name", b.name() );
		Assertions.assertEquals( "description", b.description() );
		Assertions.assertEquals( "property", b.property() );
		Assertions.assertEquals( "default", b.defaultValue() );
	}
}
