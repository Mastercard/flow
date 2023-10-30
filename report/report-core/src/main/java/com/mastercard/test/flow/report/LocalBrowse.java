package com.mastercard.test.flow.report;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.mastercard.test.flow.util.Option;

/**
 * Utility for opening a browser on some local target, either a
 * <code>file://</code> or a <code>http://localhost</code> url
 */
public class LocalBrowse {

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

	/**
	 * A {@link LocalBrowse} implementation that uses AWT's desktop integration in
	 * the first instance, but falls back to <code>xdg-open</code> when AWT's browse
	 * action is not supported
	 */
	public static final LocalBrowse WITH_AWT = new LocalBrowse(
			// This works on windows, and I'd hope that it's OK on mac too
			() -> Desktop.isDesktopSupported()
					&& Desktop.getDesktop().isSupported( Action.BROWSE ),
			uri -> Desktop.getDesktop().browse( uri ),
			// the browse action seems to be poorly supported linux, but
			// xdg-open will probably work
			uri -> new ProcessBuilder( "xdg-open",
					// we're issuing commandlines, so belt-and-braces
					localCommandlineURI( uri ) ).start() );

	/**
	 * Mitigates command injection risk by stripping anything we don't use out of a
	 * URI. We only retain the scheme, which must be <code>file</code> or
	 * <code>http</code>, and the path, which cannot contain anything other that
	 * <code>[0-9a-zA-Z_/]</code>.
	 *
	 * @param uri A URI
	 * @return a file or localhost uri with the same path, but with anything outside
	 *         of <code>[0-9a-zA-Z_/]</code> stripped out
	 * @throws IllegalArgumentException if the scheme is not <code>file</code> or
	 *                                  <code>http</code>
	 */
	static String localCommandlineURI( URI uri ) {
		if( "file".equals( uri.getScheme() ) ) {
			return uri.getScheme() + ":" + uri.getPath()
					.replaceAll( "[^0-9a-zA-Z_/]", "" );
		}
		if( "http".equals( uri.getScheme() ) ) {
			return uri.getScheme() + "://localhost" + uri.getPath()
					.replaceAll( "[^0-9a-zA-Z_/]", "" );
		}
		throw new IllegalArgumentException( "Unsupported scheme on " + uri );
	}

	private final Support support;
	private final Trigger primary;
	private final Trigger fallback;

	/**
	 * @param support  is browsing supported
	 * @param primary  launch a browser via the supported means
	 * @param fallback launch a browser via some other means
	 */
	public LocalBrowse( Support support, Trigger primary, Trigger fallback ) {
		this.support = support;
		this.primary = primary;
		this.fallback = fallback;
	}

	/**
	 * Attempts to open a browser to view the supplied target
	 *
	 * @param uri   The target
	 * @param sinks What to do with failures
	 */
	@SafeVarargs
	public final void to( String uri, FailureSink... sinks ) {
		try {
			to( new URI( uri ), sinks );
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
	public final void to( URL url, FailureSink... sinks ) {
		try {
			to( url.toURI(), sinks );
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
	public final void to( URI uri, FailureSink... sinks ) {
		if( SUPPRESS.isTrue() ) {
			logTo( sinks, "Browser launch suppressed by system property {}={}",
					SUPPRESS.property(), SUPPRESS.value() );
			return;
		}

		if( !supported( uri ) ) {
			logTo( sinks, "Declining nonlocal uri `{}`", uri );
			return;
		}

		if( support.supported() ) {
			try {
				primary.browse( uri );
			}
			catch( Exception e ) {
				logTo( sinks, "Failed to browse `{}`", uri, e );
			}
		}
		else {
			try {
				fallback.browse( uri );
			}
			catch( Exception e ) {
				logTo( sinks, "Failed to {} via fallback route", uri, e );
			}
		}
	}

	private static void logTo( FailureSink[] sinks, String msg, Object... params ) {
		for( FailureSink sink : sinks ) {
			sink.log( msg, params );
		}
	}

	private static boolean supported( URI uri ) {
		return "file".equals( uri.getScheme() )
				|| "http".equals( uri.getScheme() ) && "localhost".equals( uri.getHost() );
	}

	/**
	 * Interface for checking if launching a browser is supported
	 */
	public interface Support {
		/**
		 * Checks browsing support
		 *
		 * @return <code>true</code> if we can expect to launch a browser
		 */
		boolean supported();
	}

	/**
	 * Interface for provoking a browser
	 */
	public interface Trigger {
		/**
		 * Launches a browser to view the supplied URI
		 *
		 * @param uri The browse target
		 * @throws IOException on failure
		 */
		void browse( URI uri ) throws IOException;
	}
}
