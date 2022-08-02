package com.mastercard.test.flow;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Atomic unit of system modelling. A request/response pair between two parts of
 * the system
 */
public interface Interaction {

	/**
	 * Defines the {@link Actor} the initiates this {@link Interaction}
	 *
	 * @return The {@link Actor} that sends the request
	 */
	Actor requester();

	/**
	 * Defines the content of the request
	 *
	 * @return The request content
	 */
	Message request();

	/**
	 * Defines the {@link Actor} the responds in this {@link Interaction}
	 *
	 * @return The {@link Actor} that sends the response
	 */
	Actor responder();

	/**
	 * Defines the content of the response
	 *
	 * @return The response content
	 */
	Message response();

	/**
	 * Defines the cause of this interaction. This can be <code>null</code> if the
	 * cause is external to the system model.
	 *
	 * @return The {@link Interaction} that caused this one, or <code>null</code> if
	 *         this is the entry point of a {@link Flow}
	 */
	Interaction parent();

	/**
	 * Defines the {@link Interaction}s that are caused by this one.
	 *
	 * @return The {@link Interaction}s that are caused by this one, in
	 *         chronological order
	 */
	Stream<Interaction> children();

	/**
	 * Tags can be used to disambiguate separate {@link Interaction}s between the
	 * same pair of {@link Actor}s in a single {@link Flow}
	 *
	 * @return tag values
	 */
	Set<String> tags();
}
