package com.mastercard.test.flow;

import java.util.Set;

/**
 * Data attached to {@link Flow}s for human consumption
 */
public interface Metadata {

	/**
	 * <p>
	 * Defines the identity for the {@link Flow}, which must be unique within a
	 * given {@link Model}.
	 * </p>
	 * <p>
	 * Tag values should form the vast majority of a {@link Flow}'s identity. If you
	 * find yourself repeating the same word in the {@link #description()} of
	 * multiple {@link Flow}s, consider adding that word as a tag instead
	 *
	 * @return A human-readable identifier for the {@link Flow}. This must be unique
	 *         in any {@link Model} that contains the {@link Flow}
	 */
	default String id() {
		return String.format( "%s %s", description(), tags() );
	}

	/**
	 * The description should be very short - just enough to disambiguate
	 * {@link Flow} instances that bear the same set of {@link #tags()}
	 *
	 * @return The description
	 */
	String description();

	/**
	 * Tags should form the overwhelming majority of the {@link #id()} value
	 *
	 * @return tag values
	 */
	Set<String> tags();

	/**
	 * Defines a human-readable description for why the flow exists.
	 *
	 * @return A description of why the flow exists
	 */
	String motivation();

	/**
	 * Defines a human-readable hint on where to find the flow definition in the
	 * codebase. This should be presented on assertion failures.
	 *
	 * @return A locator for the definition of the flow in the codebase
	 */
	String trace();
}
