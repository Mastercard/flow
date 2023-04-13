package com.mastercard.test.flow;

import java.util.Optional;
import java.util.function.Function;

/**
 * Address for a single {@link Message} field in a {@link Flow}
 */
public interface FieldAddress {

	/**
	 * Defines the {@link Flow} that contains the addressed field
	 *
	 * @return the {@link Flow} of interest
	 */
	Flow flow();

	/**
	 * Defines the {@link Interaction} that contains the addressed field
	 *
	 * @return How to get the {@link Interaction} of interest from a {@link Flow}
	 */
	Function<Flow, Interaction> interaction();

	/**
	 * Defines the {@link Message} that contains the addressed field
	 *
	 * @return How to get the {@link Message} of interest from an
	 *         {@link Interaction}
	 */
	Function<Interaction, Message> message();

	/**
	 * Defines the address of the field within the {@link Message}
	 *
	 * @return The address of the field within the {@link Message} of interest
	 */
	String field();

	/**
	 * Determines if the address is fully-specified, i.e.: it unambiguously
	 * identifies a single field in a single message in a single flow
	 *
	 * @return <code>true</code> if this address is complete
	 */
	default boolean isComplete() {
		return flow() != null
				&& interaction() != null
				&& message() != null
				&& field() != null;
	}

	/**
	 * Convenience accessor for the {@link Flow}
	 *
	 * @return The addressed {@link Flow}
	 */
	default Optional<Flow> getFlow() {
		return Optional.ofNullable( flow() );
	}

	/**
	 * Convenience accessor for the {@link Interaction}
	 *
	 * @return The addressed interaction
	 */
	default Optional<Interaction> getInteraction() {
		if( interaction() != null ) {
			return getFlow()
					.map( interaction() );
		}
		return Optional.empty();
	}

	/**
	 * Convenience accessor for the {@link Message}
	 *
	 * @return The addressed message
	 */
	default Optional<Message> getMessage() {
		if( message() != null ) {
			return getInteraction()
					.map( message() );
		}
		return Optional.empty();
	}

	/**
	 * Convenience accessor for the field value
	 *
	 * @return The field value
	 */
	default Optional<Object> getValue() {
		if( field() != null ) {
			return getMessage()
					.map( m -> m.get( field() ) );
		}
		return Optional.empty();
	}
}
