package com.mastercard.test.flow.example.app.rmt;

import java.net.URL;
import java.util.Map;

import com.mastercard.test.flow.example.app.Ui;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Remote;
import com.mastercard.test.flow.example.framework.Remotes;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Proxy for a {@link Ui} {@link Service} that exists in another
 * {@link Instance}
 */
public class RemoteUi extends Remote implements Ui {
	/**
	 * Registers this implementation with the framework
	 */
	public static void register() {
		Remotes.register( Ui.class, RemoteUi::new );
	}

	/**
	 * @param url The url of the other {@link Instance}
	 */
	public RemoteUi( URL url ) {
		super( url );
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Integer> histogram( String text ) {
		return (Map<String, Integer>) invoke( 1, text );
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Integer> histogram( String text, String characters ) {
		return (Map<String, Integer>) invoke( 2, text, characters );
	}

	@Override
	public String deferred( String text ) {
		return (String) invoke( 3, text );
	}

	@Override
	public String status( String id ) {
		return (String) invoke( 4, id );
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Integer> results( String id ) {
		return (Map<String, Integer>) invoke( 5, id );
	}

}
