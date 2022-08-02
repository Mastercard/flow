package com.mastercard.test.flow.msg.txt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Exercises the {@link Text} message type
 */
@SuppressWarnings("static-method")
class TextTest {

	/**
	 * byte input is assumed to be UTF8
	 */
	@Test
	void utf8() {
		Text text = new Text( new byte[] {
				-31, -102, -69, -31, -101, -106,
				32,
				-31, -102, -77, -31, -102, -71, -31, -102, -85, -31, -102, -90 } );
		assertEquals( ""
				+ "ᚻᛖ"
				+ " "
				+ "ᚳᚹᚫᚦ",
				text.assertable() );
	}

	/**
	 * Exercises {@link Text#set(String, Object)} behaviour
	 */
	@Test
	void set() {
		Text msg = new Text( "foobar" );

		Assertions.assertEquals( "foobar", msg.assertable() );

		Text t = msg.set( "foo", "gpp" );
		Assertions.assertEquals( "gppbar", msg.assertable() );
		Assertions.assertEquals( msg, t, "fluent api" );

		msg.set( "[aeiou]", " vowel " );
		Assertions.assertEquals( "gppb vowel r", msg.assertable() );

		msg.set( " (.)", "$1 " );
		Assertions.assertEquals( "gppbv owelr ", msg.assertable() );

		msg.set( "[^aeiou]", AbstractMessage.DELETE );
		Assertions.assertEquals( "oe", msg.assertable() );
	}

	/**
	 * It's not possible to list all the regular expressions that might be used to
	 * address content, so we don't even try
	 */
	@Test
	void fields() {
		Assertions.assertTrue( new Text( "foobar" ).fields().isEmpty() );
	}

	/**
	 * Exercises {@link Text#get(String)} behaviour
	 */
	@Test
	void get() {
		Text msg = new Text( "foobar" );
		Assertions.assertEquals( "a", msg.get( "b(.)r" ), "capture group returned" );
		Assertions.assertEquals( "foob", msg.get( "f.*b" ), "no group, whole match returned" );
		Assertions.assertEquals( null, msg.get( "\\d+" ), "no match" );
	}

	/**
	 * Exercises accessing the message bytes
	 */
	@Test
	void content() {
		Assertions.assertArrayEquals( new byte[] { 102, 111, 111 }, new Text( "foo" ).content() );
	}

	/**
	 * Exercises {@link Text#child()} behaviour
	 */
	@Test
	void child() {
		Text parent = new Text( "foobar" );
		Text child = parent.child();
		// same initial state
		Assertions.assertEquals( "foobar", parent.assertable() );
		Assertions.assertEquals( "foobar", child.assertable() );

		// updates to child do not reflect in parent
		child.set( "foo", "" );
		Assertions.assertEquals( "foobar", parent.assertable() );
		Assertions.assertEquals( "bar", child.assertable() );

		// updates to parent are inherited
		parent.set( "bar", "BAR" );
		Assertions.assertEquals( "fooBAR", parent.assertable() );
		Assertions.assertEquals( "BAR", child.assertable() );
	}

	/**
	 * Exercises masking behaviour
	 */
	@Test
	void masks() {
		Unpredictable u = () -> "rng";
		Text msg = new Text( "random:" + new Random().nextInt( 1000 ) ).masking( u,
				m -> m.delete( "[0-9]+" ) );

		Assertions.assertEquals( "random:", msg.assertable( u ),
				"unpredictable fields masked" );

		Text child = msg.child();
		Assertions.assertEquals( "random:", child.assertable( u ),
				"masks are inherited by children" );

		Text peer = msg.peer( "blah0987halb".getBytes( UTF_8 ) );
		Assertions.assertEquals( "blahhalb", peer.assertable( u ),
				"masks are gifted to peers" );
	}
}
