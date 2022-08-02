package com.mastercard.test.flow.example.app;

import java.util.Map;

import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Operation.Path;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Stores up analysis tasks for asynchronous processing.
 */
public interface Queue extends Service {

	/**
	 * Adds a task
	 *
	 * @param text The text to analyse
	 * @return An id for status and result retrieval
	 */
	@Operation(method = "POST", path = "/queue/add")
	String enqueue( @Body String text );

	/**
	 * Task status query results
	 */
	enum Status {
		/***/
		PENDING,
		/***/
		COMPLETE,
		/***/
		NO_SUCH_TASK
	}

	/**
	 * Gets the status of a task
	 *
	 * @param id as returned from {@link #enqueue(String)}
	 * @return task status
	 */
	@Operation(method = "GET", path = "/queue/:id/status")
	Status status( @Path("id") String id );

	/**
	 * Gets the task results
	 *
	 * @param id as returned from {@link #enqueue(String)}
	 * @return A map from character to incidence count
	 */
	@Operation(method = "POST", path = "/queue/:id/result")
	Map<String, Integer> result( @Path("id") String id );

	/**
	 * Deletes the task and results
	 *
	 * @param id as returned from {@link #enqueue(String)}
	 */
	@Operation(method = "DELETE", path = "/queue/:id")
	void delete( @Path("id") String id );

	/**
	 * Updates the service configuration
	 * 
	 * @param config The new configuration values to be merged with the current set
	 */
	@Operation(method = "POST", path = "/queue/configure")
	void configure( @Body Map<String, String> config );
}
