package com.mastercard.test.flow.validation;

import java.util.stream.Stream;

import com.mastercard.test.flow.Model;

/**
 * A validation check that can be applied to a {@link Model} or constituents
 * thereof
 */
public interface Validation {

	/**
	 * Defines a name for the validation.
	 *
	 * @return A human-readable name for this {@link Validation}
	 */
	String name();

	/**
	 * Defines an explanation for the validation's rationale.
	 *
	 * @return A human readable explanation of why this {@link Validation} exists
	 */
	String explanation();

	/**
	 * Produces the validation checks that should be processed to assert
	 * {@link Model} validity
	 *
	 * @param model The model to check
	 * @return A stream of {@link Check}s that should be exercised
	 */
	Stream<Check> checks( Model model );
}
