package com.mastercard.test.flow.msg.sql;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
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
	 * Shows that types are preserved in the round-trip to byte content
	 */
	@Test
	void types() {

		Result result = new Result(
				"bit", "tinyint", "smallint", "integer", "bigint", "biggerint",
				"real", "double", "decimal",
				"character", "varchar",
				"blob",
				"date", "time", "timestamp",
				"value" )
						.set( "0:0", true )
						.set( "0:1", (byte) 1 )
						.set( "0:2", (short) 2 )
						.set( "0:3", 3 )
						.set( "0:4", 4L )
						.set( "0:5", new BigInteger( "5" ) )
						.set( "0:6", 6.0f )
						.set( "0:7", 7.0 )
						.set( "0:8", new BigDecimal( "8.0" ) )
						.set( "0:9", 'a' )
						.set( "0:10", "bcd" )
						.set( "0:11", "bytes".getBytes( UTF_8 ) )
						.set( "0:12", Date.valueOf( "1970-01-02" ) )
						.set( "0:13", Time.valueOf( "03:04:06" ) )
						.set( "0:14", Timestamp.valueOf( "1970-01-02 03:04:07.0" ) )
						.set( "0:15", null );

		assertEquals( ""
				+ " --- Row 0 ---\n"
				+ "       bit : true\n"
				+ "   tinyint : 1\n"
				+ "  smallint : 2\n"
				+ "   integer : 3\n"
				+ "    bigint : 4\n"
				+ " biggerint : 5\n"
				+ "      real : 6.0\n"
				+ "    double : 7.0\n"
				+ "   decimal : 8.0\n"
				+ " character : a\n"
				+ "   varchar : bcd\n"
				+ "      blob : Ynl0ZXM=\n"
				+ "      date : 1970-01-02\n"
				+ "      time : 03:04:06\n"
				+ " timestamp : 1970-01-02 03:04:07.0\n"
				+ "     value : null",
				result.assertable() );

		Result parsed = new Result().peer( result.content() );

		assertEquals( true, parsed.get( "0:0" ) );
		assertEquals( (byte) 1, parsed.get( "0:1" ) );
		assertEquals( (short) 2, parsed.get( "0:2" ) );
		assertEquals( 3, parsed.get( "0:3" ) );
		assertEquals( 4L, parsed.get( "0:4" ) );
		assertEquals( new BigInteger( "5" ), parsed.get( "0:5" ) );
		assertEquals( 6.0f, parsed.get( "0:6" ) );
		assertEquals( 7.0, parsed.get( "0:7" ) );
		assertEquals( new BigDecimal( "8.0" ), parsed.get( "0:8" ) );
		assertEquals( 'a', parsed.get( "0:9" ) );
		assertEquals( "bcd", parsed.get( "0:10" ) );
		assertArrayEquals( "bytes".getBytes( UTF_8 ), (byte[]) parsed.get( "0:11" ) );
		assertEquals( Date.valueOf( "1970-01-02" ), parsed.get( "0:12" ) );
		assertEquals( Time.valueOf( "03:04:06" ), parsed.get( "0:13" ) );
		assertEquals( Timestamp.valueOf( "1970-01-02 03:04:07.0" ), parsed.get( "0:14" ) );
		assertEquals( null, parsed.get( "0:15" ) );
	}
}
