package com.mastercard.test.flow.msg.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.json.Json;
import com.mastercard.test.flow.msg.txt.Text;

/**
 * Exercising the {@link HttpRes} message type
 */
@SuppressWarnings("static-method")
class HttpResTest {

	/**
	 * Empty request
	 */
	@Test
	void empty() {
		assertEquals( ""
				+ " \r\n"
				+ "\r\n",
				new HttpRes().assertable() );
	}

	/**
	 * field set/get
	 */
	@Test
	void populated() {
		HttpRes msg = new HttpRes()
				.set( HttpMsg.VERSION, "version" )
				.set( HttpRes.STATUS, "status" )
				.set( HttpRes.STATUS_TEXT, "text" )
				.set( HttpMsg.header( "key" ), "value" )
				.set( HttpMsg.BODY, new Json() )
				.set( "body", "content" );

		assertEquals( "version", msg.get( HttpMsg.VERSION ) );
		assertEquals( "status", msg.get( HttpRes.STATUS ) );
		assertEquals( "text", msg.get( HttpRes.STATUS_TEXT ) );
		assertEquals( ""
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				((Message) msg.get( HttpMsg.BODY )).assertable() );
		assertEquals( "value", msg.get( HttpMsg.header( "key" ) ) );
		assertEquals( "content", msg.get( "body" ) );

		String expected = ""
				+ "version status text\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}";

		assertEquals( expected, msg.assertable() );
		assertEquals( expected, msg
				.peer( expected.getBytes( UTF_8 ) )
				.assertable() );

		assertEquals( "[HTTP_BODY, HTTP_STATUS, HTTP_STATUS_TEXT, HTTP_VERSION, ^key^, body]",
				msg.fields().toString() );

		assertEquals( ""
				+ "version status text\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\"body\":\"content\"}", new String( msg.content(), UTF_8 ) );
	}

	/**
	 * Byte parsing
	 */
	@Test
	void parse() {
		byte[] bytes = (""
				+ "HTTP/1.1 418 I'm a teapot\r\n"
				+ "bad header\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}").getBytes( UTF_8 );

		assertEquals( ""
				+ "HTTP/1.1 418 I'm a teapot\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				new HttpRes( bytes, Json::new ).assertable() );
	}

	/**
	 * Shows that we can cope with responses that lack the status text
	 */
	@Test
	void missingStatusText() {
		byte[] bytes = (""
				+ "HTTP/1.1 418\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}").getBytes( UTF_8 );

		assertEquals( ""
				+ "HTTP/1.1 418\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				new HttpRes( bytes, Json::new ).assertable() );
	}

	/**
	 * What happens when the body is not parseable as the expected type
	 */
	@Test
	void parseFailure() {
		String resdata = ""
				+ "HTTP/1.1 418 I'm a teapot\r\n"
				+ "bad header\r\n"
				+ "key: value\r\n"
				+ "\r\n";

		HttpRes req = new HttpRes( resdata.getBytes(), b -> new Json() {
			@Override
			public Json peer( byte[] content ) {
				throw new IllegalArgumentException();
			}
		} );

		byte[] badBytes = (resdata + "bad data").getBytes( UTF_8 );
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
		HttpRes msg = new HttpRes()
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpRes.STATUS, "200" )
				.set( HttpRes.STATUS_TEXT, "OK" )
				.set( HttpMsg.header( "key" ), "value" )
				.set( HttpMsg.BODY, new Json() )
				.set( "body", "content" );

		HttpRes child = msg.child()
				.set( HttpRes.STATUS_TEXT, "super-great!" )
				.set( "body", "child content" );

		msg.set( HttpMsg.VERSION, "HTTP/2.0" );

		assertEquals( ""
				+ "HTTP/2.0 200 OK\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				msg.assertable() );

		assertEquals( ""
				+ "HTTP/2.0 200 super-great!\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"child content\"\n"
				+ "}",
				child.assertable() );
	}

	/**
	 * Extracting masked content does not alter the base message
	 */
	@Test
	void masking() {
		Unpredictable u = () -> "unpredictable";

		HttpRes msg = new HttpRes()
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpRes.STATUS, "200" )
				.set( HttpRes.STATUS_TEXT, "OK" )
				.set( HttpMsg.header( "key" ), "value" )
				.set( HttpMsg.BODY, new Json() )
				.set( "body", "content" )
				.masking( u, m -> m.delete( HttpMsg.header( "key" ) ) );

		String full = "HTTP/1.1 200 OK\r\n"
				+ "key: value\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}";
		String masked = "HTTP/1.1 200 OK\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}";

		assertEquals( full, msg.assertable() );
		assertEquals( masked, msg.assertable( u ) );
		assertEquals( full, msg.assertable() );
	}

	/**
	 * Demonstrates that masks on the body message are triggered correctly
	 */
	@Test
	void bodyMasking() {
		Unpredictable u = () -> "rng";
		HttpRes msg = new HttpRes()
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpRes.STATUS, "200" )
				.set( HttpRes.STATUS_TEXT, "OK" )
				.set( HttpMsg.header( "key" ), "value" )
				.set( HttpMsg.BODY,
						new Text( "0f7f760c-2294-49c2-817f-d2bcdea5c252" )
								.masking( u, m -> m.replace( ".+", "_UUID_" )
								// note that the mask in on the body message, not the http wrapper
								) );

		assertEquals( "HTTP/1.1 200 OK\r\n" +
				"key: value\r\n" +
				"\r\n" +
				"_UUID_", msg.assertable( u ) );
	}

	/**
	 * Honouring chunked Transfer-Encoding
	 */
	@Test
	void chunked() {
		HttpRes msg = new HttpRes()
				.set( HttpMsg.VERSION, "HTTP/1.1" )
				.set( HttpRes.STATUS, "200" )
				.set( HttpRes.STATUS_TEXT, "OK" )
				.set( HttpMsg.header( "Transfer-Encoding" ), "chunked" )
				.set( HttpMsg.BODY, new Json() )
				.set( "body", "content" );

		assertEquals( "HTTP/1.1 200 OK\r\n"
				+ "Transfer-Encoding: chunked\r\n"
				+ "\r\n"
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				msg.assertable() );

		assertEquals( "HTTP/1.1 200 OK\r\n"
				+ "Transfer-Encoding: chunked\r\n"
				+ "\r\n"
				+ "12\r\n"
				+ "{\"body\":\"content\"}\r\n"
				+ "0\r\n"
				+ "\r\n",
				new String( msg.content(), UTF_8 ) );

		HttpRes parsed = new HttpRes( msg.content(), Json::new );
		assertEquals( ""
				+ "{\n"
				+ "  \"body\" : \"content\"\n"
				+ "}",
				parsed.body().get().assertable() );
	}
}
