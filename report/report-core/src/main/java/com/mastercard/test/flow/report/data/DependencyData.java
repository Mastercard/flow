package com.mastercard.test.flow.report.data;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;

/**
 * Encapsulates the human-readable identity of the {@link Flow} on the source
 * side of a {@link Dependency}
 */
public class DependencyData {

	/**
	 * @see Metadata#description()
	 */
	@JsonProperty("description")
	public final String description;
	/**
	 * @see Metadata#tags()
	 */
	@JsonProperty("tags")
	public final Set<String> tags;

	/**
	 * @param description {@link Flow} description
	 * @param tags        {@link Flow} tags
	 */
	public DependencyData(
			@JsonProperty("description") String description,
			@JsonProperty("tags") Set<String> tags ) {
		this.description = description;
		this.tags = tags;
	}

}
