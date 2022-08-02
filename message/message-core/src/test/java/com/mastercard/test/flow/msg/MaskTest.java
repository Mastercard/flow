package com.mastercard.test.flow.msg;

import static com.mastercard.test.flow.msg.AbstractMessage.DELETE;
import static java.util.stream.Collectors.toSet;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Message;

/**
 * Exercising {@link Mask} operations
 */
@SuppressWarnings("static-method")
class MaskTest {

	/**
	 * Exercises field deletion masking
	 */
	@Test
	void delete() {
		Message msg = Mockito.mock( Message.class );

		Mask msk = new Mask()
				.delete( "a", "b" )
				.delete( Arrays.asList( "c", "d" ) )
				.delete( Stream.of( "e", "f" ) );

		msk.accept( msg );

		Mockito.verify( msg ).set( "a", DELETE );
		Mockito.verify( msg ).set( "b", DELETE );
		Mockito.verify( msg ).set( "c", DELETE );
		Mockito.verify( msg ).set( "d", DELETE );
		Mockito.verify( msg ).set( "e", DELETE );
		Mockito.verify( msg ).set( "f", DELETE );
		Mockito.verifyNoMoreInteractions( msg );
	}

	/**
	 * Exercises fiedl retention
	 */
	@Test
	void retain() {
		Message msg = Mockito.mock( Message.class );
		when( msg.fields() ).thenReturn( Stream.of( "a", "b", "c" )
				.collect( toSet() ) );

		Mask msk = new Mask().retain( "b" );
		msk.accept( msg );

		Mockito.verify( msg ).set( "a", DELETE );
		Mockito.verify( msg ).set( "c", DELETE );
	}

	/**
	 * Exercise field replacement
	 */
	@Test
	void replace() {

		Message msg = Mockito.mock( Message.class );
		Mockito.when( msg.get( "a" ) ).thenReturn( "to be replaced" );

		new Mask()
				.replace( "a", "b" )
				.replace( "c", "d" )
				.accept( msg );

		// a is retrieved and replaced
		Mockito.verify( msg ).get( "a" );
		Mockito.verify( msg ).set( "a", "b" );

		Mockito.verify( msg ).get( "c" );
		// no value for c, so no replacement
		Mockito.verifyNoMoreInteractions( msg );
	}

	/**
	 * Exercising length-aware replacement
	 */
	@Test
	void chars() {
		Message msg = Mockito.mock( Message.class );
		Mockito.when( msg.get( "a" ) ).thenReturn( "to be replaced" );

		new Mask()
				.chars( "a", "x" )
				.accept( msg );

		// a is retrieved and replaced
		Mockito.verify( msg ).get( "a" );
		Mockito.verify( msg ).set( "a", "xxxxxxxxxxxxxx" );
		Mockito.verifyNoMoreInteractions( msg );
	}

	/**
	 * You have to specify <i>something</i>
	 */
	@Test
	void badChars() {
		Assertions.assertThrows( IllegalArgumentException.class,
				() -> new Mask().chars( "a", "" ) );
	}

	/**
	 * Exercising length-aware replacement
	 */
	@Test
	void label() {
		Message msg = Mockito.mock( Message.class );
		Mockito.when( msg.get( "a" ) ).thenReturn( "to be replaced" );

		new Mask()
				.label( "a", "masked" )
				.accept( msg );

		// a is retrieved and replaced
		Mockito.verify( msg ).get( "a" );
		Mockito.verify( msg ).set( "a", "____masked____" );
		Mockito.verifyNoMoreInteractions( msg );
	}

	/**
	 * Exercising regex-based matching
	 */
	@Test
	void match() {
		Message msg = Mockito.mock( Message.class );
		Mockito.when( msg.get( "a" ) ).thenReturn( "to be replaced" );
		Mockito.when( msg.get( "b" ) ).thenReturn( "will not match" );

		new Mask()
				.match( "a", ".*repl.*" )
				.match( "b", ".*\\d+.*" )
				.accept( msg );

		// a is retrieved and replaced
		Mockito.verify( msg ).get( "a" );
		Mockito.verify( msg ).set( "a", "Matches '.*repl.*'" );

		Mockito.verify( msg ).get( "b" );
		// The value for b does not fit the regex, so no replacment
		Mockito.verifyNoMoreInteractions( msg );
	}

	/**
	 * Exercising regular expression capture groups
	 */
	@Test
	void captures() {

		Message msg = Mockito.mock( Message.class );
		Mockito.when( msg.get( "a" ) ).thenReturn( "keep mask retain" );
		Mockito.when( msg.get( "b" ) ).thenReturn( "keep mask retain" );
		Mockito.when( msg.get( "c" ) ).thenReturn( "keep mask retain" );

		new Mask()
				.captures( "a", "[aeiou]" )
				.captures( "b", "k(\\w+)p" )
				.captures( "c", "(\\w+ )mask( \\w+)", "$1####$2 " )
				.accept( msg );

		Mockito.verify( msg ).get( "a" );
		Mockito.verify( msg ).set( "a", "eeaeai" );
		Mockito.verify( msg ).get( "b" );
		Mockito.verify( msg ).set( "b", "ee" );
		Mockito.verify( msg ).get( "c" );
		Mockito.verify( msg ).set( "c", "keep #### retain" );
		Mockito.verifyNoMoreInteractions( msg );
	}
}
