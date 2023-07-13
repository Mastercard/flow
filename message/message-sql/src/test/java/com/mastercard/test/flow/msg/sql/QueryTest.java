package com.mastercard.test.flow.msg.sql;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Exercises {@link Query}
 */
@SuppressWarnings("static-method")
class QueryTest {

	/**
	 * Unrecognised content is noisily rejected
	 */
	@Test
	void badBytes() {
		byte[] bytes = "{]".getBytes( UTF_8 );
		Query query = new Query( "" ).peer( bytes );
		IllegalArgumentException e = Assertions.assertThrows( IllegalArgumentException.class,
				() -> query.asHuman() );
		Assertions.assertEquals( "Failed to parse '{]' ([123, 93])", e.getMessage() );
	}

	/**
	 * Exercises the convenient bind-variable setters
	 */
	@Test
	void binds() {
		String query = "SELECT foo FROM table WHERE a = ? AND b = ? and c = ?";
		String expected = ""
				+ "Query:\n"
				+ "SELECT\n"
				+ "  foo\n"
				+ "FROM\n"
				+ "  table\n"
				+ "WHERE\n"
				+ "  a = ?\n"
				+ "  AND b = ?\n"
				+ "  and c = ?\n"
				+ "Bind variables:\n"
				+ "  1 : abc\n"
				+ "  2 : def\n"
				+ "  3 : ghi";

		assertEquals(
				expected,
				new Query( query )
						.binds( "abc", "def", "ghi" )
						.asHuman(),
				"varargs" );

		assertEquals(
				expected,
				new Query( query )
						.binds( Arrays.asList( "abc", "def", "ghi" ) )
						.asHuman(),
				"list" );

		assertEquals(
				expected,
				new Query( query )
						.binds( Stream.of( "abc", "def", "ghi" ) )
						.asHuman(),
				"list" );

		assertEquals(
				expected,
				new Query( query )
						.binds( new TreeSet<>( Arrays.asList( "ghi", "abc", "def" ) ) )
						.asHuman(),
				"set" );
	}

	/**
	 * Illustrates human-readable formatting
	 */
	@Test
	void asHuman() {
		Query query = new Query( "SELECT foo, bar FROM table "
				+ "LEFT JOIN lj_table ON ( table.foo = lj_table.foo ) WHERE column_a = ? AND column_b = ?" )
						.set( "1", "string_value" )
						.set( "2", 2 );

		assertEquals( ""
				+ "Query:\n"
				+ "SELECT\n"
				+ "  foo,\n"
				+ "  bar\n"
				+ "FROM\n"
				+ "  table\n"
				+ "  LEFT JOIN lj_table ON (table.foo = lj_table.foo)\n"
				+ "WHERE\n"
				+ "  column_a = ?\n"
				+ "  AND column_b = ?\n"
				+ "Bind variables:\n"
				+ "  1 : string_value\n"
				+ "  2 : 2",
				query.asHuman() );
	}

	/**
	 * Demonstrates field masking
	 */
	@Test
	void masking() {
		Unpredictable rng = () -> "rng";
		Query query = new Query( "SELECT foo FROM bar WHERE baz = ?" )
				.set( "1", "random!" )
				.masking( rng, m -> m.replace( "1", "_MASKED_" ) );

		assertEquals( ""
				+ "Query:\n"
				+ "SELECT\n"
				+ "  foo\n"
				+ "FROM\n"
				+ "  bar\n"
				+ "WHERE\n"
				+ "  baz = ?\n"
				+ "Bind variables:\n"
				+ "  1 : _MASKED_",
				query.assertable( rng ) );

		Query child = query.child();

		assertEquals( query.assertable( rng ), child.assertable( rng ),
				"Masks are inherited" );
	}

	/**
	 * Exercising field deletion
	 */
	@Test
	void delete() {
		Query query = new Query( "SELECT foo FROM bar WHERE baz = ?" )
				.set( "1", "value" );
		assertEquals( ""
				+ "Query:\n"
				+ "SELECT\n"
				+ "  foo\n"
				+ "FROM\n"
				+ "  bar\n"
				+ "WHERE\n"
				+ "  baz = ?\n"
				+ "Bind variables:\n"
				+ "  1 : value",
				query.asHuman() );

		query.set( "1", AbstractMessage.DELETE );
		assertEquals( ""
				+ "Query:\n"
				+ "SELECT\n"
				+ "  foo\n"
				+ "FROM\n"
				+ "  bar\n"
				+ "WHERE\n"
				+ "  baz = ?\n"
				+ "Bind variables:",
				query.asHuman() );
	}

	/**
	 * Exercising field listing
	 */
	@Test
	void fields() {
		Query query = new Query( "SELECT foo, bar FROM table "
				+ "LEFT JOIN lj_table ON ( table.foo = lj_table.foo )"
				+ " WHERE column_a = ? AND column_b = ?" )
						.set( "1", "string_value" )
						.set( "2", 2 );
		assertEquals( "[1, 2, sql]", query.fields().toString() );
	}

	/**
	 * Exercises extraction of query data
	 */
	@Test
	void get() {
		Query query = new Query( "SELECT foo FROM bar WHERE baz = ?" )
				.set( "1", "abc" )
				.set( "2", "def" )
				.set( "3", "ghi" )
				.set( "4", "jkl" )
				.set( "6", "mno" );

		List<Object> bv = new ArrayList<>();
		String sql = query.get( bv );
		assertEquals( "SELECT foo FROM bar WHERE baz = ?", sql );
		assertEquals( "[abc, def, ghi, jkl]", bv.toString() );
	}

	/**
	 * Exercising more than 10 bind variables
	 */
	@Test
	void manyBinds() {
		Query query = new Query( "SELECT 1" );
		for( int i = 0; i < 15; i++ ) {
			query.set( String.valueOf( i ), i );
		}

		assertEquals( "Query:\n"
				+ "SELECT\n"
				+ "  1\n"
				+ "Bind variables:\n"
				+ "  0 : 0\n"
				+ "  1 : 1\n"
				+ "  2 : 2\n"
				+ "  3 : 3\n"
				+ "  4 : 4\n"
				+ "  5 : 5\n"
				+ "  6 : 6\n"
				+ "  7 : 7\n"
				+ "  8 : 8\n"
				+ "  9 : 9\n"
				+ " 10 : 10\n"
				+ " 11 : 11\n"
				+ " 12 : 12\n"
				+ " 13 : 13\n"
				+ " 14 : 14", query.asHuman() );
	}

	/**
	 * Shows that types are preserved in the round-trip to byte content
	 */
	@Test
	void types() {

		Query query = new Query( "SELECT 1" )
				.set( "0", true )
				.set( "1", (byte) 1 )
				.set( "2", (short) 2 )
				.set( "3", 3 )
				.set( "4", 4.0f )
				.set( "5", 5L )
				.set( "6", 6.0 )
				.set( "7", 'a' )
				.set( "8", "bcd" )
				.set( "9", new BigInteger( "9" ) )
				.set( "10", new BigDecimal( "10.0" ) )
				.set( "11", "bytes".getBytes( UTF_8 ) )
				.set( "12", Date.valueOf( "1970-01-02" ) )
				.set( "13", Time.valueOf( "03:04:06" ) )
				.set( "14", Timestamp.valueOf( "1970-01-02 03:04:07.0" ) )
				.set( "15", null );

		assertEquals( ""
				+ "Query:\n"
				+ "SELECT\n"
				+ "  1\n"
				+ "Bind variables:\n"
				+ "  0 : true\n"
				+ "  1 : 1\n"
				+ "  2 : 2\n"
				+ "  3 : 3\n"
				+ "  4 : 4.0\n"
				+ "  5 : 5\n"
				+ "  6 : 6.0\n"
				+ "  7 : a\n"
				+ "  8 : bcd\n"
				+ "  9 : 9\n"
				+ " 10 : 10.0\n"
				+ " 11 : Ynl0ZXM=\n"
				+ " 12 : 1970-01-02\n"
				+ " 13 : 03:04:06\n"
				+ " 14 : 1970-01-02 03:04:07.0\n"
				+ " 15 : null",
				query.assertable() );

		Query parsed = new Query( "" ).peer( query.content() );

		assertEquals( "SELECT 1", parsed.get( Query.SQL ) );
		assertEquals( true, parsed.get( "0" ) );
		assertEquals( (byte) 1, parsed.get( "1" ) );
		assertEquals( (short) 2, parsed.get( "2" ) );
		assertEquals( 3, parsed.get( "3" ) );
		assertEquals( 4.0f, parsed.get( "4" ) );
		assertEquals( 5L, parsed.get( "5" ) );
		assertEquals( 6.0, parsed.get( "6" ) );
		assertEquals( 'a', parsed.get( "7" ) );
		assertEquals( "bcd", parsed.get( "8" ) );
		assertEquals( new BigInteger( "9" ), parsed.get( "9" ) );
		assertEquals( new BigDecimal( "10.0" ), parsed.get( "10" ) );
		assertArrayEquals( "bytes".getBytes( UTF_8 ), (byte[]) parsed.get( "11" ) );
		assertEquals( Date.valueOf( "1970-01-02" ), parsed.get( "12" ) );
		assertEquals( Time.valueOf( "03:04:06" ), parsed.get( "13" ) );
		assertEquals( Timestamp.valueOf( "1970-01-02 03:04:07.0" ), parsed.get( "14" ) );
		assertEquals( null, parsed.get( "15" ) );
	}

	/**
	 * Exercises mask inheritance to peers
	 */
	@Test
	void peer() {
		Unpredictable rng = () -> "rng";
		Query query = new Query( "SELECT foo FROM bar WHERE baz = ?" )
				.set( "1", "random!" )
				.masking( rng, m -> m.replace( "1", "_MASKED_" ) );

		Query peer = query
				.peer( new Query( "SELECT oof FROM rab WHERE zab = ?" )
						.set( "1", "!modnar" )
						.content() );

		assertEquals( ""
				+ "Query:\n"
				+ "SELECT\n"
				+ "  oof\n"
				+ "FROM\n"
				+ "  rab\n"
				+ "WHERE\n"
				+ "  zab = ?\n"
				+ "Bind variables:\n"
				+ "  1 : _MASKED_",
				peer.assertable( rng ) );
	}

	/**
	 * Shows what happens when serialisation fails
	 */
	@Test
	void badContent() {
		Query query = new Query( "SELECT 1" ) {
			@Override
			protected Object validateValueType( String field, Object value ) {
				// allow everything
				return value;
			}
		};
		query.set( "1", new Object() );

		assertThrows( IllegalStateException.class, () -> query.content() );
	}

}
