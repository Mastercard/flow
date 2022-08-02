package com.mastercard.test.flow.example.app.model;

import static com.mastercard.test.flow.builder.Sets.set;
import static com.mastercard.test.flow.example.app.model.Interactions.CORE;
import static com.mastercard.test.flow.example.app.model.Interactions.QUEUE;
import static com.mastercard.test.flow.example.app.model.Interactions.UI;
import static com.mastercard.test.flow.example.app.model.Messages.PATH_ID;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.app.model.ctx.QueueProcessing;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Flows that imply interactions that are not modelled
 */
public class Implicit extends EagerModel {

	/**
	 * Tags of flows in this model
	 */
	public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "deferred", "histogram" )
			.union( "enqueue", "retrieve" );

	/**
	 * We want to let queue processing happen naturally, so turn it on
	 */
	private static final QueueProcessing Q_ON = new QueueProcessing()
			.active( true );

	/**
	 * We want all queued tasks to be completed
	 */
	private static final QueueProcessing Q_EXHAUST = Q_ON.child()
			.exhausted( true );

	/**
	 * @param deferred provides basis {@link Flow}s
	 */
	public Implicit( Deferred deferred ) {
		super( MODEL_TAGS );

		Flow enqueue = Deriver.build( deferred.enqueue(), flow -> flow
				.context( Q_ON ) );

		Flow results = Deriver.build( deferred.results(), flow -> flow
				.context( Q_EXHAUST )
				// This context will cause the queue system to process all of its queued tasks.
				// This work involves the core and histogram services, but we don't want to
				// model those interactions. Hence we flag up the implicit service dependencies
				// and let the assert components act appropriately.
				// If the system under test includes core and histogram then this flow will be
				// processed, otherwise the flow will be skipped
				.implicit( set( Actors.CORE, Actors.HISTOGRAM ) )
				// we're skipping the intermediate flows from our basis chain, so we have to set
				// up the dependencies again
				.dependency( enqueue, d -> d
						.from( UI, RESPONSE, ".+" )
						.to( UI, REQUEST, PATH_ID ) )
				.dependency( enqueue, d -> d
						.from( CORE, RESPONSE, "deferredID" )
						.to( CORE, REQUEST, PATH_ID ) )
				.dependency( enqueue, d -> d
						.from( QUEUE, RESPONSE, ".+" )
						.to( QUEUE, REQUEST, PATH_ID ) ) );

		members( flatten( enqueue, results ) );
	}
}
