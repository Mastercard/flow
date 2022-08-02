package com.mastercard.test.flow.assrt;

import java.util.Arrays;

import com.mastercard.test.flow.Interaction;

/**
 * Encapsulates the results of exercising an expected {@link Interaction}
 * against the system under test
 */
public class Actual {
	private byte[] request;
	private byte[] response;

	// These methods gets called from user code. The failures that would stem from
	// the array being changed under our feet would be extraordinarily painful to
	// debug, so we're taking a defensive copies of the data

	/**
	 * Retrieves the observed request data
	 *
	 * @return The actual request bytes
	 */
	public byte[] request() {
		return request != null
				? Arrays.copyOf( request, request.length )
				: null;
	}

	/**
	 * Populates the observed request data
	 *
	 * @param r The actual request bytes
	 * @return <code>this</code>
	 */
	public Actual request( byte[] r ) {
		request = r != null
				? Arrays.copyOf( r, r.length )
				: null;
		return this;
	}

	/**
	 * Retrieves the observed response data
	 *
	 * @return The actual response bytes
	 */
	public byte[] response() {
		return response != null
				? Arrays.copyOf( response, response.length )
				: null;
	}

	/**
	 * Populates the observed response data
	 *
	 * @param r The actual response bytes
	 * @return <code>this</code>
	 */
	public Actual response( byte[] r ) {
		response = r != null
				? Arrays.copyOf( r, r.length )
				: null;
		return this;
	}
}
