package com.mastercard.test.flow.builder.mutable;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.builder.concrete.ConcreteRootInteraction;

/**
 * A builder for {@link ConcreteRootInteraction}
 */
public class MutableRootInteraction extends MutableInteraction {

	private Actor requester;

	/**
	 * Initially empty
	 */
	public MutableRootInteraction() {
		super( null );
	}

	/**
	 * @param basis The interaction to inherit state from
	 */
	public MutableRootInteraction( Interaction basis ) {
		super( null, basis );
		requester = basis.requester();
	}

	@Override
	public MutableInteraction requester( Actor r ) {
		requester = r;
		return this;
	}

	@Override
	public Actor requester() {
		return requester;
	}

	/**
	 * Builds an immutable copy of the current state
	 *
	 * @return An immutable {@link Interaction} instance
	 */
	public ConcreteRootInteraction build() {
		return addChildren( new ConcreteRootInteraction(
				requester(), request(),
				responder(), response(),
				tags() ) );
	}
}
