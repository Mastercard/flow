package com.mastercard.test.flow.msg.bytes;

import static com.mastercard.test.flow.util.Bytes.toHex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * Exercises {@link Bytes}
 */
@SuppressWarnings("static-method")
class BytesTest {

	private static byte[] range( int s, int e ) {
		byte[] range = new byte[e - s];
		for( int i = 0; i < range.length; i++ ) {
			range[i] = (byte) (i + s);
		}
		return range;
	}

	/**
	 * Illustrating construction
	 */
	@Test
	void construction() {
		assertEquals( "", toHex( new Bytes().content() ) );
		assertEquals( "00010203", toHex( new Bytes( range( 0, 4 ) ).content() ) );
	}

	/**
	 * This message type does not support field listing
	 */
	@Test
	void fields() {
		assertEquals( "[]", new Bytes( range( 0, 4 ) ).fields().toString() );
	}

	/**
	 * Shows that a defensive copy is taken by the constructor
	 */
	@Test
	void defence() {
		byte[] bytes = range( 0, 10 );
		Message msg = new Bytes( bytes );
		for( int i = 0; i < bytes.length; i++ ) {
			bytes[i] = 0;
		}

		assertEquals( "00010203040506070809", toHex( msg.content() ) );
	}

	/**
	 * Demonstrates the assertable message view
	 */
	@Test
	void assertable() {
		assertEquals( ""
				+ "0b00001000  0d008  0x08  BACKSPACE\n"
				+ "0b00001001  0d009  0x09  CHARACTER TABULATION\n"
				+ "0b00001010  0d010  0x0A  LINE FEED (LF)\n"
				+ "0b00001011  0d011  0x0B  LINE TABULATION\n"
				+ "0b00001100  0d012  0x0C  FORM FEED (FF)\n"
				+ "0b00001101  0d013  0x0D  CARRIAGE RETURN (CR)\n"
				+ "0b00001110  0d014  0x0E  SHIFT OUT\n"
				+ "0b00001111  0d015  0x0F  SHIFT IN\n"
				+ "0b00010000  0d016  0x10  DATA LINK ESCAPE\n"
				+ "0b00010001  0d017  0x11  DEVICE CONTROL ONE\n"
				+ "",
				new Bytes( range( 8, 18 ) ).assertable() );

		assertEquals( ""
				+ "0b01111001  0d121  0x79  LATIN SMALL LETTER Y\n"
				+ "0b01111010  0d122  0x7A  LATIN SMALL LETTER Z\n"
				+ "0b01111011  0d123  0x7B  LEFT CURLY BRACKET\n"
				+ "0b01111100  0d124  0x7C  VERTICAL LINE\n"
				+ "0b01111101  0d125  0x7D  RIGHT CURLY BRACKET\n"
				+ "0b01111110  0d126  0x7E  TILDE\n"
				+ "0b01111111  0d127  0x7F  DELETE\n"
				+ "0b10000000  0d128  0x80  \n"
				+ "0b10000001  0d129  0x81  \n"
				+ "0b10000010  0d130  0x82  \n"
				+ "",
				new Bytes( range( 121, 131 ) ).assertable() );

		assertEquals( ""
				+ "0b11111110  0d254  0xFE  \n"
				+ "0b11111111  0d255  0xFF  \n"
				+ "0b00000000  0d000  0x00  NULL\n"
				+ "0b00000001  0d001  0x01  START OF HEADING\n"
				+ "",
				new Bytes( range( 254, 258 ) ).assertable() );
	}

	/**
	 * Invalid range specifications are rejected
	 */
	@Test
	void badRange() {
		Bytes msg = new Bytes();

		Consumer<String> test = in -> {
			IllegalArgumentException iae = assertThrows(
					IllegalArgumentException.class,
					() -> msg.set( in, null ),
					"for " + in );
			assertEquals(
					"'" + in + "' is not a valid range",
					iae.getMessage(),
					"for " + in );
		};

		test.accept( null );
		test.accept( "" );
		test.accept( "foo" );
		test.accept( "2..1" );
	}

	/**
	 * Exercising content updates
	 */
	@Test
	void set() {
		// baseline
		assertEquals( "00010203040506070809", toHex( new Bytes( range( 0, 10 ) )
				.content() ) );

		// set a single byte
		assertEquals( "FF010203040506070809", toHex( new Bytes( range( 0, 10 ) )
				.set( "0", (byte) 255 )
				.content() ) );

		// set multiple bytes
		assertEquals( "0001FFFF040506070809", toHex( new Bytes( range( 0, 10 ) )
				.set( "2..4", new byte[] { (byte) 255, (byte) 255 } )
				.content() ) );

		// set all to the end
		assertEquals( "0001020304050607FFFF", toHex( new Bytes( range( 0, 10 ) )
				.set( "8..", new byte[] { (byte) 255, (byte) 255 } )
				.content() ) );

		// set all from the start
		assertEquals( "FFFF0203040506070809", toHex( new Bytes( range( 0, 10 ) )
				.set( "..2", new byte[] { (byte) 255, (byte) 255 } )
				.content() ) );

		// set all
		assertEquals( "FFFF", toHex( new Bytes( range( 0, 10 ) )
				.set( "..", new byte[] { (byte) 255, (byte) 255 } )
				.content() ) );

		// insert values
		assertEquals( "0001020304FFFF0506070809", toHex( new Bytes( range( 0, 10 ) )
				.set( "5..5", new byte[] { (byte) 255, (byte) 255 } )
				.content() ) );

		// remove values
		assertEquals( "00010206070809", toHex( new Bytes( range( 0, 10 ) )
				.set( "3..6", new byte[] {} )
				.content() ) );
		assertEquals( "00010206070809", toHex( new Bytes( range( 0, 10 ) )
				.set( "3..6", AbstractMessage.DELETE )
				.content() ) );
	}

	/**
	 * Exercising content extraction
	 */
	@Test
	void get() {
		Bytes msg = new Bytes( range( 0, 10 ) );
		BiConsumer<String, String> test = ( in, out ) -> assertEquals(
				out, toHex( (byte[]) msg.get( in ) ), "for " + in );

		test.accept( "4..4", "" );
		test.accept( "4", "04" );
		test.accept( "4..5", "04" );
		test.accept( "4..8", "04050607" );
		test.accept( "..4", "00010203" );
		test.accept( "6..", "06070809" );
		test.accept( "..", "00010203040506070809" );
		test.accept( "8..12", "0809" );
	}

	/**
	 * Shows that masks are passed on to peers and children
	 */
	@Test
	void mask() {
		Unpredictable rng = () -> "RNG";
		Message msg = new Bytes( range( 65, 72 ) )
				.masking( rng, m -> m
						.delete( "0" )
						.replace( "2..5", range( 88, 91 ) ) );
		{
			Message child = msg.child();

			assertEquals( ""
					+ "0b01000001  0d065  0x41  LATIN CAPITAL LETTER A\n"
					+ "0b01000010  0d066  0x42  LATIN CAPITAL LETTER B\n"
					+ "0b01000011  0d067  0x43  LATIN CAPITAL LETTER C\n"
					+ "0b01000100  0d068  0x44  LATIN CAPITAL LETTER D\n"
					+ "0b01000101  0d069  0x45  LATIN CAPITAL LETTER E\n"
					+ "0b01000110  0d070  0x46  LATIN CAPITAL LETTER F\n"
					+ "0b01000111  0d071  0x47  LATIN CAPITAL LETTER G\n"
					+ "", child.assertable() );
			assertEquals( ""
					+ "0b01000010  0d066  0x42  LATIN CAPITAL LETTER B\n"
					+ "0b01000011  0d067  0x43  LATIN CAPITAL LETTER C\n"
					+ "0b01011000  0d088  0x58  LATIN CAPITAL LETTER X\n"
					+ "0b01011001  0d089  0x59  LATIN CAPITAL LETTER Y\n"
					+ "0b01011010  0d090  0x5A  LATIN CAPITAL LETTER Z\n"
					+ "0b01000111  0d071  0x47  LATIN CAPITAL LETTER G\n"
					+ "", child.assertable( rng ) );
		}
		{
			Message peer = msg.peer( range( 97, 104 ) );
			assertEquals( ""
					+ "0b01100001  0d097  0x61  LATIN SMALL LETTER A\n"
					+ "0b01100010  0d098  0x62  LATIN SMALL LETTER B\n"
					+ "0b01100011  0d099  0x63  LATIN SMALL LETTER C\n"
					+ "0b01100100  0d100  0x64  LATIN SMALL LETTER D\n"
					+ "0b01100101  0d101  0x65  LATIN SMALL LETTER E\n"
					+ "0b01100110  0d102  0x66  LATIN SMALL LETTER F\n"
					+ "0b01100111  0d103  0x67  LATIN SMALL LETTER G\n"
					+ "", peer.assertable() );
			assertEquals( ""
					+ "0b01100010  0d098  0x62  LATIN SMALL LETTER B\n"
					+ "0b01100011  0d099  0x63  LATIN SMALL LETTER C\n"
					+ "0b01011000  0d088  0x58  LATIN CAPITAL LETTER X\n"
					+ "0b01011001  0d089  0x59  LATIN CAPITAL LETTER Y\n"
					+ "0b01011010  0d090  0x5A  LATIN CAPITAL LETTER Z\n"
					+ "0b01100111  0d103  0x67  LATIN SMALL LETTER G\n"
					+ "", peer.assertable( rng ) );
		}
	}
}
