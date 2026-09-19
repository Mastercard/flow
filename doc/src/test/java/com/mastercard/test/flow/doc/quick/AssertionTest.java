package com.mastercard.test.flow.doc.quick;

import static com.mastercard.test.flow.doc.quick.Actors.BEN;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.Flocessor;

/**
 * Trivial example of assertion component usage.
 */
@SuppressWarnings("static-method")
class AssertionTest {

	/**
	 * @return Test instances
	 */
	// snippet-start:assertion
	@TestFactory
	Stream<DynamicNode> tests() {
		return new Flocessor( "Ben behaviour", new Greetings() )
				.system( State.LESS, BEN )
				.behaviour( asrt -> {
					String input = new String( asrt.expected().request().content(), UTF_8 );
					String output = BenSys.getGreetingResponse( input );
					asrt.actual()
							.request( input.getBytes( UTF_8 ) )
							.response( output.getBytes( UTF_8 ) );
				} )
				.tests();
	}
	// snippet-end:assertion
}
