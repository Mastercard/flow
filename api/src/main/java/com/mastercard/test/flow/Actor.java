package com.mastercard.test.flow;

/**
 * Represents a part of the system under test - something capable of making
 * requests to, and responding to requests from, other {@link Actor}s in the
 * system.
 */
public interface Actor {

	/**
	 * Defines a unique and human-readable name for the system component
	 *
	 * @return A human-readable name for this actor
	 */
	String name();
}
