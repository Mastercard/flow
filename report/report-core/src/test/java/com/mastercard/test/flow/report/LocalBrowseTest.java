package com.mastercard.test.flow.report;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Exercises {@link LocalBrowse}
 */
@SuppressWarnings("static-method")
class LocalBrowseTest {

	private static final String FILE_STRING = "file://path/to/file";
	private static final String VALID_STRING = "http://localhost/string";
	private static final String NON_LOCAL_STRING = "http://string.com";
	private static final String BAD_STRING = "_ ";
	private static final URI VALID_URI;
	private static final URL VALID_URL;
	private static final URL BAD_URL;
	static {
		try {
			// with thanks to https://stackoverflow.com/a/62660558
			VALID_URI = new URI( "http://localhost/uri" );
			VALID_URL = new URL( "http://localhost/url" );
			BAD_URL = new URL( "http://   _" ); // it's a valid URL, but it's not a URI
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
		bh.expect( b -> b.to( FILE_STRING, bh.sink ),
				"Browsing to file://path/to/file" );
		bh.expect( b -> b.to( VALID_STRING, bh.sink ),
				"Browsing to http://localhost/string" );
		bh.expect( b -> b.to( VALID_URI, bh.sink ),
				"Browsing to http://localhost/uri" );
		bh.expect( b -> b.to( VALID_URL, bh.sink ),
				"Browsing to http://localhost/url" );

		bh.expect( b -> b.to( BAD_STRING, bh.sink ),
				"Failure! Failed to parse uri from '{}'",
				"    _ ",
				"    java.net.URISyntaxException: Illegal character in path at index 1: _ " );
		bh.expect( b -> b.to( BAD_URL, bh.sink ),
				"Failure! Failed to extract uri from '{}'",
				"    http://   _",
				"    java.net.URISyntaxException: Illegal character in authority at index 7: http://   _" );
		bh.expect( b -> b.to( NON_LOCAL_STRING, bh.sink ),
				"Failure! Declining nonlocal uri `{}`",
				"    http://string.com" );

		try( Temporary t = LocalBrowse.SUPPRESS.temporarily( "true" ) ) {
			bh.expect( b -> b.to( VALID_STRING, bh.sink ),
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

		bh.expect( b -> b.to( VALID_URI, bh.sink ),
				"Failure! Failed to browse `{}`",
				"    http://localhost/uri",
				"    java.io.IOException: browse fail!" );
	}

	/**
	 * Browsing is not supported, and our fallback works ok
	 */
	@Test
	void unSupportedNoErrors() {
		BrowseHarness bh = new BrowseHarness()
				.supported( false );

		// The xdg-open fallback must be explicitly enabled
		try( Temporary t = LocalBrowse.XDG_OPEN_FALLBACK.temporarily( "true" ) ) {
			bh.expect( b -> b.to( VALID_URI, bh.sink ),
					"Fallback browsing to http://localhost/uri" );
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

		try( Temporary t = LocalBrowse.XDG_OPEN_FALLBACK.temporarily( "true" ) ) {
			bh.expect( b -> b.to( VALID_URI, bh.sink ),
					"Failure! Failed to {} via fallback route",
					"    http://localhost/uri",
					"    java.io.IOException: fallback browse fail" );
		}
	}

	/**
	 * Exercises {@link LocalBrowse#localCommandlineURI(URI)}
	 */
	@Test
	void localCommandlineURI() {
		BiConsumer<String, String> test = ( in, out ) -> {
			try {
				assertEquals( out,
						LocalBrowse.localCommandlineURI( new URI( in ) ),
						"for " + in );
			}
			catch( URISyntaxException e ) {
				throw new IllegalArgumentException( "bad uri " + in, e );
			}
			catch( Exception e ) {
				e.printStackTrace();
				assertEquals( out, e.getMessage(), "for bad input " + in );
			}
		};

		test.accept( "file:/path/to/file", "file:/path/to/file" );
		test.accept( "file:/path/to/file;rm%20-rf%20*", "file:/path/to/filermrf" );
		test.accept( "file:/ᓄᓇᕗᒻᒥᐅᑦ/বাংলা/ኤርትራ", "file:///" );

		test.accept( "http://localhost/path/to/file", "http://localhost/path/to/file" );
		test.accept( "http://localhost/path/to/file?query=foo", "http://localhost/path/to/file" );
		test.accept( "http://localhost/path/to/file#fragment", "http://localhost/path/to/file" );
		test.accept( "http://localhost:1234/path/to/file", "http://localhost:1234/path/to/file" );
		test.accept( "http://localhost:1234/path/to/file?query=foo#fragment",
				"http://localhost:1234/path/to/file" );

		test.accept( "http://remote.com/path/to/file", "http://localhost/path/to/file" );

		test.accept( "http://remote.com/path/to/file;rm%20-rf%20*",
				"http://localhost/path/to/filermrf" );

		test.accept( "https://localhost/path/to/file",
				"Unsupported scheme on https://localhost/path/to/file" );
	}

	private static class BrowseHarness {
		private final List<String> log = new ArrayList<>();

		private boolean supported = true;
		private boolean browseFail = false;
		private boolean launchFail = false;

		public final FailureSink sink = ( msg, params ) -> log
				.add( String.format( "Failure! %s\n    %s",
						msg, Stream.of( params ).map( String::valueOf ).collect( joining( "\n    " ) ) ) );

		private final LocalBrowse browse = new LocalBrowse(
				() -> supported,
				uri -> {
					if( browseFail ) {
						throw new IOException( "browse fail!" );
					}
					log.add( "Browsing to " + uri );
				},
				uri -> {
					if( launchFail ) {
						throw new IOException( "fallback browse fail" );
					}
					log.add( "Fallback browsing to " + uri );
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
		void expect( Consumer<LocalBrowse> action, String... events ) {
			action.accept( browse );
			assertEquals(
					Copy.pasta( events ),
					Copy.pasta( log ) );
			log.clear();
		}
	}
}
