package com.mastercard.test.flow.example.app;

import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Operation.Path;
import com.mastercard.test.flow.example.framework.Service;

/**
 * key/value data storage
 */
public interface Store extends Service {

	/**
	 * Stores data
	 *
	 * @param key  Key under which to store
	 * @param data Data to store
	 */
	@Operation(method = "PUT", path = "/data/:key")
	void store( @Path("key") String key, @Body String data );

	/**
	 * Retrieves stored data
	 *
	 * @param key as supplied to {@link #store(String, String)}
	 * @return The stored data
	 */
	@Operation(method = "GET", path = "/data/:key")
	String retrieve( @Path("key") String key );

	/**
	 * Deletes data
	 *
	 * @param key as supplied to {@link #store(String, String)}
	 * @return The data that <i>was</i> stored under that key
	 */
	@Operation(method = "DELETE", path = "/data/:key")
	String delete( @Path("key") String key );

	/**
	 * Deletes all data
	 */
	@Operation(method = "DELETE", path = "/data")
	void clear();
}
