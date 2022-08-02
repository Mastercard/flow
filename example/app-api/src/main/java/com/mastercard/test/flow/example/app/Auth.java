package com.mastercard.test.flow.example.app;

import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Operation.Header;
import com.mastercard.test.flow.example.framework.Operation.Path;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Checks user credentials and permissions
 */
public interface Auth extends Service {

	/**
	 * @param basic HTTP basic auth header
	 * @return <code>true</code> if the user has been authenticated
	 */
	@Operation(method = "GET", path = "/auth")
	boolean authenticate( @Header("Authorization") String basic );

	/**
	 * @param user     user name
	 * @param password user secret
	 * @return <code>true</code> if the user has been authenticated
	 */
	@Operation(method = "GET", path = "/auth/user/:user")
	boolean authenticate( @Path("user") String user, @Body String password );

	/**
	 * @param user   The user name
	 * @param action The name of a system action
	 * @return <code>true</code> if the user is allowed to do the specified action
	 */
	@Operation(method = "GET", path = "/permission/user/:user/action/:action")
	boolean permitted( @Path("user") String user, @Path("action") String action );

	/**
	 * Allows users to perform restricted actions
	 *
	 * @param user The user to allow all actions for
	 */
	@Operation(method = "POST", path = "/permission/user/:user/elevate")
	void elevate( @Path("user") String user );

	/**
	 * Removes elevated permissions
	 *
	 * @param user The user to restrict
	 */
	@Operation(method = "POST", path = "/permission/user/:user/demote")
	void demote( @Path("user") String user );
}
