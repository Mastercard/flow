package com.mastercard.test.flow.report.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Residue;

/**
 * Encapsulates a {@link Residue} content
 */
public class ResidueData {

	/**
	 * A human readable name for the data
	 */
	@JsonProperty("name")
	public final String name;

	/**
	 * The residue object data
	 */
	@JsonProperty("raw")
	public final Object raw;

	/**
	 * Extracted and expected formatted residue data, without masking operations
	 * applied
	 */
	@JsonProperty("full")
	public AssertedData full;

	/**
	 * Extracted and expected formatted residue data, as asserted.
	 */
	@JsonProperty("masked")
	public AssertedData masked;

	/**
	 * @param name   {@link Residue#name()}
	 * @param raw    {@link Residue} object itself
	 * @param full   Extracted and expected formatted residue data, without masking
	 *               operations applied
	 * @param masked Extracted and expected formatted residue data, as asserted.
	 */
	public ResidueData(
			@JsonProperty("name") String name,
			@JsonProperty("raw") Object raw,
			@JsonProperty("full") AssertedData full,
			@JsonProperty("masked") AssertedData masked ) {
		this.name = name;
		this.raw = raw;
		this.full = full;
		this.masked = masked;
	}

}
