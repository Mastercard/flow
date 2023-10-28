package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Exercises {@link Server}
 */
@SuppressWarnings("static-method")
class ServerTest {

	/**
	 * Shows that the routing will serve flow details pages that didn't exist at the
	 * time the report was indexed. This is important as the report will be added to
	 * duct before all flows have been processed.
	 */
	@Test
	void dynamicDetailMap() {
		Server server = new Server( null, 0 );
		try {
			Path root = Paths.get( "target/ServerTest/dynamicMap" );
			Path detail = root.resolve( "detail" );
			QuietFiles.recursiveDelete( root );
			QuietFiles.createDirectories( detail );

			// create a sketch of a report structure
			QuietFiles.write( root.resolve( "index.html" ), "index".getBytes( UTF_8 ) );
			QuietFiles.write( detail.resolve( "00000000000000000000000000000001.html" ),
					"before".getBytes( UTF_8 ) );

			server.start();

			server.map( "/dynamicMap/", root );

			BiConsumer<String, String> test = ( path, response ) -> {
				String url = "http://localhost:" + server.port() + path;
				assertEquals( response, HttpClient.request( url, "GET", null ).toString(), "for " + url );
			};

			// the files that exist at the time of mapping are returned as expected
			test.accept( "/dynamicMap/index.html", ""
					+ "rc: 200\n"
					+ "index" );
			test.accept( "/dynamicMap/detail/00000000000000000000000000000001.html", ""
					+ "rc: 200\n"
					+ "before" );
			// and we even mapped the index file to the directory path
			test.accept( "/dynamicMap/", ""
					+ "rc: 200\n"
					+ "index" );

			// add another detail file

			QuietFiles.write( detail.resolve( "00000000000000000000000000000002.html" ),
					"after".getBytes( UTF_8 ) );

			// it gets mapped without us having to tell the server about it
			test.accept( "/dynamicMap/detail/00000000000000000000000000000002.html", ""
					+ "rc: 200\n"
					+ "after" );

			// only files that are named like flow detail pages are served
			QuietFiles.write( detail.resolve( "badname.html" ),
					"should not be served".getBytes( UTF_8 ) );
			test.accept( "/dynamicMap/detail/badname.html", ""
					+ "rc: 404\n" );
		}
		finally {
			server.stop();
		}
	}

	/**
	 * Shows that requests to non-loopback addresses are 403-rejected
	 *
	 * @throws UnknownHostException if we fail to find our own address
	 */
	@Test
	void loopback() throws UnknownHostException {
		InetAddress localhost = InetAddress.getLocalHost();
		List<String> nonLoop = Stream.of( InetAddress
				.getAllByName( localhost.getCanonicalHostName() ) )
				.filter( a -> !a.isLoopbackAddress() )
				.map( addr -> {
					if( addr instanceof Inet6Address ) {
						// in URLs, ipv6 addresses need to be in brackets
						return "[" + addr.getHostAddress() + "]";
					}
					return addr.getHostAddress();
				} )
				.collect( toList() );

		assertTrue( nonLoop.size() > 0, "Expected at least 1 non-loopback address" );

		Path root = Paths.get( "target/ServerTest/loopback" );
		QuietFiles.recursiveDelete( root );
		QuietFiles.createDirectories( root );
		QuietFiles.write( root.resolve( "index.html" ), "index".getBytes( UTF_8 ) );

		Server server = new Server( null, 0 );
		try {
			server.start();

			server.map( "loopback/", root );

			BiConsumer<String, String> test = ( addr, response ) -> {
				String url = "http://" + addr + ":" + server.port() + "/loopback/";
				assertEquals(
						response,
						HttpClient.request( url, "GET", null ).toString(),
						"for " + url );
			};

			// requests on the loopback address are served normally
			Stream.of( "127.0.0.1", "localhost", "[::1]" )
					.forEach( addr -> test.accept(
							addr, "rc: 200\nindex" ) );

			// non-loopback address are rejected
			nonLoop.stream()
					.forEach( addr -> test.accept(
							addr, "rc: 403\n" ) );
		}
		finally {
			server.stop();
		}
	}
}
