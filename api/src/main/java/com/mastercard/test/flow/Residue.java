package com.mastercard.test.flow;

/**
 * <p>
 * Represents a persistent impact of the behaviour documented in a {@link Flow}
 * </p>
 * <p>
 * Assertion components should confirm that a {@link Flow}'s residual impacts
 * are present after processing it
 */
public interface Residue {

	/**
	 * Defines a human-readable name for this {@link Residue}. This will be
	 * presented in execution reports.
	 *
	 * @return A human-readable name for this {@link Residue}.
	 */
	String name();

	/**
	 * Creates a new {@link Residue} that is based on this instance, but that can be
	 * updated independently without affecting this instance.
	 *
	 * @return A new {@link Residue} based on this one.
	 */
	Residue child();
}
