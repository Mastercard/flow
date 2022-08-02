package com.mastercard.test.flow.example.app.rmt;

import java.net.URL;

import com.mastercard.test.flow.example.app.Store;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Remote;
import com.mastercard.test.flow.example.framework.Remotes;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Proxy for a {@link Store} {@link Service} that exists in another
 * {@link Instance}
 */
public class RemoteStore extends Remote implements Store {

	/**
	 * Registers this implementation with the framework
	 */
	public static void register() {
		Remotes.register( Store.class, RemoteStore::new );
	}

	/**
	 * @param url The url of the other {@link Instance}
	 */
	public RemoteStore( URL url ) {
		super( url );
	}

	@Override
	public void store( String key, String data ) {
		invoke( 3, key, data );
	}

	@Override
	public String retrieve( String key ) {
		return (String) invoke( 5, key );
	}

	@Override
	public String delete( String key ) {
		return (String) invoke( 6, key );
	}

	@Override
	public void clear() {
		invoke( 7 );
	}

}
