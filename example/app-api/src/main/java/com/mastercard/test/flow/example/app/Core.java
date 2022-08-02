package com.mastercard.test.flow.example.app;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Operation.Path;
import com.mastercard.test.flow.example.framework.Service;

/**
 * The core service orchestrates the other services to implement functionality
 */
public interface Core extends Service {

	/**
	 * @param req Operation inputs
	 * @return Operation results
	 */
	@Operation(method = "POST", path = "/process")
	Response process( @Body Request req );

	/**
	 * @param id ID of a deferred operation
	 * @return operation status
	 */
	@Operation(method = "GET", path = "/query/:id")
	Response query( @Path("id") String id );

	/**
	 * @param id ID of a deferred operation
	 * @return operation results
	 */
	@Operation(method = "POST", path = "/result/:id")
	Response get( @Path("id") String id );

	/**
	 * Deletes a deferred operation
	 *
	 * @param id ID of a deferred operation
	 */
	@Operation(method = "DELETE", path = "/result/:id")
	void delete( @Path("id") String id );

	/**
	 * Encapsulates input data
	 */
	static class Request {
		/**
		 * <code>true</code> to return immediately with a deferredID value
		 */
		@JsonProperty("defer")
		public final boolean defer;
		/**
		 * Optional, the characters to count
		 */
		@JsonProperty("characters")
		public final String characters;
		/**
		 * The text to process
		 */
		@JsonProperty("text")
		public final String text;

		/**
		 * @param defer      <code>true</code> to return immediately with a deferredID
		 *                   value
		 * @param characters Optional, the characters to count
		 * @param text       The text to process
		 */
		public Request(
				@JsonProperty("defer") boolean defer,
				@JsonProperty("characters") String characters,
				@JsonProperty("text") String text ) {
			this.defer = defer;
			this.characters = characters;
			this.text = text;
		}
	}

	/**
	 * Encapsulates output data
	 */
	static class Response {
		/**
		 * Present when {@link Request#defer} was <code>true</code>, the ID that can be
		 * used to retrieve or delete results
		 */
		@JsonProperty("deferredID")
		public final String deferredID;

		/**
		 * Indicates if processing is complete
		 */
		@JsonProperty("deferredStatus")
		public final Queue.Status deferredStatus;

		/**
		 * The processing results
		 */
		@JsonProperty("result")
		public final Map<String, Integer> result;

		/**
		 * @param deferredID     the ID that can be used to retrieve or delete deferred
		 *                       results
		 * @param deferredStatus Indicates if processing is complete
		 * @param result         processing results
		 */
		public Response(
				@JsonProperty("deferredID") String deferredID,
				@JsonProperty("deferredStatus") Queue.Status deferredStatus,
				@JsonProperty("result") Map<String, Integer> result ) {
			this.deferredID = deferredID;
			this.deferredStatus = deferredStatus;
			this.result = result;
		}
	}
}
