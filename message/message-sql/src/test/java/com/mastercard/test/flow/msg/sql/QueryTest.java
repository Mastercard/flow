package com.mastercard.test.flow.msg.sql;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

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
		IllegalArgumentException e = Assertions.assertThrows( IllegalArgumentException.class,
				() -> new Query( "" ).peer( bytes ).asHuman() );
		Assertions.assertEquals( "Failed to parse '{]' ([123, 93])", e.getMessage() );
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
			protected void validateValueType( String field, Object value ) {
				// allow everything
			}
		};
		query.set( "1", new Object() );

		assertThrows( IllegalStateException.class, () -> query.content() );
	}
}
