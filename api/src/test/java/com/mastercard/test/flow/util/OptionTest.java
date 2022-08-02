package com.mastercard.test.flow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.util.Option.Builder;

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
		TSTOPT.set( "foobar" );
		String old = TSTOPT.set( "raboof" );

		Assertions.assertEquals( "foobar", old );
		Assertions.assertEquals( "raboof", TSTOPT.value() );
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
		Option badDefault = new Builder().property( "tstopt" ).defaultValue( "no an int either" );
		badDefault.set( "not an int" );
		assertEquals( "For input string: \"no an int either\"",
				assertThrows( NumberFormatException.class, () -> badDefault.asInt() )
						.getMessage() );
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
