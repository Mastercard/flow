package com.mastercard.test.flow.msg.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Exercises generic functionality of the {@link HttpMsg} superclass
 */
@SuppressWarnings("static-method")
class HttpMsgTest {

	/**
	 * Illustrates the lower-casing behaviour of {@link HttpMsg#header(String)}
	 */
	@Test
	void header() {
		assertEquals( "^mixed-case^", HttpMsg.header( "MiXeD-cAsE" ),
				"name is lower-cased and sigils are added" );

		assertEquals( "mixed-case", HttpMsg.unheader( HttpMsg.header( "MiXeD-cAsE" ) ),
				"unheader cannot restore the input casing" );
	}
}
