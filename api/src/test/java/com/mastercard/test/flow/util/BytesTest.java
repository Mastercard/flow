package com.mastercard.test.flow.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Bytes}
 */
@SuppressWarnings("static-method")
class BytesTest {

	/**
	 * Bumps pitest coverage
	 *
	 * @throws Exception reflection failure
	 */
	@Test
	void constructor() throws Exception {
		// pitest complains that we don't exercise the private constructor, so...
		Constructor<Bytes> c = Bytes.class.getDeclaredConstructor();
		assertTrue( Modifier.isPrivate( c.getModifiers() ) );
		c.setAccessible( true );
		c.newInstance();
		c.setAccessible( false );
	}

	/**
	 * Exercises {@link Bytes#toHex(byte[])}
	 */
	@Test
	void toHex() {
		BiConsumer<byte[], String> test = ( in, out ) -> Assertions.assertEquals(
				out, Bytes.toHex( in ), "for " + Arrays.toString( in ) );

		test.accept( null, null );
		test.accept( new byte[] {}, "" );
		test.accept( new byte[] { -2 }, "FE" );
		test.accept( new byte[] { -1 }, "FF" );
		test.accept( new byte[] { 1 }, "01" );
		test.accept( new byte[] { 2 }, "02" );
		test.accept( new byte[] { 14 }, "0E" );
		test.accept( new byte[] { 15 }, "0F" );
		test.accept( new byte[] { 16 }, "10" );
		test.accept( new byte[] { 17 }, "11" );
		test.accept( new byte[] { 127 }, "7F" );
		test.accept( new byte[] { -128 }, "80" );

		test.accept( new byte[] { 1, 2, 4, 8, 16, 32, 64, -128, 0 }, "010204081020408000" );
	}
}
