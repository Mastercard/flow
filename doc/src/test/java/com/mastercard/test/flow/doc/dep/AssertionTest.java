package com.mastercard.test.flow.doc.dep;

import static com.mastercard.test.flow.doc.dep.Actors.BEN;
import static com.mastercard.test.flow.doc.dep.Unpredictables.RNG;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.Flocessor;

/**
 * Assertion component for a stateful system
 */
@SuppressWarnings("static-method")
class AssertionTest {

	private static final BenSys system = new BenSys();

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> tests() {
		return new Flocessor( "Ben behaviour", new Storage() )
				.system( State.FUL, BEN )
				.masking( RNG )
				.behaviour( asrt -> {
					String input = new String( asrt.expected().request().content(), UTF_8 );
					String output = system.getStorageResponse( input );
					asrt.actual()
							.request( input.getBytes( UTF_8 ) )
							.response( output.getBytes( UTF_8 ) );
				} )
				.tests();
	}
}
