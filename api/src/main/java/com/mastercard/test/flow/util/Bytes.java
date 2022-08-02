package com.mastercard.test.flow.util;

import java.nio.charset.StandardCharsets;

/**
 * Utilities for working with bytes
 */
public class Bytes {

	private Bytes() {
		// no instances
	}

	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes( StandardCharsets.US_ASCII );

	/**
	 * Produces a string of hexadecimal characters from a byte sequence. From
	 * https://stackoverflow.com/a/9855338/494747
	 *
	 * @param bytes data
	 * @return hex-encoded data (uppercase)
	 */
	public static String toHex( byte[] bytes ) {
		if( bytes == null ) {
			return null;
		}

		byte[] hexChars = new byte[bytes.length * 2];
		for( int j = 0; j < bytes.length; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String( hexChars, StandardCharsets.UTF_8 );
	}
}
