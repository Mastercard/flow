package com.mastercard.test.flow.msg.sql;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Exercises {@link Result}
 */
@SuppressWarnings("static-method")
class ResultTest {

	/**
	 * Unrecognised content is noisily rejected
	 */
	@Test
	void badBytes() {
		byte[] bytes = "{]".getBytes( UTF_8 );
		Result result = new Result( "" ).peer( bytes );
		IllegalStateException e = Assertions.assertThrows( IllegalStateException.class,
				() -> result.asHuman() );
		Assertions.assertEquals( ""
				+ "Failed to parse\n"
				+ "{]\n"
				+ "[123, 93]", e.getMessage() );
	}

	/**
	 * Human-readable format
	 */
	@Test
	void asHuman() {
		Result res = new Result( "abc", "def", "longer" )
				.set( "0:0", "abc value" )
				.set( "1:1", "def value" )
				.set( "2", Arrays.asList( "g", "h", "i" ) );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ "    abc : abc value\n"
				+ "    def : null\n"
				+ " longer : null\n"
				+ " --- Row 1 ---\n"
				+ "    abc : null\n"
				+ "    def : def value\n"
				+ " longer : null\n"
				+ " --- Row 2 ---\n"
				+ "    abc : g\n"
				+ "    def : h\n"
				+ " longer : i",
				res.asHuman() );
	}

	/**
	 * Demonstrates field masking
	 */
	@Test
	void masking() {
		Unpredictable rng = () -> "rng";
		Result result = new Result( "abc" )
				.set( "0:0", "random!" )
				.masking( rng, m -> m.replace( "0:0", "_MASKED_" ) );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ " abc : _MASKED_",
				result.assertable( rng ) );

		Result child = result.child();

		assertEquals( result.assertable( rng ), child.assertable( rng ),
				"Masks are inherited" );
	}

	/**
	 * Field deletion
	 */
	@Test
	void delete() {
		Result res = new Result( "abc", "def", "longer" )
				.set( "0:0", "abc value" )
				.set( "1:1", "def value" )
				.set( "2:2", "longer value" );

		res.set( "1:1", AbstractMessage.DELETE )
				.set( "2", AbstractMessage.DELETE )
				.set( "3", AbstractMessage.DELETE )
				.set( "4.5", AbstractMessage.DELETE );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ "    abc : abc value\n"
				+ "    def : null\n"
				+ " longer : null\n"
				+ " --- Row 1 ---\n"
				+ "    abc : null\n"
				+ "    def : null\n"
				+ " longer : null",
				res.asHuman() );
	}

	/**
	 * Exercising field listing
	 */
	@Test
	void fields() {
		Result res = new Result( "abc", "def", "longer" )
				.set( "0:0", "a" )
				.set( "1:1", "b" )
				.set( "2:2", "c" );
		assertEquals( "[0:0, 1:1, 2:2, columns]", res.fields().toString() );
	}

	/**
	 * Exercise field population
	 */
	@Test
	void set() {

		Result res = new Result( "abc", "def" )
				.set( "0", Arrays.asList( "a", "b" ) );

		res.set( Result.COLUMNS, "ghi,ijk" )
				.set( "1", Arrays.asList( "c", "d", "e" ) )
				.set( "2:1", "f" )
				.set( "2", Arrays.asList( "g" ) );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ " ghi : a\n"
				+ " ijk : b\n"
				+ " --- Row 1 ---\n"
				+ " ghi : c\n"
				+ " ijk : d\n"
				+ " --- Row 2 ---\n"
				+ " ghi : g\n"
				+ " ijk : null",
				res.asHuman() );

		res.set( "2", AbstractMessage.DELETE );
		res.set( "1:1", AbstractMessage.DELETE );
		res.set( "2", AbstractMessage.DELETE );
		res.set( "2:0", AbstractMessage.DELETE );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ " ghi : a\n"
				+ " ijk : b\n"
				+ " --- Row 1 ---\n"
				+ " ghi : c\n"
				+ " ijk : null",
				res.asHuman() );

		Object o = new Object();
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> res.set( "0:0", o ) );
		assertTrue( iae.getMessage().startsWith( "Field '0:0' - Possibly-mutable value type" ),
				iae.getMessage() );

		List<Object> l = Arrays.asList( "1", new Object() );
		iae = assertThrows( IllegalArgumentException.class,
				() -> res.set( "0", l ) );
		assertTrue( iae.getMessage().startsWith( "Field '0:1' - Possibly-mutable value type" ),
				iae.getMessage() );
	}

	/**
	 * Exercising field access
	 */
	@Test
	void get() {
		Result res = new Result( "abc", "def", "longer" )
				.set( "0:0", "a" )
				.set( "1:1", "b" )
				.set( "2:2", "c" );
		assertEquals( "[0:0, 1:1, 2:2, columns]", res.fields().toString() );
		assertEquals( "[abc, def, longer]", res.get( Result.COLUMNS ).toString() );
		assertEquals( "a", res.get( "0:0" ) );
		assertEquals( "b", res.get( "1:1" ) );
		assertEquals( "c", res.get( "2:2" ) );
		assertEquals( "[null, null, c]", res.get( "2" ).toString() );
		assertEquals( null, res.get( "3:3" ) );
		assertEquals( null, res.get( "3" ) );

		assertEquals( "[{abc=a}, {def=b}, {longer=c}]", res.get().toString() );
	}

	/**
	 * Exercises mask inheritance to peers
	 */
	@Test
	void peer() {
		Unpredictable rng = () -> "rng";
		Result res = new Result( "abc" )
				.set( "0:0", "a" )
				.masking( rng, m -> m.replace( "0:0", "_MASKED_" ) );

		Result peer = res
				.peer( new Result( "abc" )
						.set( "0:0", "b" )
						.content() );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ " abc : _MASKED_",
				peer.assertable( rng ) );
	}

	/**
	 * Shows what happens when serialisation fails
	 */
	@Test
	void badContent() {
		Result res = new Result( "abc" ) {
			@Override
			protected Object validateValueType( String field, Object value ) {
				// allow everything
				return value;
			}
		};

		res.set( "0:0", new Object() );

		assertThrows( IllegalStateException.class, () -> res.content() );
	}

	/**
	 * Demonstrates encoding of byte array result values
	 */
	@Test
	void bytes() {
		Result res = new Result( "byte_column" )
				.set( "0:0", "value".getBytes( UTF_8 ) );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ " byte_column : bytes: dmFsdWU=",
				res.assertable() );
	}
}
