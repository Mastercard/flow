package com.mastercard.test.flow.validation;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * An individual instance of the validation behaviour
 */
public class Check {

	private final Validation validation;
	private final String name;
	private Supplier<Violation> behaviour;

	/**
	 * @param validation The parent {@link Validation}
	 * @param name       A human-readable name for this check
	 * @param check      How to get the results of the check
	 */
	public Check( Validation validation, String name, Supplier<Violation> check ) {
		this.validation = validation;
		this.name = name;
		behaviour = check;
	}

	/**
	 * Accessor for the {@link Validation} that produced this check
	 *
	 * @return The parent {@link Validation}
	 */
	public Validation validation() {
		return validation;
	}

	/**
	 * Defines a name for the check.
	 *
	 * @return A human readable name for this {@link Check}
	 */
	public String name() {
		return name;
	}

	/**
	 * Produces the result of the check
	 *
	 * @return The results of the check, an empty optional of the check has been
	 *         satisfied
	 */
	public Optional<Violation> check() {
		return Optional.ofNullable( behaviour.get() );
	}
}
