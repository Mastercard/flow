package com.mastercard.test.flow.report.data;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Flow;

/**
 * How a {@link Flow} is represented in the {@link Index}
 */
public class Entry {

	/**
	 * Flow description
	 */
	@JsonProperty("description")
	public final String description;

	/**
	 * Flow tags
	 */
	@JsonProperty("tags")
	public final Set<String> tags;

	/**
	 * Flow detail file name
	 */
	@JsonProperty("detail")
	public final String detail;

	/**
	 * @param description Flow description
	 * @param tags        Flow tags
	 * @param detail      Flow detail file name
	 */
	public Entry(
			@JsonProperty("description") String description,
			@JsonProperty("tags") Collection<String> tags,
			@JsonProperty("detail") String detail ) {
		this.description = description;
		this.tags = Collections.unmodifiableSet( new TreeSet<>( tags ) );
		this.detail = detail;
	}

}
