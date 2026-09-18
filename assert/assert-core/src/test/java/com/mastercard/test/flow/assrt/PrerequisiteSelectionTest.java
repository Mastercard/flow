package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.TestModel.Actors.A;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static com.mastercard.test.flow.builder.Builder.SELF;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.mock.Mdl;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.msg.txt.Text;

/**
 * Selection of prerequisite flows
 */
@SuppressWarnings("static-method")
class PrerequisiteSelectionTest {

	/**
	 * A binding from one message to another within the same flow is published, but
	 * does not make the flow wait for itself
	 */
	@Test
	void withinFlowBindingPublishesWithoutSelfWait() {
		List<String> mutations = new ArrayList<>();
		Flow flow = Creator.build( f -> f.meta( m -> m.description( "self" ) )
				.call( i -> i.from( A ).to( B ).request( new Text( "value" ) )
						.response( new Text( "value" ) ) )
				.dependency( SELF, d -> d.from( i -> true, REQUEST, ".+" )
						.mutate( value -> {
							mutations.add( String.valueOf( value ) );
							return value + "-bound";
						} ).to( i -> true, RESPONSE, ".+" ) ) );
		List<Flow> bodies = new ArrayList<>();
		TestFlocessor runner = new TestFlocessor( "self binding", new Mdl().withFlows( flow ) )
				.system( State.FUL, B ).reporting( Reporting.NEVER )
				.behaviour( a -> {
					bodies.add( a.flow() );
					a.actual().request( "value".getBytes( UTF_8 ) )
							.response( "value-bound".getBytes( UTF_8 ) );
				} );
		runner.execute();
		assertEquals( "self [] SUCCESS", runner.results() );
		assertEquals( List.of( flow ), bodies );
		assertEquals( List.of( "value" ), mutations );
		assertEquals( "value-bound", flow.root().response().assertable() );
	}
}
