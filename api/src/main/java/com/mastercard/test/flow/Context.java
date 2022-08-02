package com.mastercard.test.flow;

import java.util.Set;

/**
 * <p>
 * Represents aspects of the environment in which the behaviour modelled by a
 * {@link Flow} is expected to be valid.
 * </p>
 * <p>
 * Some aspects of testing context are so obviously implicit (e.g.: that the
 * system under test is running) that there's little point in including them in
 * the system model, but some aspects (e.g.: the system is configured in a
 * particular way) should be modelled.
 * </p>
 * <p>
 * Assertion components should configure the system under test to match a
 * {@link Flow}'s {@link Context} before attempting to process it.
 */
public interface Context {

	/**
	 * Defines a human-readable name for this {@link Context}. This will be
	 * presented in execution reports.
	 *
	 * @return A human-readable name for this {@link Context}.
	 */
	String name();

	/**
	 * Defines the set of {@link Actor}s for which this context is applicable. This
	 * is used by assertion components to decide whether a given {@link Context} is
	 * applicable to the system under test.
	 *
	 * @return The set of system {@link Actor}s for which this context is
	 *         applicable.
	 */
	Set<Actor> domain();

	/**
	 * Creates a new {@link Context} that is based on this instance, but that can be
	 * updated independently without affecting this instance.
	 *
	 * @return A new {@link Context} based on this one.
	 */
	Context child();
}
