package com.mastercard.test.flow.report.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The data that was asserted after masking operations were applied to message
 * content
 */
public class AssertedData {

	/**
	 * Expected message content
	 */
	@JsonProperty("expect")
	public String expect;

	/**
	 * Actual message content
	 */
	@JsonProperty("actual")
	public String actual;

	/**
	 * @param expect Expected message content
	 * @param actual Actual message content
	 */
	public AssertedData(
			@JsonProperty("expect") String expect,
			@JsonProperty("actual") String actual ) {
		this.expect = expect;
		this.actual = actual;
	}
}
