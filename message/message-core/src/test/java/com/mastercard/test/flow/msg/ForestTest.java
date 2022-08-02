package com.mastercard.test.flow.msg;

import static java.util.stream.Collectors.toCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Exercises the {@link Forest} methods
 */
@SuppressWarnings("static-method")
class ForestTest {

	/**
	 * Allows a much more readable view of key/value/array structures
	 */
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	private static BiConsumer<Map<String, Object>, String> mapSet( Object value ) {
		return ( map, key ) -> map.put( key, value );
	}

	private static BiConsumer<Map<String, Object>, String> mapGet( AtomicReference<Object> value ) {
		return ( map, key ) -> value.set( map.get( key ) );
	}

	private static ObjIntConsumer<List<Object>> listSet( Object value ) {
		return ( list, index ) -> list.set( index, value );
	}

	private static ObjIntConsumer<List<Object>> listGet( AtomicReference<Object> value ) {
		return ( list, index ) -> value.set( list.get( index ) );
	}

	/**
	 * Bumps pitest coverage
	 *
	 * @throws Exception reflection failure
	 */
	@Test
	void constructor() throws Exception {
		// pitest complains that we don't exercise the private constructor, so...
		Constructor<Forest> c = Forest.class.getDeclaredConstructor();
		assertTrue( Modifier.isPrivate( c.getModifiers() ) );
		c.setAccessible( true );
		c.newInstance();
		c.setAccessible( false );
	}

	/**
	 * Simplest case of setting a name/value pair
	 */
	@Test
	void map() {
		Deque<String> path = path( "name" );
		Map<String, Object> data = testSet( data( "{}" ), path, false, "value",
				"{",
				"  'name' : 'value'",
				"}" );
		testGet( data, path, "value" );
	}

	/**
	 * root does not exist, and we don't create it
	 */
	@Test
	void nonvivifyingDeepMap() {
		Deque<String> path = path( "root", "name" );
		Map<String, Object> data = testSet( data( "{}" ), path, false, "value",
				"{ }" );
		testGet( data, path, "Not set" );
	}

	/**
	 * path elements are created if they don't exist
	 */
	@Test
	void vivifyingDeepMap() {
		Deque<String> path = path( "root", "name" );
		Map<String, Object> data = testSet( data( "{}" ), path, true, "value",
				"{",
				"  'root' : {",
				"    'name' : 'value'",
				"  }",
				"}" );
		testGet( data, path, "value" );
	}

	/**
	 * Behaviour of overwriting map members
	 */
	@Test
	void mapOverwrite() {
		// overwrite
		Map<String, Object> data = data( "{}" );

		testSet( data, path( "root", "foo" ), true, "a",
				"{",
				"  'root' : {",
				"    'foo' : 'a'",
				"  }",
				"}" );
		testSet( data, path( "root", "foo" ), false, "b",
				"{",
				"  'root' : {",
				"    'foo' : 'b'",
				"  }",
				"}" );

		// without vivification, cannot convert a value to a map
		testSet( data, path( "root", "foo", "bar" ), false, "c",
				"{",
				"  'root' : {",
				"    'foo' : 'b'",
				"  }",
				"}" );

		// with vivification, can convert a value to a map
		testSet( data, path( "root", "foo", "bar" ), true, "c",
				"{",
				"  'root' : {",
				"    'foo' : {",
				"      'bar' : 'c'",
				"    }",
				"  }",
				"}" );

		// can convert a map to a value without vivification
		testSet( data, path( "root" ), false, "d",
				"{",
				"  'root' : 'd'",
				"}" );
	}

	/**
	 * Exercising setting list members
	 */
	@Test
	void list() {
		Map<String, Object> data = data( "{\"name\":[null]}" );
		Deque<String> path = path( "name[0]" );
		data = testSet( data, path, false, "value",
				"{",
				"  'name' : [ 'value' ]",
				"}" );
		testGet( data, path, "value" );

		path = path( "name[2]" );

		// non-vivifying, list is not grown
		data = testSet( data, path, false, "eulav",
				"{",
				"  'name' : [ 'value' ]",
				"}" );

		// vivifying, list is grown
		data = testSet( data, path, true, "eulav",
				"{",
				"  'name' : [ 'value', null, 'eulav' ]",
				"}" );
		testGet( data, path, "eulav" );

		// non-vivifying again, but this time the list is big enough
		path = path( "name[1]" );
		data = testSet( data, path, false, "foo",
				"{",
				"  'name' : [ 'value', 'foo', 'eulav' ]",
				"}" );
		testGet( data, path, "foo" );

		// non-vivifying can't overwrite type
		path = path( "name[2]", "foo" );
		data = testSet( data, path, false, "bar",
				"{",
				"  'name' : [ 'value', 'foo', 'eulav' ]",
				"}" );

		// nor extend the list by 1 element
		path = path( "name[3]", "foo" );
		data = testSet( data, path, false, "bar",
				"{",
				"  'name' : [ 'value', 'foo', 'eulav' ]",
				"}" );
	}

	/**
	 * Negative list indices are not supported
	 */
	@Test
	void negativeIndices() {
		Map<String, Object> data = data( "{}" );
		Deque<String> path = path( "name[-1]" );

		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> testSet( data, path, true, "value" ) );

		assertEquals( "Field path element 'name[-1]' has negative index", iae.getMessage() );
	}

	/**
	 * Exploring the vivification of deep list structures
	 */
	@Test
	void deepListVivification() {
		Map<String, Object> data = data( "{}" );
		Deque<String> path = path( "name[0][1][2]" );
		data = testSet( data, path, false, "value",
				"{ }" );

		path = path( "name[0][1][2]" );
		data = testSet( data, path, true, "value",
				"{",
				"  'name' : [ [ null, [ null, null, 'value' ] ] ]",
				"}" );
		testGet( data, path, "value" );

		path = path( "name[0][1][3]" );
		data = testSet( data, path, false, "eulav",
				"{",
				"  'name' : [ [ null, [ null, null, 'value' ] ] ]",
				"}" );
		testGet( data, path, "Not set" );

		path = path( "name[0][1][2]" );
		data = testSet( data, path, false, "eulav",
				"{",
				"  'name' : [ [ null, [ null, null, 'eulav' ] ] ]",
				"}" );
		testGet( data, path, "eulav" );
	}

	/**
	 * Paths with a mix of maps and lists
	 */
	@Test
	void mixed() {

		Deque<String> path = path( "root", "listlist[0][0]", "maplist[0]", "map", "name" );
		Map<String, Object> data = testSet( data( "{}" ), path, true, "value",
				"{",
				"  'root' : {",
				"    'listlist' : [ [ {",
				"      'maplist' : [ {",
				"        'map' : {",
				"          'name' : 'value'",
				"        }",
				"      } ]",
				"    } ] ]",
				"  }",
				"}" );
		testGet( data, path, "value" );
	}

	/**
	 * Demonstrating the harvesting of leaf values
	 */
	@Test
	void leaves() {

		Map<String, Object> data = data( "{}" );
		int v = 1;
		for( Deque<String> path : Arrays.asList(
				path( "root", "listlist[0][0]", "maplist[0]", "map", "a" ),
				path( "root", "listlist[0][0]", "maplist[1]", "map", "b" ),
				path( "root", "listlist[1][0]", "c" ),
				path( "root", "listlist[0][3]", "map", "d" ),
				path( "e" ) ) ) {
			int vs = v++;
			Forest.traverse( data, path, true, mapSet( vs ), listSet( vs ) );
		}

		StringBuilder sb = new StringBuilder();
		Forest.leaves( ".", data, ( path, value ) -> sb
				.append( path )
				.append( " " )
				.append( value )
				.append( "\n" ) );

		Assertions.assertEquals( copypasta(
				"root.listlist[0][0].maplist[0].map.a 1",
				"root.listlist[0][0].maplist[1].map.b 2",
				"root.listlist[0][3].map.d 4",
				"root.listlist[1][0].c 3",
				"e 5" ),
				copypasta( sb.toString() ) );
	}

	/**
	 * Nested lists
	 */
	@Test
	void nestedList() {
		Deque<String> path = path( "root[0][1]" );
		Map<String, Object> data = testSet( data( "{}" ), path, true, "value",
				"{",
				"  'root' : [ [ null, 'value' ] ]",
				"}" );
		testGet( data, path, "value" );
	}

	private static Map<String, Object> testSet( Map<String, Object> data, Deque<String> path,
			boolean vivify,
			Object value, String... expected ) {
		try {
			String before = JSON.writeValueAsString( data );

			Forest.traverse( data, path, vivify, mapSet( value ), listSet( value ) );

			String after = JSON.writeValueAsString( data );
			Assertions.assertEquals(
					copypasta( expected ),
					copypasta( after ),
					() -> String.format( "Setting %s to %s, %s, into %s",
							value, path, vivify ? "vivifying" : "not vivifying", before ) );
		}
		catch( JsonProcessingException e ) {
			throw new IllegalArgumentException( e );
		}
		return data;
	}

	private static void testGet( Map<String, Object> data, Deque<String> path, Object expected ) {
		AtomicReference<Object> retrieved = new AtomicReference<>( "Not set" );
		Forest.traverse( data, path, false, mapGet( retrieved ), listGet( retrieved ) );
		Assertions.assertEquals( expected, retrieved.get(),
				() -> String.format( "Fetching %s from %s",
						path, data ) );
	}

	private static Map<String, Object> data( String json ) {
		try {
			return JSON.readValue( json,
					new TypeReference<LinkedHashMap<String, Object>>() {
						// type hint only
					} );
		}
		catch( JsonProcessingException e ) {
			throw new IllegalArgumentException( "failed to parse " + json, e );
		}
	}

	private static Deque<String> path( String... elements ) {
		return Stream.of( elements )
				.collect( toCollection( ArrayDeque::new ) );
	}

	private static String copypasta( String... content ) {
		return Stream.of( content )
				.map( s -> s.replaceAll( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replaceAll( "\"", "'" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}
}
