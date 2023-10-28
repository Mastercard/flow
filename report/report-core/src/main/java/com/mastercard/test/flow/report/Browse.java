package com.mastercard.test.flow.report;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

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

	/**
	 * Allows the option of a fallback browser-opening behaviour
	 */
	public static final Option XDG_OPEN_FALLBACK = new Option.Builder()
			.property( "mctf.browse.xdg_open" )
			.description( "Supply true to try and fall back to `xdg-open` when "
					+ "java's desktop integration fails" );

	private Browse() {
		// no instances
	}

	/**
	 * Attempts to open a browser to view the supplied target
	 *
	 * @param uri   The target
	 * @param sinks What to do with failures
	 */
	@SafeVarargs
	public static void browse( String uri, FailureSink... sinks ) {
		try {
			browse( new URI( uri ), sinks );
		}
		catch( URISyntaxException e ) {
			for( FailureSink sink : sinks ) {
				sink.log( "Failed to parse uri from '{}'", uri, e );
			}
		}
	}

	/**
	 * Attempts to open a browser to view the supplied target
	 *
	 * @param url   The target
	 * @param sinks What to do with failures
	 */
	@SafeVarargs
	public static void browse( URL url, FailureSink... sinks ) {
		try {
			browse( url.toURI(), sinks );
		}
		catch( URISyntaxException e ) {
			for( FailureSink sink : sinks ) {
				sink.log( "Failed to extract uri from '{}'", url, e );
			}
		}
	}

	/**
	 * Attempts to open a browser to view the supplied target
	 *
	 * @param uri   The target
	 * @param sinks What to do with failures
	 */
	@SafeVarargs
	public static void browse( URI uri, FailureSink... sinks ) {
		if( SUPPRESS.isTrue() ) {
			return;
		}

		try {
			if( Desktop.isDesktopSupported()
					&& Desktop.getDesktop().isSupported( Action.BROWSE ) ) {
				Desktop.getDesktop().browse( uri );
			}
			else if( XDG_OPEN_FALLBACK.isTrue() ) {
				// we might be on linux, where the browse action is poorly supported
				new ProcessBuilder( "xdg-open", uri.toString() ).start();
			}
		}
		catch( Exception e ) {
			for( FailureSink sink : sinks ) {
				sink.log( "Failed to browse `{}`", uri, e );
			}
		}
	}
}
