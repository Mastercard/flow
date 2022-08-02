package com.mastercard.test.flow.example.app;

import java.util.Map;

import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Operation.Header;
import com.mastercard.test.flow.example.framework.Operation.Path;
import com.mastercard.test.flow.example.framework.Service;

/**
 * The gateway service defines the publicly-accessible interface to the
 * application
 */
public interface Ui extends Service {

	/**
	 * @param text Text to analyse
	 * @return A map of characters to incident counts
	 */
	@Operation(method = "POST", path = "/histogram")
	Map<String, Integer> histogram( @Body String text );

	/**
	 * @param text       Text to analyse
	 * @param characters The specific characters to count
	 * @return A map of characters to incident counts
	 */
	@Operation(method = "POST", path = "/histogram/subset")
	Map<String, Integer> histogram( @Body String text, @Header("characters") String characters );

	/**
	 * @param text Text to analyse
	 * @return An ID with which to query for results
	 */
	@Operation(method = "POST", path = "/histogram/deferred")
	String deferred( @Body String text );

	/**
	 * @param id As returned by {@link #deferred(String)}
	 * @return The status of the task
	 */
	@Operation(method = "GET", path = "/histogram/deferred/:id")
	String status( @Path("id") String id );

	/**
	 * @param id As returned by {@link #deferred(String)}
	 * @return The results of the task
	 */
	@Operation(method = "POST", path = "/histogram/deferred/:id")
	Map<String, Integer> results( @Path("id") String id );
}
