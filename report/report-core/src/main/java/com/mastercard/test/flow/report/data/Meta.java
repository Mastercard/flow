package com.mastercard.test.flow.report.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Model;

/**
 * Metadata attached to the report index
 */
public class Meta {
	/**
	 * The title of the {@link Model} that supplied the test data
	 */
	@JsonProperty("modelTitle")
	public final String modelTitle;

	/**
	 * The title of the test that exercised the data
	 */
	@JsonProperty("testTitle")
	public final String testTitle;

	/**
	 * The timestamp of when the report was generated (number of milliseconds since
	 * 1970 epoch)
	 */
	@JsonProperty("timestamp")
	public final long timestamp;

	/**
	 * @param modelTitle The name of the model being reported on
	 * @param testTitle  The name of the execution producing the report
	 * @param timestamp  Now (milliseconds since epoch)
	 */
	public Meta(
			@JsonProperty("modelTitle") String modelTitle,
			@JsonProperty("testTitle") String testTitle,
			@JsonProperty("timestamp") long timestamp ) {
		this.modelTitle = modelTitle;
		this.testTitle = testTitle;
		this.timestamp = timestamp;
	}
}
