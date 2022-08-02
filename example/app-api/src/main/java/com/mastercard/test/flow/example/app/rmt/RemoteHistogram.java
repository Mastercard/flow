package com.mastercard.test.flow.example.app.rmt;

import java.net.URL;
import java.util.Map;

import com.mastercard.test.flow.example.app.Histogram;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Remote;
import com.mastercard.test.flow.example.framework.Remotes;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Proxy for a {@link Histogram} {@link Service} that exists in another
 * {@link Instance}
 */
@SuppressWarnings("unchecked")
public class RemoteHistogram extends Remote implements Histogram {

	/**
	 * Registers this implementation with the framework
	 */
	public static void register() {
		Remotes.register( Histogram.class, RemoteHistogram::new );
	}

	/**
	 * @param url The url of the other instance
	 */
	public RemoteHistogram( URL url ) {
		super( url );
	}

	@Override
	public Map<String, Integer> histogram( String text ) {
		return (Map<String, Integer>) invoke( 1, text );
	}

	@Override
	public Map<String, Integer> histogram( String text, String characters ) {
		return (Map<String, Integer>) invoke( 2, text, characters );
	}
}
