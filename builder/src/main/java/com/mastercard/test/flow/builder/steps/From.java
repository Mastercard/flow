package com.mastercard.test.flow.builder.steps;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;

/**
 * The first stage of defining an interaction - setting the origin.
 *
 * @param <R> The type that initiated the call sequence
 */
public class From<R> {
	private final R returnTo;
	private final MutableInteraction building;

	/**
	 * @param returnTo The object to return control to when the call sequence is
	 *                 complete
	 * @param building The interaction that we're building
	 */
	public From( R returnTo, MutableInteraction building ) {
		this.returnTo = returnTo;
		this.building = building;
	}

	/**
	 * Sets the source of the interaction
	 *
	 * @param source The requester in the resulting {@link Interaction}
	 * @return The next stage of the call sequence
	 */
	public To<R> from( Actor source ) {
		building.requester( source );
		return new To<>( returnTo, building );
	}

}
