package com.mastercard.test.flow.msg.xml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Exercises the {@link XML} message type
 */
@SuppressWarnings("static-method")
class XMLTest {

	private enum Nprdtbl implements Unpredictable {
		RNG
	}

	private static void test( XML xml, String... expectedLines ) {
		testHumanFormat( xml, expectedLines );
		testWireFormat( xml, expectedLines );
	}

	private static void testHumanFormat( XML xml, String... expectedLines ) {
		String actual = xml.asHuman();
		assertEquals( copypasta( expectedLines ), copypasta( actual ),
				"Human-format serialisation" );

		XML parsed = new XML( actual.getBytes( UTF_8 ) );
		String roundTrip = parsed.asHuman();
		assertEquals( actual, roundTrip,
				"Round-trip data" );
	}

	private static void testWireFormat( XML xml, String... expectedLines ) {
		String actual = new String( xml.content(), UTF_8 )
				.replaceAll( "\"", "'" );

		assertEquals(
				Stream.of( expectedLines )
						.map( String::trim )
						.collect( joining() ),
				actual );

		XML parsed = new XML( actual.getBytes( UTF_8 ) );
		String roundTrip = new String( parsed.content(), UTF_8 )
				.replaceAll( "\"", "'" );
		assertEquals( actual, roundTrip );
	}

	/**
	 * Exercises an empty document
	 */
	@Test
	void empty() {
		{
			XML xml = new XML();
			test( xml, "" );
			xml.set( "field", "value" );
			test( xml, "<field>value</field>" );
		}
		{
			XML xml = new XML( new byte[0] );
			test( xml, "" );
			xml.set( "field", "value" );
			test( xml, "<field>value</field>" );
		}
	}

	/**
	 * Setting the root element
	 */
	@Test
	void root() {
		XML xml = new XML()
				.set( "/root", "foo" );
		test( xml,
				"<root>foo</root>" );

		xml.set( "/alt", "bar" );
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> xml.asHuman() );
		assertEquals( "Multiple root elements found [alt, root]", ise.getMessage() );
	}

	/**
	 * Controlling the XML header content
	 */
	@Test
	void header() {
		XML xml = new XML()
				.set( XML.HEADER_VERSION, "1.1" )
				.set( XML.HEADER_ENCODING, "ISO-8859-1" )
				.set( "foo", "bar" );

		test( xml,
				"<?xml version='1.1' encoding='ISO-8859-1'?>",
				"<foo>bar</foo>" );

		// the version is mandatory in the declaration, so the whole thing is skipped if
		// we don't have one
		xml.set( XML.HEADER_VERSION, null );

		test( xml,
				"<foo>bar</foo>" );
	}

	/**
	 * Setting child elements
	 */
	@Test
	void children() {
		XML xml = new XML()
				.set( "/root/abc", "foo" )
				.set( "/root/def", Arrays.asList( 1, 2, 3 ) )
				.set( "/root/ghi", Stream.of( "a", "b", "c" )
						.collect( toMap( k -> k, String::hashCode, ( a, b ) -> a, TreeMap::new ) ) )
				.set( "/root/def[4]", 4 )
				.set( "/root/ghi/b/@attr", "d" );

		test( xml,
				"<root>",
				"  <abc>foo</abc>",
				"  <def>1</def>",
				"  <def>2</def>",
				"  <def>3</def>",
				"  <def></def>",
				"  <def>4</def>",
				"  <ghi>",
				"    <a>97</a>",
				"    <b attr='d'>98</b>",
				"    <c>99</c>",
				"  </ghi>",
				"</root>" );
	}

	/**
	 * Exercise field retrieval
	 */
	@Test
	void get() {
		XML xml = new XML()
				.set( "/root/abc", "foo" )
				.set( "/root/def", Arrays.asList( 1, 2, 3 ) )
				.set( "/root/ghi", Stream.of( "a", "b", "c" )
						.collect( toMap( k -> k, String::hashCode, ( a, b ) -> a, TreeMap::new ) ) )
				.set( "/root/def[4]", 4 )
				.set( "/root/ghi/b/@attr", "d" );

		assertEquals( "foo", xml.get( "/root/abc" ) );
		assertEquals( "d", xml.get( "/root/ghi/b/@attr" ) );
		assertEquals( "3", xml.get( "/root/def[2]" ) );
	}

	/**
	 * Exercise field deletion
	 */
	@Test
	void delete() {
		XML xml = new XML()
				.set( "/root/element", "elv" )
				.set( "/root/element/@foo", "bar" )
				.set( "/root/list[0]", "a" )
				.set( "/root/list[1]", "b" )
				.set( "/root/list[2]", "c" )
				.set( "/root/with/@attr", "atv" );

		test( xml,
				"<root>",
				"  <element foo='bar'>elv</element>",
				"  <list>a</list>",
				"  <list>b</list>",
				"  <list>c</list>",
				"  <with attr='atv'></with>",
				"</root>" );

		// null or the DELETE value can be used
		xml.set( "/root/element", null )
				.set( "/root/list[1]", null )
				.set( "/root/with/@attr", AbstractMessage.DELETE );

		test( xml,
				"<root>",
				"  <list>a</list>",
				"  <list>c</list>",
				"  <with></with>",
				"</root>" );

	}

	/**
	 * Exercise field listing
	 */
	@Test
	void fields() {
		XML xml = new XML()
				.set( "/root/abc", "foo" )
				.set( "/root/def", Arrays.asList( 1, 2, 3 ) )
				.set( "/root/ghi", Stream.of( "a", "b", "c" )
						.collect( toMap( k -> k, String::hashCode, ( a, b ) -> a, TreeMap::new ) ) )
				.set( "/root/def[4]", 4 )
				.set( "/root/ghi/b/@attr", "d" );

		assertEquals( copypasta(
				"/root/abc",
				"/root/def[0]",
				"/root/def[1]",
				"/root/def[2]",
				"/root/def[4]",
				"/root/ghi/a",
				"/root/ghi/b",
				"/root/ghi/b/@attr",
				"/root/ghi/c" ),
				copypasta( xml.fields().stream() ) );
	}

	/**
	 * Demonstrating data inheritance
	 */
	@Test
	void child() {
		XML parent = new XML()
				.set( "/root/abc", "def" )
				.set( "/root/ghi", "jkl" )
				.masking( Nprdtbl.RNG, m -> m
						.delete( "/root/abc" ) );
		XML child = parent.child()
				.set( "/root/ghi", "mno" );

		// update to the child has not affected the parent
		test( parent,
				"<root>",
				"  <abc>def</abc>",
				"  <ghi>jkl</ghi>",
				"</root>" );
		// child has inherited data from the parent
		test( child,
				"<root>",
				"  <abc>def</abc>",
				"  <ghi>mno</ghi>",
				"</root>" );

		parent.set( "/root/abc", "pqr" );

		// parent is updated
		test( parent,
				"<root>",
				"  <abc>pqr</abc>",
				"  <ghi>jkl</ghi>",
				"</root>" );

		// update is inherited by the child
		test( child,
				"<root>",
				"  <abc>pqr</abc>",
				"  <ghi>mno</ghi>",
				"</root>" );

		// masking operations are inherited
		assertEquals( copypasta(
				"<root>",
				"  <ghi>jkl</ghi>",
				"</root>" ),
				copypasta( parent.assertable( Nprdtbl.RNG ) ) );
		assertEquals( copypasta(
				"<root>",
				"  <ghi>mno</ghi>",
				"</root>" ),
				copypasta( child.assertable( Nprdtbl.RNG ) ) );
	}

	/**
	 * Demonstrates the independence of the child of a peer
	 */
	@Test
	void nibling() {
		XML parent = new XML().set( "root/key", "abc" );

		XML peer = parent.peer( "<root><key>def</key></root>".getBytes( UTF_8 ) );

		XML nibl = peer.child().set( "root/key", "ghi" );

		test( peer,
				"<root>",
				"  <key>def</key>",
				"</root>" );

		test( nibl,
				"<root>",
				"  <key>ghi</key>",
				"</root>" );
	}

	/**
	 * Demonstrating mask sharing
	 */
	@Test
	void peer() {
		XML xml = new XML()
				.set( "/root/abc", "def" )
				.set( "/root/ghi", "jkl" )
				.masking( Nprdtbl.RNG, m -> m
						.delete( "/root/abc" ) );

		XML peer = xml.peer( "<root><abc>def</abc><ghi>mno</ghi></root>".getBytes( UTF_8 ) );

		assertEquals( copypasta(
				"<root>",
				"  <ghi>mno</ghi>",
				"</root>" ),
				copypasta( peer.assertable( Nprdtbl.RNG ) ) );
	}

	/**
	 * Explores encoding beahviour
	 */
	@Test
	void encoding() {
		XML xml = new XML()
				.set( XML.HEADER_ENCODING, "no such encoding" )
				.set( "a", "Ø" );

		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> xml.content() );
		assertEquals( "Failed to serialise in 'no such encoding'", iae.getMessage() );

		xml.set( XML.HEADER_ENCODING, "ISO-8859-1" );
		assertEquals( "[60, 97, 62, " // <a>
				+ "-40, "
				+ "60, 47, 97, 62]", // </a>
				Arrays.toString( xml.content() ) );

		xml.set( XML.HEADER_ENCODING, "UTF-8" );
		assertEquals( "[60, 97, 62, " // <a>
				+ "-61, -104, "
				+ "60, 47, 97, 62]", // </a>
				Arrays.toString( xml.content() ) );
	}

	/**
	 * Shows what happens when bad data is supplied
	 */
	@Test
	void badData() {
		{
			byte[] bytes = "this isn't xml!".getBytes( UTF_8 );
			IllegalStateException ise = assertThrows( IllegalStateException.class,
					() -> new XML( bytes ).content() );
			assertEquals( ""
					+ "Failed to parse\n"
					+ "this isn't xml!\n"
					+ "[116, 104, 105, 115, 32, 105, 115, 110, 39, 116, 32, 120, 109, 108, 33]",
					ise.getMessage() );
		}
		{
			byte[] bytes = "<?xml version='1.1'?>".getBytes( UTF_8 );
			IllegalStateException ise = assertThrows( IllegalStateException.class,
					() -> new XML( bytes ).content() );
			assertEquals( ""
					+ "Failed to parse\n"
					+ "<?xml version='1.1'?>\n"
					+ "[60, 63, 120, 109, 108, 32, 118, 101, 114, 115, 105, 111, 110, 61, 39, 49, 46, 49, 39, 63, 62]",
					ise.getMessage() );
		}
	}

	/**
	 * Shows that the parser ignores external entity declarations
	 */
	@Test
	void xxeAvoidance() {
		// parsing this without setting IS_SUPPORTING_EXTERNAL_ENTITIES to false will
		// populate the document with a listing of your system's root directory
		String attack = ""
				+ "<?xml version=\"1.0\"?>\n"
				+ "<!DOCTYPE root_file_list\n"
				+ "[\n"
				+ "<!ENTITY list SYSTEM \"/\">\n"
				+ "]\n"
				+ ">\n"
				+ "<root_file_list>&list;</root_file_list>";

		XML xml = new XML( attack.getBytes( UTF_8 ) );
		test( xml,
				"<?xml version='1.0' encoding='UTF-8'?>",
				"<root_file_list></root_file_list>" );
	}

	private static String copypasta( String... content ) {
		return copypasta( Stream.of( content ) );
	}

	private static String copypasta( Stream<String> content ) {
		return content
				.map( s -> s.replaceAll( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replaceAll( "\"", "'" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}
}
