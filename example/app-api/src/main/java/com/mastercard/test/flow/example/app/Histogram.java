package com.mastercard.test.flow.example.app;

import java.util.Map;

import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Operation.Query;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Counts characters
 */
public interface Histogram extends Service {

	/**
	 * @param text The text to analyse
	 * @return A map from character to incidence count
	 */
	@Operation(method = "POST", path = "/count/all")
	Map<String, Integer> histogram( @Body String text );

	/**
	 * @param text       The text to analyse
	 * @param characters The characters of interest
	 * @return The counts of those characters
	 */
	@Operation(method = "POST", path = "/count/subset")
	Map<String, Integer> histogram( @Body String text,
			@Query("characters") String characters );
}
