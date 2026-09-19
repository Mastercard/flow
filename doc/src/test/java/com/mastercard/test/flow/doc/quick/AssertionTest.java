package com.mastercard.test.flow.doc.quick;

import static com.mastercard.test.flow.doc.quick.Actors.BEN;
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
 * Trivial example of assertion component usage.
 */
@SuppressWarnings("static-method")
// snippet-start:assertion
@TestInstance(Lifecycle.PER_CLASS)
class AssertionTest {

	private Flocessor flocessor;

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> tests() {
		flocessor = new Flocessor( "Ben behaviour", new Greetings() )
				.system( State.LESS, BEN )
				.behaviour( asrt -> {
					String input = new String( asrt.expected().request().content(), UTF_8 );
					String output = BenSys.getGreetingResponse( input );
					asrt.actual()
							.request( input.getBytes( UTF_8 ) )
							.response( output.getBytes( UTF_8 ) );
				} );
		return flocessor.tests();
	}

	/** Closes reporting after dynamic children, not when their factory returns. */
	@AfterAll
	void completeFlows() {
		if( flocessor != null ) {
			flocessor.close();
		}
	}
}
// snippet-end:assertion
