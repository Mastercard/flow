package com.mastercard.test.flow.assrt.filter;

import java.util.Set;

import com.mastercard.test.flow.Flow;

/**
 * Access methods for {@link Filter} configuration
 */
public interface FilterConfiguration {
	/**
	 * Gets all tags in the model
	 *
	 * @return All tags available in the model
	 */
	Set<String> allTags();

	/**
	 * Gets the tags that flows must have to pass the filter.
	 *
	 * @return The current set of included tags
	 */
	Set<String> includedTags();

	/**
	 * Sets the tags that flows must have to pass the filter. Materially changing
	 * the tag filters may change selected index values.
	 *
	 * @param tags The new included tag values
	 * @return <code>this</code>
	 */
	Filter includedTags( Set<String> tags );

	/**
	 * Gets the tags that flows must not have to pass the filter.
	 *
	 * @return The current set of excluded tags
	 */
	Set<String> excludedTags();

	/**
	 * Sets the tags that flows must not have to pass the filter. Materially
	 * changing the tag filters may change selected index values.
	 *
	 * @param tags The new excluded tag values
	 * @return <code>this</code>
	 */
	Filter excludedTags( Set<String> tags );

	/**
	 * Gets the indices in the tag-filtered list of flows that pass the filter
	 *
	 * @return The current set of chosen {@link Flow} indices
	 */
	Set<Integer> indices();

	/**
	 * Sets the indices in the tag-filtered list of flows that pass the filter
	 *
	 * @param idx The new selected {@link Flow} indices
	 * @return <code>this</code>
	 */
	Filter indices( Set<Integer> idx );
}
