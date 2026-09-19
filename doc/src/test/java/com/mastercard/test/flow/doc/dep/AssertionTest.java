package com.mastercard.test.flow.doc.dep;

import static com.mastercard.test.flow.doc.dep.Actors.BEN;
import static com.mastercard.test.flow.doc.dep.Unpredictables.RNG;
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
 * Assertion component for a stateful system
 */
@SuppressWarnings("static-method")
@TestInstance(Lifecycle.PER_CLASS)
class AssertionTest {

	private static final BenSys system = new BenSys();
	private Flocessor flocessor;

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> tests() {
		flocessor = new Flocessor( "Ben behaviour", new Storage() )
				.system( State.FUL, BEN )
				.masking( RNG )
				.behaviour( asrt -> {
					String input = new String( asrt.expected().request().content(), UTF_8 );
					String output = system.getStorageResponse( input );
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
