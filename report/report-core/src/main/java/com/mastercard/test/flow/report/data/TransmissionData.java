package com.mastercard.test.flow.report.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Message;

/**
 * Encapsulates the details of a single request or response
 */
public class TransmissionData {

	/**
	 * The full message data
	 */
	@JsonProperty("full")
	public final MessageData full;

	/**
	 * The message data that was asserted (i.e.: after masking operations)
	 */
	@JsonProperty("asserted")
	public final AssertedData asserted;

	/**
	 * @param full     Complete message data
	 * @param asserted What was asserted
	 */
	public TransmissionData(
			@JsonProperty("full") MessageData full,
			@JsonProperty("asserted") AssertedData asserted ) {

		this.full = full;
		this.asserted = asserted;
	}

	/**
	 * @param request Complete message data
	 */
	public TransmissionData( Message request ) {
		full = new MessageData( request.assertable(), request.content(), null, null );
		asserted = new AssertedData( null, null );
	}

}
