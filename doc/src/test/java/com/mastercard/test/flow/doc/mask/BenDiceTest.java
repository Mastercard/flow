package com.mastercard.test.flow.doc.mask;

import static com.mastercard.test.flow.doc.mask.Actors.BEN;
import static com.mastercard.test.flow.doc.mask.Actors.DIE;
import static com.mastercard.test.flow.doc.mask.Unpredictables.RNG;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.Flocessor;

/**
 * Exercises the entire BEN/DICE system
 */
@TestInstance(Lifecycle.PER_CLASS)
class BenDiceTest {
	private Flocessor flocessor;

	// using the real dice implementation where the results are random
	private final BenSys system = new BenSys( new DieSys() );

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> tests() {
		flocessor = new Flocessor( "Ben/Dice behaviour", new Rolling() )
				.system( State.LESS, BEN, DIE )
				.masking( RNG )
				.behaviour( asrt -> {
					String input = new String( asrt.expected().request().content(), UTF_8 );
					String output = system.getDiceResponse( input );
					asrt.actual()
							.request( input.getBytes( UTF_8 ) )
							.response( output.getBytes( UTF_8 ) );
				} );
		return flocessor.tests();
	}

	/** Closes reporting after dynamic children have finished using the runner. */
	@AfterAll
	void completeFlows() {
		if( flocessor != null ) {
			flocessor.close();
		}
	}
}
