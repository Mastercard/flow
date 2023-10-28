package com.mastercard.test.flow.report;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
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

	/**
	 * A {@link Browse} implementation that uses AWT's desktop integration in the
	 * first instance
	 */
	public static final Browse WITH_AWT = new Browse(
			() -> Desktop.isDesktopSupported()
					&& Desktop.getDesktop().isSupported( Action.BROWSE ),
			uri -> Desktop.getDesktop().browse( uri ),
			cmd -> new ProcessBuilder( cmd ).start() );

	private final Support support;
	private final Trigger trigger;
	private final Launch launch;

	/**
	 * @param support is browsing supported
	 * @param trigger launch a browser
	 * @param launch  run a commandline
	 */
	Browse( Support support, Trigger trigger, Launch launch ) {
		this.support = support;
		this.trigger = trigger;
		this.launch = launch;
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
			for( FailureSink sink : sinks ) {
				sink.log( "Browser launch suppressed by system property {}={}",
						SUPPRESS.property(), SUPPRESS.value() );
			}
			return;
		}

		if( support.supported() ) {
			try {
				trigger.browse( uri );
			}
			catch( Exception e ) {
				for( FailureSink sink : sinks ) {
					sink.log( "Failed to browse `{}`", uri, e );
				}
			}
		}
		else if( XDG_OPEN_FALLBACK.isTrue() ) {
			try {
				// we might be on linux, where the browse action is poorly supported
				launch.launch( "xdg-open", uri.toString() );
			}
			catch( Exception e ) {
				for( FailureSink sink : sinks ) {
					sink.log( "Failed to launch `xdg-open {}`", uri, e );
				}
			}
		}
	}

	/**
	 * Interface for checking if launching a browser is supported
	 */
	interface Support {
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
	interface Trigger {
		/**
		 * Launches a browser to view the supplied URI
		 *
		 * @param uri The browse target
		 * @throws IOException on failure
		 */
		void browse( URI uri ) throws IOException;
	}

	/**
	 * Interface for executing a command
	 */
	interface Launch {
		/**
		 * Runs the command in a new process
		 *
		 * @param cmd the commandline
		 * @throws IOException on failure
		 */
		void launch( String... cmd ) throws IOException;
	}
}
