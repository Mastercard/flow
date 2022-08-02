package com.mastercard.test.flow.report.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulates the details of a single message
 */
public class MessageData {

	/**
	 * Expected message content in human-readable form
	 */
	@JsonProperty("expect")
	public String expect;
	/**
	 * bytes of the expected message as they appear on the wire. Jackson will take
	 * care of converting this data to and from a base64-string
	 */
	@JsonProperty("expectBytes")
	public byte[] expectBytes;

	/**
	 * Actual message content in human-readable form
	 */
	@JsonProperty("actual")
	public String actual;

	/**
	 * bytes of the actual message as they appear on the wire. Jackson will take
	 * care of converting this data to and from a base64-string
	 */
	@JsonProperty("actualBytes")
	public byte[] actualBytes;

	/**
	 * @param expect      human-readable expected message content
	 * @param expectBytes bytes of the expected message
	 * @param actual      human-readable actual message content
	 * @param actualBytes bytes of the actual message
	 */
	public MessageData(
			@JsonProperty("expect") String expect,
			@JsonProperty("expectBytes") byte[] expectBytes,
			@JsonProperty("actual") String actual,
			@JsonProperty("actualBytes") byte[] actualBytes ) {
		this.expect = expect;
		this.expectBytes = expectBytes;
		this.actual = actual;
		this.actualBytes = actualBytes;
	}

}
