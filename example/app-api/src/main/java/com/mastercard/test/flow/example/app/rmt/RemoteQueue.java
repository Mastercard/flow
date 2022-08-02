package com.mastercard.test.flow.example.app.rmt;

import java.net.URL;
import java.util.Map;

import com.mastercard.test.flow.example.app.Queue;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Remote;
import com.mastercard.test.flow.example.framework.Remotes;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Proxy for a {@link Queue} {@link Service} that exists in another
 * {@link Instance}
 */
public class RemoteQueue extends Remote implements Queue {
	/**
	 * Registers this implementation with the framework
	 */
	public static void register() {
		Remotes.register( Queue.class, RemoteQueue::new );
	}

	/**
	 * @param url The url of the other {@link Instance}
	 */
	public RemoteQueue( URL url ) {
		super( url );
	}

	@Override
	public void configure( Map<String, String> config ) {
		invoke( 1, config );
	}

	@Override
	public String enqueue( String text ) {
		return (String) invoke( 2, text );
	}

	@Override
	public Status status( String id ) {
		return (Status) invoke( 3, id );
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Integer> result( String id ) {
		return (Map<String, Integer>) invoke( 4, id );
	}

	@Override
	public void delete( String id ) {
		invoke( 5, id );
	}

}
