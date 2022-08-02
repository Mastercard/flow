package com.mastercard.test.flow.example.app.rmt;

import java.net.URL;

import com.mastercard.test.flow.example.app.Core;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Remote;
import com.mastercard.test.flow.example.framework.Remotes;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Proxy for a {@link Core} {@link Service} that exists in another
 * {@link Instance}
 */
public class RemoteCore extends Remote implements Core {

	/**
	 * Registers this implementation with the framework
	 */
	public static void register() {
		Remotes.register( Core.class, RemoteCore::new );
	}

	/**
	 * @param url The url of the other {@link Instance}
	 */
	public RemoteCore( URL url ) {
		super( url );
	}

	@Override
	public Response process( Request req ) {
		return (Response) invoke( 1, req );
	}

	@Override
	public Response query( String deferredID ) {
		return (Response) invoke( 2, deferredID );
	}

	@Override
	public Response get( String deferredID ) {
		return (Response) invoke( 3, deferredID );
	}

	@Override
	public void delete( String deferredID ) {
		invoke( 4, deferredID );
	}
}
