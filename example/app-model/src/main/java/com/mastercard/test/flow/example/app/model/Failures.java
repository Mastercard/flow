package com.mastercard.test.flow.example.app.model;

import static com.mastercard.test.flow.example.app.model.Interactions.CORE;
import static com.mastercard.test.flow.example.app.model.Interactions.HISTOGRAM;
import static com.mastercard.test.flow.example.app.model.Interactions.UI;
import static com.mastercard.test.flow.example.app.model.Messages.notFound;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.app.model.ctx.Upness;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.util.Tags;

/**
 * Illustrates failure modes
 */
public class Failures extends EagerModel {
	/***/
	public static final TaggedGroup MODEL_TAGS = new TaggedGroup(
			"direct", "histogram" )
					.union( "failure", "timeout" );

	/**
	 * @param direct basis source
	 */
	public Failures( Direct direct ) {
		super( MODEL_TAGS );

		Flow noHist = Deriver.build( direct.hello, flow -> flow
				.meta( data -> data.tags( Tags.add( "failure" ) )
						.motivation( "Shows what happens when the histogram service fails" ) )
				.context( new Upness().broken( Actors.HISTOGRAM ) )
				.update( HISTOGRAM, i -> i.response( Messages.error() ) )
				.update( CORE, i -> i.response( notFound() ) )
				.update( UI, i -> i.response( notFound() ) ) );

		Flow histTimeout = Deriver.build( direct.hello, flow -> flow
				.meta( data -> data.tags( Tags.add( "timeout" ) )
						.motivation( "Shows what happens when the histogram service times out" ) )
				.context( new Upness().down( Actors.HISTOGRAM ) )
				.update( HISTOGRAM, i -> i.response( new Text(
						"java.net.SocketTimeoutException: Read timed out" ) ) )
				.update( CORE, i -> i.response( notFound() ) )
				.update( UI, i -> i.response( notFound() ) ) );

		members( flatten( noHist, histTimeout ) );
	}

}
