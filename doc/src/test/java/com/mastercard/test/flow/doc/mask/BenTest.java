package com.mastercard.test.flow.doc.mask;

import static com.mastercard.test.flow.doc.mask.Actors.BEN;
import static com.mastercard.test.flow.doc.mask.Actors.DIE;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Consequests;
import com.mastercard.test.flow.assrt.junit5.Flocessor;

/**
 * Exercises BEN in isolation
 */
class BenTest {

	// a mock dice implementation where the results are controlled by the test
	private final Consequests capturedDice = new Consequests();
	private String expectedRoll;
	private final BenSys system = new BenSys( def -> {
		capturedDice.capture( DIE, def.getBytes( UTF_8 ) );
		return expectedRoll;
	} );

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> tests() {
		return new Flocessor( "Ben behaviour", new Rolling() )
				.system( State.LESS, BEN )
				.behaviour( asrt -> {
					// set up our mock dice implementation using model data
					expectedRoll = asrt.expected().children()
							.findFirst()
							.map( i -> new String( i.response().content(), UTF_8 ) )
							.orElse( "no downstream expectations found!" );

					String input = new String( asrt.expected().request().content(), UTF_8 );
					String output = system.getDiceResponse( input );

					asrt.actual()
							.request( input.getBytes( UTF_8 ) )
							.response( output.getBytes( UTF_8 ) );
					asrt.assertConsequests( capturedDice );
				} )
				.tests();
	}
}
