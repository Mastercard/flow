package com.mastercard.test.flow.builder.steps;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;

/**
 * The second stage of defining an interaction - setting the destination.
 *
 * @param <R> The type that initiated the call sequence
 */
public class To<R> {
	private final R returnTo;
	private final MutableInteraction building;

	/**
	 * @param returnTo The object to return control to when the call sequence is
	 *                 complete
	 * @param building The interaction that we're populating
	 */
	public To( R returnTo, MutableInteraction building ) {
		this.returnTo = returnTo;
		this.building = building;
	}

	/**
	 * Sets the destination of the interaction
	 *
	 * @param destination The responder in the resulting {@link Interaction}
	 * @return The next stage of the call sequence
	 */
	public Request<R> to( Actor destination ) {
		building.responder( destination );
		return new Request<>( returnTo, building );
	}

}
