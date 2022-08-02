package com.mastercard.test.flow;

import java.util.Set;

/**
 * Data that is passed between {@link Actor}s in an {@link Interaction}
 */
public interface Message {

	/**
	 * Creates a new independent {@link Message} that holds the same data as the
	 * current instance
	 *
	 * @return A new {@link Message} that is based on this instance, but that can be
	 *         updated independently without affecting this instance
	 */
	Message child();

	/**
	 * Creates a new {@link Message} of the same type as this instance
	 *
	 * @param content message data
	 * @return A new {@link Message} of the same type as <code>this</code>, but with
	 *         the supplied content
	 */
	Message peer( byte[] content );

	/**
	 * Creates a human-readable string representation of the message data, suitable
	 * for assertion comparison.
	 *
	 * @param masks The sources of unpredictability for fields that should be masked
	 *              out
	 * @return A human-readable representation of message content, suitable for
	 *         assertion comparison
	 */
	String assertable( Unpredictable... masks );

	/**
	 * Returns the message data encoded as it would be in the actual system
	 *
	 * @return The bytes of the message content as they would appear on the wire
	 */
	byte[] content();

	/**
	 * Defines the set of fields in the {@link Message}, such as can be passed to
	 * {@link #get(String)} and {@link #set(String, Object)}
	 *
	 * @return The addressable fields in this message
	 */
	Set<String> fields();

	/**
	 * Sets a field value
	 *
	 * @param field The field address to update
	 * @param value The new value
	 * @return <code>this</code>
	 */
	Message set( String field, Object value );

	/**
	 * Gets a field value
	 *
	 * @param field The field address to retrieve
	 * @return The field value
	 */
	Object get( String field );
}
