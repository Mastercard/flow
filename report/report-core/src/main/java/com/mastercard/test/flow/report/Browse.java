package com.mastercard.test.flow.report;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Consumer;

import com.mastercard.test.flow.util.Option;

/**
 * Utility for opening a browser on some target
 */
public class Browse {

	/**
	 * Allows control over browser-opening behaviour
	 */
	public static final Option SUPPRESS = new Option.Builder()
			.property( "mctf.suppress.browse" )
			.description( "Supply 'true' to suppress attempts to open browsers" );

	private Browse() {
		// no instances
	}

	/**
	 * Attempts to open a browser to view the supplied target
	 *
	 * @param uri      The target
	 * @param failSink What to do with failures
	 */
	@SafeVarargs
	public static void browse( String uri, Consumer<Exception>... failSink ) {
		try {
			browse( new URI( uri ), failSink );
		}
		catch( URISyntaxException e ) {
			for( Consumer<Exception> sink : failSink ) {
				sink.accept( e );
			}
		}
	}

	/**
	 * Attempts to open a browser to view the supplied target
	 *
	 * @param url      The target
	 * @param failSink What to do with failures
	 */
	@SafeVarargs
	public static void browse( URL url, Consumer<Exception>... failSink ) {
		try {
			browse( url.toURI(), failSink );
		}
		catch( URISyntaxException e ) {
			for( Consumer<Exception> sink : failSink ) {
				sink.accept( e );
			}
		}
	}

	/**
	 * Attempts to open a browser to view the supplied target
	 *
	 * @param uri      The target
	 * @param failSink What to do with failures
	 */
	@SafeVarargs
	public static void browse( URI uri, Consumer<Exception>... failSink ) {
		if( SUPPRESS.isTrue() ) {
			return;
		}

		try {
			if( Desktop.isDesktopSupported()
					&& Desktop.getDesktop().isSupported( Action.BROWSE ) ) {
				Desktop.getDesktop().browse( uri );
			}
			else {
				// we might be on linux, where the browse action is poorly supported, but this
				// might work
				new ProcessBuilder( "xdg-open", uri.toString() ).start();
			}
		}
		catch( IOException e ) {
			for( Consumer<Exception> sink : failSink ) {
				sink.accept( e );
			}
		}
	}
}
