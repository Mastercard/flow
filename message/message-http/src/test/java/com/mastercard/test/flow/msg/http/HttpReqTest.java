
package com.mastercard.test.flow.msg.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;
import com.mastercard.test.flow.msg.json.Json;

/**
 * Exercising the {@link HttpReq} message type
 */
@SuppressWarnings("static-method")
class HttpReqTest {

	/**
	 * Empty request
	 */
	@Test
	void empty() {
		assertEquals( ""
				+ "  \r\n"
				+ "\r\n",
				new HttpReq().assertable() );

		assertEquals( "", new HttpReq().bodyText() );

		HttpReq withBody = new HttpReq()
				.set( HttpMsg.BODY, new Json().set( "foo", "bar" ) );
		assertEquals( ""
				+ "  \r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"foo\" : \"bar\"\n"
				+ "}",
				withBody.assertable() );

		withBody.set( HttpMsg.BODY, AbstractMessage.DELETE );
		assertEquals( ""
				+ "  \r\n"
				+ "\r\n",
				withBody.assertable() );
	}

	/**
	 * field set/get
	 */
	@Test
	void populated() {
		HttpReq msg = new HttpReq()
				.set( HttpReq.METHOD, "method" )
				.set( HttpReq.PATH, "path" )
				.set( HttpMsg.VERSION, "version" )
				.set( HttpMsg.header( "key" ), "value" )
				.set( HttpMsg.BODY, new Json() )
				.set( "body", "content" );

		assertEquals( "method", msg.get( HttpReq.METHOD ) );
		assertEquals( "path", msg.get( HttpReq.PATH ) );
		assertEquals( "version", msg.get( HttpMsg.VERSION ) );
		assertEquals( ""
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				((Message) msg.get( HttpMsg.BODY )).assertable() );
		assertEquals( "value", msg.get( HttpMsg.header( "key" ) ) );
		assertEquals( "content", msg.get( "body" ) );

		String expected = ""
				+ "method path version\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}";

		assertEquals( expected, msg.assertable() );
		assertEquals( expected, msg
				.peer( expected.getBytes( UTF_8 ) )
				.assertable() );

		assertEquals( "[HTTP_BODY, HTTP_METHOD, HTTP_PATH, HTTP_VERSION, ^key^, body]",
				msg.fields().toString() );

		assertEquals( ""
				+ "method path version\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\"body\":\"content\"}", new String( msg.content(), UTF_8 ) );

		assertEquals( ""
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}", msg.bodyText() );
	}

	/**
	 * Path variables
	 */
	@Test
	void path() {
		HttpReq msg = new HttpReq()
				.set( HttpReq.METHOD, "GET" )
				.set( HttpReq.PATH, "/root/foo/%foo_id%/bar/%bar_id%/baz/^baz_id^" )
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpMsg.header( "foo_id" ), "not a path var" )
				.set( HttpMsg.header( "bar_id" ), "also not a path var" )
				.set( HttpMsg.header( "baz_id" ), "still not a path var" )
				.set( HttpReq.path( "foo_id" ), 1 )
				.set( HttpReq.path( "bar_id" ), 2 );

		assertEquals( 1, msg.get( HttpReq.path( "foo_id" ) ) );

		assertEquals( ""
				+ "GET /root/foo/1/bar/2/baz/^baz_id^ HTTP/1.1\r\n"
				+ "bar_id: also not a path var\r\n"
				+ "baz_id: still not a path var\r\n"
				+ "foo_id: not a path var\r\n"
				+ "\r\n"
				+ "",
				msg.assertable() );
	}

	/**
	 * Byte parsing
	 */
	@Test
	void parse() {
		byte[] bytes = (""
				+ "method path version\r\n"
				+ "bad header\r\n"
				+ "key : value\r\n"
				+ "value_with_colon    : foo:bar\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}").getBytes( UTF_8 );

		assertEquals( ""
				+ "method path version\r\n"
				+ "key: value\r\n"
				+ "value_with_colon: foo:bar\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				new HttpReq( bytes, Json::new ).assertable() );
	}

	/**
	 * What happens when the body is not parseable as the expected type
	 */
	@Test
	void parseFailure() {
		String reqdata = ""
				+ "method path version\r\n"
				+ "bad header\r\n"
				+ "key : value\r\n"
				+ "value_with_colon    : foo:bar\r\n"
				+ "\r\n";

		HttpReq req = new HttpReq( reqdata.getBytes(), b -> new Json() {

			@Override
			public Json peer( byte[] content ) {
				throw new IllegalArgumentException();
			}
		} );

		byte[] badBytes = (reqdata + "bad data").getBytes( UTF_8 );
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> req.peer( badBytes ) );
		assertEquals( ""
				+ "Failed to parse  from body content\n"
				+ "UTF8:[bad data]\n"
				+ " hex:[6261642064617461]",
				iae.getMessage() );
	}

	/**
	 * Data inheritance
	 */
	@Test
	void child() {
		HttpReq msg = new HttpReq()
				.set( HttpReq.METHOD, "GET" )
				.set( HttpReq.PATH, "/foo/bar" )
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpMsg.header( "key" ), "value" )
				.set( HttpMsg.BODY, new Json() )
				.set( "body", "content" );

		HttpReq child = msg.child()
				.set( HttpReq.METHOD, "PUT" )
				.set( "body", "child content" );

		msg.set( HttpMsg.VERSION, "HTTP/2.0" );

		assertEquals( ""
				+ "GET /foo/bar HTTP/2.0\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				msg.assertable() );

		assertEquals( ""
				+ "PUT /foo/bar HTTP/2.0\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"child content\"\n"
				+ "}",
				child.assertable() );
	}

	/**
	 * Honouring chunked Transfer-Encoding
	 */
	@Test
	void chunked() {
		HttpReq msg = new HttpReq()
				.set( HttpReq.METHOD, "GET" )
				.set( HttpReq.PATH, "/foo/bar" )
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpMsg.header( "Transfer-Encoding" ), "chunked" )
				.set( HttpMsg.BODY, new Json() )
				.set( "body", "content" );

		assertEquals( ""
				+ "GET /foo/bar HTTP/1.1\r\n"
				+ "Transfer-Encoding: chunked\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				msg.assertable() );

		assertEquals( ""
				+ "GET /foo/bar HTTP/1.1\r\n"
				+ "Transfer-Encoding: chunked\r\n"
				+ "\r\n"
				+ "12\r\n" // remember this is hex
				+ "{\"body\":\"content\"}\r\n"
				+ "0\r\n"
				+ "\r\n"
				+ "",
				new String( msg.content(), UTF_8 ) );

		HttpReq parsed = new HttpReq( msg.content(), Json::new );
		assertEquals( ""
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				parsed.body().get().assertable() );
	}

	/**
	 * Masking of fields in peered messages
	 */
	@Test
	void masking() {
		Unpredictable RNG = () -> "rng";

		HttpReq msg = new HttpReq()
				.set( HttpReq.METHOD, "GET" )
				.set( HttpReq.PATH,
						"/foo/" + HttpReq.path( "p" ) + "/bar?q=" + HttpReq.path( "q" ) + "&r="
								+ HttpReq.path( "r" ) )
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpMsg.header( "Transfer-Encoding" ), "chunked" )
				.set( HttpReq.path( "p" ), "path" )
				.set( HttpReq.path( "q" ), "valu" )
				.set( HttpReq.path( "r" ), "ulav" )
				.set( HttpMsg.BODY, new Json().masking( RNG, m -> m.replace( "body", "masked" ) ) )
				.set( "body", "content" )
				.masking( RNG, m -> m
						.replace( HttpReq.path( "p" ), "_mp_" )
						.replace( HttpReq.path( "q" ), "_mq_" )
						.replace( HttpReq.path( "r" ), "_mr_" ) );

		msg = msg.peer( msg.content() );

		assertEquals( ""
				+ "GET /foo/_mp_/bar?q=_mq_&r=_mr_ HTTP/1.1\r\n"
				+ "Transfer-Encoding: chunked\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"masked\"\n"
				+ "}", msg.assertable( RNG ) );
	}

	/**
	 * Asserting that we can parse messages with missing fields
	 *
	 * @return test instances
	 */
	@TestFactory
	Stream<DynamicNode> missing() {
		HttpReq full = new HttpReq()
				.set( HttpReq.METHOD, "GET" )
				.set( HttpReq.PATH, "/path/to?q=uery" )
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpMsg.header( "header" ), "value" )
				.set( HttpMsg.BODY, new Json() );

		Stream<TreeSet<String>> fieldSets = new Combinator<>( TreeSet::new,
				HttpReq.METHOD,
				HttpReq.PATH,
				HttpMsg.VERSION,
				HttpMsg.header( "header" ),
				HttpMsg.BODY )
						.stream();

		return fieldSets.map( fields -> dynamicTest(
				"missing " + fields.toString(), () -> {
					HttpReq partial = full.child();
					fields.forEach( f -> partial.set( f, AbstractMessage.DELETE ) );

					HttpReq parsed = partial.peer( partial.content() );
					assertEquals( partial.assertable(), parsed.assertable(),
							"serialisation/parsing round-trip divergence" );
				} ) );
	}
}
