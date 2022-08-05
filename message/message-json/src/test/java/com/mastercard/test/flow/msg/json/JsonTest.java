package com.mastercard.test.flow.msg.json;

import static com.mastercard.test.flow.msg.AbstractMessage.DELETE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Exercises the {@link Json} message type
 */
@SuppressWarnings("static-method")
class JsonTest {

	/**
	 * Non-json content is noisily rejected
	 */
	@Test
	void badInput() {
		byte[] bytes = "{]".getBytes( UTF_8 );
		Json json = new Json( bytes );
		IllegalArgumentException e = Assertions.assertThrows( IllegalArgumentException.class,
				() -> json.content() );
		Assertions.assertEquals( "Failed to parse '{]' ([123, 93])", e.getMessage() );
	}

	/**
	 * Empty messages
	 */
	@Test
	void empty() {
		assertEquals( "{ }", new Json().assertable() );
		assertEquals( "{ }", new Json( "{}".getBytes( UTF_8 ) ).assertable() );
		assertEquals( "[ ]", new Json( "[]".getBytes( UTF_8 ) ).assertable() );

		assertEquals( 0, new Json().fields().size() );
	}

	/**
	 * Bare value messages
	 */
	@Test
	void bare() {
		assertEquals( "0", new Json( "0".getBytes( UTF_8 ) ).assertable() );
		assertEquals( "false", new Json( "false".getBytes( UTF_8 ) ).assertable() );
		assertEquals( "\"string\"", new Json( "\"string\"".getBytes( UTF_8 ) ).assertable() );

		assertEquals( "1.23", new Json().set( "", new BigDecimal( "1.23" ) ).assertable() );
		assertEquals( "true", new Json().set( "", true ).assertable() );
		assertEquals( "\"string\"", new Json().set( "", "string" ).assertable() );

		Set<String> fields = new Json().set( "", true ).fields();
		assertEquals( 1, fields.size() );
		assertEquals( "", fields.iterator().next() );
	}

	/**
	 * Building and querying object trees
	 */
	@Test
	void object() {
		Json msg = new Json()
				.set( "key", "value" )
				.set( "sub.obj", 5 );

		assertEquals( "value", msg.get( "key" ) );
		assertEquals( "{obj=5}", msg.get( "sub" ).toString() );
		assertEquals( 5, msg.get( "sub.obj" ) );
		assertEquals( null, msg.get( "non.existent" ) );
		assertEquals( "[key, sub.obj]", msg.fields().toString() );

		assertEquals( ""
				+ "{\n"
				+ "  \"key\" : \"value\",\n"
				+ "  \"sub\" : {\n"
				+ "    \"obj\" : 5\n"
				+ "  }\n"
				+ "}",
				msg.assertable() );

		assertEquals( "[key, sub.obj]", msg.fields().toString() );
	}

	/**
	 * Building and querying array trees
	 */
	@Test
	void array() {
		Json msg = new Json( "[]".getBytes( UTF_8 ) )
				.set( "[0]", "value" )
				.set( "[1][2][3]", "deep" );

		assertEquals( "value", msg.get( "[0]" ) );
		assertEquals( "[null, null, [null, null, null, deep]]", msg.get( "[1]" ).toString() );
		assertEquals( "[null, null, null, deep]", msg.get( "[1][2]" ).toString() );
		assertEquals( "deep", msg.get( "[1][2][3]" ).toString() );
		assertEquals( null, msg.get( "[1][3]" ) );
		assertEquals( null, msg.get( "[2]" ) );

		assertEquals( "[ \"value\", [ null, null, [ null, null, null, \"deep\" ] ] ]",
				msg.assertable() );
		assertEquals( "[[0], [1][2][3]]", msg.fields().toString() );
	}

	/**
	 * Shows that fields can be deleted
	 */
	@Test
	void delete() {
		Json msg = new Json()
				.set( "obj.foo", "a" )
				.set( "obj.bar.baz", "b" )
				.set( "arr[1]", "c" )
				.set( "arr[2].sub", "d" )
				.set( "sub.arr[0][1]", "e" );

		assertEquals( ""
				+ "{\n"
				+ "  \"arr\" : [ null, \"c\", {\n"
				+ "    \"sub\" : \"d\"\n"
				+ "  } ],\n"
				+ "  \"obj\" : {\n"
				+ "    \"bar\" : {\n"
				+ "      \"baz\" : \"b\"\n"
				+ "    },\n"
				+ "    \"foo\" : \"a\"\n"
				+ "  },\n"
				+ "  \"sub\" : {\n"
				+ "    \"arr\" : [ [ null, \"e\" ] ]\n"
				+ "  }\n"
				+ "}",
				msg.assertable() );
		assertEquals(
				"[arr[1], arr[2].sub, obj.bar.baz, obj.foo, sub.arr[0][1]]",
				msg.fields().toString() );

		msg.set( "obj.bar", DELETE )
				.set( "arr[2]", AbstractMessage.DELETE )
				.set( "non.existent.thing", AbstractMessage.DELETE )
				.set( "arr[1][2].non.existent.thing", AbstractMessage.DELETE )
				.set( "arr[3]", AbstractMessage.DELETE )
				.set( "sub.arr[1][2]", AbstractMessage.DELETE );

		assertEquals( ""
				+ "{\n"
				+ "  \"arr\" : [ null, \"c\" ],\n"
				+ "  \"obj\" : {\n"
				+ "    \"foo\" : \"a\"\n"
				+ "  },\n"
				+ "  \"sub\" : {\n"
				+ "    \"arr\" : [ [ null, \"e\" ] ]\n"
				+ "  }\n"
				+ "}",
				msg.assertable() );
		assertEquals(
				"{\"arr\":[null,\"c\"],\"obj\":{\"foo\":\"a\"},\"sub\":{\"arr\":[[null,\"e\"]]}}",
				new String( msg.content(), UTF_8 ) );
		assertEquals( "[arr[1], obj.foo, sub.arr[0][1]]", msg.fields().toString() );
	}

	/**
	 * Shows that updates to the parent are reflected in the child, but not vice
	 * versa
	 */
	@Test
	void child() {
		Json parent = new Json()
				.set( "obj.foo", "a" )
				.set( "obj.bar.baz", "b" )
				.set( "arr[1]", "c" )
				.set( "arr[2].sub", "d" )
				.set( "map", Json.EMPTY_MAP )
				.set( "list", Json.EMPTY_LIST );

		Json child = parent.child()
				.set( "child", "This should not appear in the parent!" )
				.set( "obj", AbstractMessage.DELETE )
				.set( "map.key", "Neither should this!" )
				.set( "list[0]", "Especially not this!" );

		assertEquals( ""
				+ "{\n"
				+ "  \"arr\" : [ null, \"c\", {\n"
				+ "    \"sub\" : \"d\"\n"
				+ "  } ],\n"
				+ "  \"list\" : [ ],\n"
				+ "  \"map\" : { },\n"
				+ "  \"obj\" : {\n"
				+ "    \"bar\" : {\n"
				+ "      \"baz\" : \"b\"\n"
				+ "    },\n"
				+ "    \"foo\" : \"a\"\n"
				+ "  }\n"
				+ "}",
				parent.assertable() );

		assertEquals( ""
				+ "{\n"
				+ "  \"arr\" : [ null, \"c\", {\n"
				+ "    \"sub\" : \"d\"\n"
				+ "  } ],\n"
				+ "  \"child\" : \"This should not appear in the parent!\",\n"
				+ "  \"list\" : [ \"Especially not this!\" ],\n"
				+ "  \"map\" : {\n"
				+ "    \"key\" : \"Neither should this!\"\n"
				+ "  }\n"
				+ "}",
				child.assertable() );

		parent.set( "new", "field" )
				.set( "child", AbstractMessage.DELETE );

		assertEquals( ""
				+ "{\n"
				+ "  \"arr\" : [ null, \"c\", {\n"
				+ "    \"sub\" : \"d\"\n"
				+ "  } ],\n"
				+ "  \"list\" : [ ],\n"
				+ "  \"map\" : { },\n"
				+ "  \"new\" : \"field\",\n"
				+ "  \"obj\" : {\n"
				+ "    \"bar\" : {\n"
				+ "      \"baz\" : \"b\"\n"
				+ "    },\n"
				+ "    \"foo\" : \"a\"\n"
				+ "  }\n"
				+ "}",
				parent.assertable() );

		assertEquals( ""
				+ "{\n"
				+ "  \"arr\" : [ null, \"c\", {\n"
				+ "    \"sub\" : \"d\"\n"
				+ "  } ],\n"
				+ "  \"child\" : \"This should not appear in the parent!\",\n"
				+ "  \"list\" : [ \"Especially not this!\" ],\n"
				+ "  \"map\" : {\n"
				+ "    \"key\" : \"Neither should this!\"\n"
				+ "  },\n"
				+ "  \"new\" : \"field\"\n"
				+ "}",
				child.assertable() );
	}

	/**
	 * Demonstrates the independence of the child of a peer
	 */
	@Test
	void nibling() {
		Json parent = new Json().set( "key", "abc" );

		Json peer = parent.peer( "{\"key\":\"def\"}".getBytes( UTF_8 ) );

		Json nibl = peer.child().set( "key", "ghi" );

		assertEquals( ""
				+ "{\n"
				+ "  \"key\" : \"ghi\"\n"
				+ "}", nibl.asHuman() );

		assertEquals( ""
				+ "{\n"
				+ "  \"key\" : \"def\"\n"
				+ "}", peer.asHuman() );
	}

	/**
	 * Show that masking operation are applied, and are inherited by children and
	 * peers
	 */
	@Test
	void masking() {
		Unpredictable u = () -> "rng";
		Json msg = new Json().set( "foo", "bar" ).masking( u, m -> m.delete( "foo" ) );
		assertEquals( "{ }", msg.assertable( u ) );
		assertEquals( "{ }", msg.child().assertable( u ) );
		assertEquals( ""
				+ "{\n"
				+ "  \"bar\" : 2\n"
				+ "}",
				msg.peer(
						"{\"foo\":\"1\",\"bar\":2}".getBytes( UTF_8 ) )
						.assertable( u ) );
	}

	/**
	 * Possibly-mutable values are rejected
	 */
	@Test
	void mutableValue() {
		Message msg = new Json();
		Object value = new Object();
		assertThrows( IllegalArgumentException.class,
				() -> msg.set( "field", value ) );
	}

	/**
	 * It's inevitable that some frantic debugging session will hinge on printing
	 * out the values being populated into the json. Let's show that the two
	 * empty-structure tokens will make themselves obvious rather than producing a
	 * cryptic Object@123abc string
	 */
	@Test
	void emptyStructureTokens() {
		assertEquals( "Json.EMPTY_MAP", Json.EMPTY_MAP.toString() );
		assertEquals( "Json.EMPTY_LIST", Json.EMPTY_LIST.toString() );
	}

	/**
	 * Shows what happens when serialisation fails
	 */
	@Test
	void badContent() {
		Json json = new Json() {
			@Override
			protected void validateValueType( String field, Object value ) {
				// allow everything
			}
		};
		json.set( "field", new Object() );

		assertThrows( IllegalStateException.class, () -> json.asHuman() );
		assertThrows( IllegalStateException.class, () -> json.content() );
	}
}
