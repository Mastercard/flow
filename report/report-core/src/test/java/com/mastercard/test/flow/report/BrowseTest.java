package com.mastercard.test.flow.report;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Exercises {@link Browse}
 */
@SuppressWarnings("static-method")
class BrowseTest {

	private static final String STRING_DOT_COM = "http://string.com";
	private static final String BAD_STRING = "_ ";
	private static final URI URI_DOT_COM;
	private static final URL URL_DOT_COM;
	private static final URL BAD_URL;
	static {
		try {
			// with thanks to https://stackoverflow.com/a/62660558
			URI_DOT_COM = new URI( "http://uri.com" );
			URL_DOT_COM = new URL( "http://url.com" );
			BAD_URL = new URL( "http://   _" );
		}
		catch( Exception e ) {
			throw new IllegalStateException( e );
		}
	}

	/**
	 * Browsing is supported and error-free
	 */
	@Test
	void supportedNoErrors() {
		BrowseHarness bh = new BrowseHarness();
		bh.expect( b -> b.to( STRING_DOT_COM, bh.sink ),
				"Browsing to http://string.com" );
		bh.expect( b -> b.to( URI_DOT_COM, bh.sink ),
				"Browsing to http://uri.com" );
		bh.expect( b -> b.to( URL_DOT_COM, bh.sink ),
				"Browsing to http://url.com" );

		bh.expect( b -> b.to( BAD_STRING, bh.sink ),
				"Failure! Failed to parse uri from '{}'",
				"    _ ",
				"    java.net.URISyntaxException: Illegal character in path at index 1: _ " );
		bh.expect( b -> b.to( BAD_URL, bh.sink ),
				"Failure! Failed to extract uri from '{}'",
				"    http://   _",
				"    java.net.URISyntaxException: Illegal character in authority at index 7: http://   _" );

		try( Temporary t = Browse.SUPPRESS.temporarily( "true" ) ) {
			bh.expect( b -> b.to( STRING_DOT_COM, bh.sink ),
					"Failure! Browser launch suppressed by system property {}={}",
					"    mctf.suppress.browse",
					"    true" );
		}
	}

	/**
	 * Browsing is supported, but it doesn't work very well
	 */
	@Test
	void supportedError() {
		BrowseHarness bh = new BrowseHarness()
				.browseFailing( true );

		bh.expect( b -> b.to( URI_DOT_COM, bh.sink ),
				"Failure! Failed to browse `{}`",
				"    http://uri.com",
				"    java.io.IOException: browse fail!" );
	}

	/**
	 * Browsing is not supported, and our fallback works ok
	 */
	@Test
	void unSupportedNoErrors() {
		BrowseHarness bh = new BrowseHarness()
				.supported( false );

		// no support? oh well!
		bh.expect( b -> b.to( URI_DOT_COM, bh.sink ),
				"" );

		// The xdg-open fallback must be explicitly enabled
		try( Temporary t = Browse.XDG_OPEN_FALLBACK.temporarily( "true" ) ) {
			bh.expect( b -> b.to( URI_DOT_COM, bh.sink ),
					"launching [xdg-open, http://uri.com]" );
		}
	}

	/**
	 * Browsing is not supported, and our fallback fails!
	 */
	@Test
	void unSupportedErrors() {

		BrowseHarness bh = new BrowseHarness()
				.supported( false )
				.launchFailing( true );

		try( Temporary t = Browse.XDG_OPEN_FALLBACK.temporarily( "true" ) ) {
			bh.expect( b -> b.to( URI_DOT_COM, bh.sink ),
					"Failure! Failed to launch `xdg-open {}`",
					"    http://uri.com",
					"    java.io.IOException: launch fail" );
		}
	}

	private static class BrowseHarness {
		private final List<String> log = new ArrayList<>();

		private boolean supported = true;
		private boolean browseFail = false;
		private boolean launchFail = false;

		public final FailureSink sink = ( msg, params ) -> log
				.add( String.format( "Failure! %s\n    %s",
						msg, Stream.of( params ).map( String::valueOf ).collect( joining( "\n    " ) ) ) );

		private final Browse browse = new Browse(
				() -> supported,
				uri -> {
					if( browseFail ) {
						throw new IOException( "browse fail!" );
					}
					log.add( "Browsing to " + uri );
				},
				cmd -> {
					if( launchFail ) {
						throw new IOException( "launch fail" );
					}
					log.add( "launching " + Arrays.toString( cmd ) );
				} );

		BrowseHarness() {
		}

		BrowseHarness supported( boolean b ) {
			supported = b;
			return this;
		}

		BrowseHarness browseFailing( boolean b ) {
			browseFail = b;
			return this;
		}

		BrowseHarness launchFailing( boolean b ) {
			launchFail = b;
			return this;
		}

		/**
		 * Invokes an action on the browser, then asserts on the event log
		 *
		 * @param action The action
		 * @param events The expected events
		 */
		void expect( Consumer<Browse> action, String... events ) {
			action.accept( browse );
			assertEquals(
					Copy.pasta( events ),
					Copy.pasta( log ) );
			log.clear();
		}
	}
}
