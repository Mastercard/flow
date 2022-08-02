package com.mastercard.test.flow.example.app.model.ctx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.Context;

/**
 * Exercises the {@link QueueProcessing} {@link Context}
 */
@SuppressWarnings("static-method")
class QueueProcessingTest {
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	/**
	 * Demonstrates child-rearing
	 */
	@Test
	void child() {

		QueueProcessing qp = new QueueProcessing()
				.active( false )
				.cleared( true )
				.exhausted( true );

		QueueProcessing child = qp.child();

		assertEquals( qp.active(), child.active() );
		assertEquals( qp.cleared(), child.cleared() );
		assertEquals( qp.exhausted(), child.exhausted() );
	}

	/**
	 * Demonstrates the serialised structure
	 *
	 * @throws Exception json failure
	 */
	@Test
	void json() throws Exception {
		QueueProcessing qp = new QueueProcessing()
				.active( false )
				.cleared( true )
				.exhausted( true );

		String json = JSON.writeValueAsString( qp ).replaceAll( "\r", "" );

		assertEquals( "" +
				"{\n" +
				"  \"active\" : false,\n" +
				"  \"cleared\" : true,\n" +
				"  \"exhausted\" : true\n" +
				"}",
				json );

		QueueProcessing parsed = JSON.readValue( json, QueueProcessing.class );

		assertEquals( false, parsed.active() );
		assertEquals( true, parsed.cleared() );
		assertEquals( true, parsed.exhausted() );
	}
}
