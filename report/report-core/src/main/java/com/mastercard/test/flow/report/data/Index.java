package com.mastercard.test.flow.report.data;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Report index data
 */
public class Index {

	/**
	 * Index metadata - title, time of generation, etc
	 */
	@JsonProperty("meta")
	public final Meta meta;

	/**
	 * Index entries
	 */
	@JsonProperty("entries")
	public final List<Entry> entries;

	/**
	 * @param meta    index metadata
	 * @param entries index entries
	 */
	public Index(
			@JsonProperty("meta") Meta meta,
			@JsonProperty("entries") List<Entry> entries ) {
		this.meta = meta;
		this.entries = Collections.unmodifiableList( entries );
	}
}
