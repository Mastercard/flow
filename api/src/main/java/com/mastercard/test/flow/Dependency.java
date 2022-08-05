package com.mastercard.test.flow;

import java.util.function.UnaryOperator;

/**
 * Defines an inter-{@link Flow} data dependency. These should be fulfilled when
 * {@link Flow}s are built and again when they are processed.
 */
public interface Dependency {

	/**
	 * Defines the origin of the data dependency
	 *
	 * @return The {@link Message} field to copy the data from
	 */
	FieldAddress source();

	/**
	 * Defines how the data is changed as the dependency is fulfilled
	 *
	 * @return How the data is transformed after it gets pulled from the
	 *         {@link #source()} and before it gets populated into the
	 *         {@link #sink()}
	 */
	UnaryOperator<Object> mutation();

	/**
	 * Defines the destination of the data dependency
	 *
	 * @return The {@link Message} field to populate the data to
	 */
	FieldAddress sink();
}
