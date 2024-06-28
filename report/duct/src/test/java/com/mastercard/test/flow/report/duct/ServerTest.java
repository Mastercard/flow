package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Exercises {@link Server}
 */
@SuppressWarnings("static-method")
class ServerTest {

	/**
	 * Demonstrates startup and shutdown
	 */
	@Test
	void life() {

		Server server = new Server( Mockito.mock( Duct.class ), 0 );
		String url = "";
		try {
			server.start();
			url = "http://localhost:" + server.port() + "/list";

			assertEquals( "rc: 200\n"
					+ "[ ]",
					HttpClient.request( url, "GET", null ).toString(),
					"Server is alive!" );
		}
		finally {
			server.awaitStop();
		}

		assertEquals( -1,
				HttpClient.request( url, "GET", null ).code,
				"server is dead!" );
	}

	/**
	 * Exercises the static endpoints
	 */
	@Test
	void endpoints() {
		Duct duct = Mockito.mock( Duct.class );
		when( duct.heartbeat() )
				.thenReturn( Instant.EPOCH );
		when( duct.add( any( Path.class ) ) )
				.thenAnswer( inv -> "added " + inv.getArgument( 0 ) );
		when( duct.index() )
				.thenReturn( Collections.emptyList() );

		Server server = new Server( duct, 0 );
		String url = "";
		try {
			server.start();

			url = "http://localhost:" + server.port();

			assertEquals( "rc: 200\n"
					+ "Expiry at 1970-01-01T00:00:00Z",
					HttpClient.request( url + "/heartbeat", "GET", null ).toString(),
					url + "/heartbeat" );
			verify( duct ).heartbeat();

			assertEquals( "rc: 200\n"
					+ "Shutting down",
					HttpClient.request( url + "/shutdown", "POST", null ).toString(),
					url + "/shutdown" );
			verify( duct ).stop();

			assertEquals( "rc: 200\n"
					+ "added path_to_report",
					HttpClient.request( url + "/add", "POST", "path_to_report" ).toString(),
					url + "/add" );
			verify( duct ).add( any( Path.class ) );

			assertEquals( "rc: 200\n"
					+ "[ ]",
					HttpClient.request( url + "/list", "GET", null ).toString(),
					url + "/list" );
			verify( duct ).index();

			verifyNoMoreInteractions( duct );
		}
		finally {
			server.awaitStop();
		}

		assertEquals( -1,
				HttpClient.request( url + "/heartbeat", "GET", null ).code,
				url + "/heartbeat" );
	}

	/**
	 * Shows that the routing will serve flow details pages that didn't exist at the
	 * time the report was indexed. This is important as the report will be added to
	 * duct before all flows have been processed.
	 */
	@Test
	void dynamicDetailMap() {
		Server server = new Server( null, 0 );
		try {
			Path root = Paths.get( "target/ServerTest/dynamicDetailMap" );
			Path detail = root.resolve( "detail" );
			QuietFiles.recursiveDelete( root );
			QuietFiles.createDirectories( detail );

			// create a sketch of a report structure
			QuietFiles.write( root.resolve( "index.html" ), "index".getBytes( UTF_8 ) );
			QuietFiles.write( detail.resolve( "00000000000000000000000000000001.html" ),
					"before".getBytes( UTF_8 ) );

			server.start();

			server.map( "/dynamicDetailMap/", root );

			BiConsumer<String, String> test = ( path, response ) -> {
				String url = "http://localhost:" + server.port() + path;
				assertEquals( response, HttpClient.request( url, "GET", null ).toString(), "for " + url );
			};

			// the files that exist at the time of mapping are returned as expected
			test.accept( "/dynamicDetailMap/index.html", ""
					+ "rc: 200\n"
					+ "index" );
			test.accept( "/dynamicDetailMap/detail/00000000000000000000000000000001.html", ""
					+ "rc: 200\n"
					+ "before" );
			// and we even mapped the index file to the directory path
			test.accept( "/dynamicDetailMap/", ""
					+ "rc: 200\n"
					+ "index" );

			// add another detail file

			QuietFiles.write( detail.resolve( "00000000000000000000000000000002.html" ),
					"after".getBytes( UTF_8 ) );

			// it gets mapped without us having to tell the server about it
			test.accept( "/dynamicDetailMap/detail/00000000000000000000000000000002.html", ""
					+ "rc: 200\n"
					+ "after" );

			// only files that are named like flow detail pages are served
			QuietFiles.write( detail.resolve( "badname.html" ),
					"should not be served".getBytes( UTF_8 ) );
			test.accept( "/dynamicDetailMap/detail/badname.html", ""
					+ "rc: 404\n" );
		}
		finally {
			server.awaitStop();
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
			server.awaitStop();
		}
	}

	/**
	 * Shows that mapped reports can be remapped and unmapped
	 */
	@Test
	void reunmap() {

		Path reportDir = Paths.get( "target/ServerTest/reunmap" );
		QuietFiles.recursiveDelete( reportDir );
		DuctTestUtil.createReport( reportDir, "model", "test", Instant.now() );
		QuietFiles.write( reportDir.resolve( "res" ).resolve( "script.js" ),
				"script".getBytes( UTF_8 ) );

		Server server = new Server( null, 0 );
		try {
			server.start();
			server.map( "initial/", reportDir );
			String url = "http://localhost:" + server.port() + "/";

			BiConsumer<String, Integer> test = ( path, rc ) -> assertEquals(
					rc,
					HttpClient.request( url + path, "GET", null ).code,
					"for " + path );

			// file is served as expected
			test.accept( "initial/res/script.js", 200 );

			// add a new js file
			QuietFiles.write( reportDir.resolve( "res" ).resolve( "added.js" ),
					"added".getBytes( UTF_8 ) );

			// new file is not served
			test.accept( "initial/res/added.js", 404 );

			// map the dir again
			server.map( "remap/", reportDir );

			// the old mapping has gone!
			test.accept( "initial/res/script.js", 404 );

			// but both now exist under the new path
			test.accept( "remap/res/script.js", 200 );
			test.accept( "remap/res/added.js", 200 );

			server.unmap( reportDir );

			// mappings removed
			test.accept( "remap/res/script.js", 404 );
			test.accept( "remap/res/added.js", 404 );
		}
		finally {
			server.awaitStop();
		}
	}

	/**
	 * Demonstrates that only .ico, .js and .css files are served from the
	 * <code>res</code> directory
	 */
	@Test
	void resourceTypes() {
		Path reportDir = Paths.get( "target/ServerTest/resourceTypes" );
		QuietFiles.recursiveDelete( reportDir );
		DuctTestUtil.createReport( reportDir, "model", "test", Instant.now() );
		QuietFiles.write( reportDir.resolve( "res" ).resolve( "favicon.ico" ),
				"icon".getBytes( UTF_8 ) );
		QuietFiles.write( reportDir.resolve( "res" ).resolve( "style.css" ),
				"style".getBytes( UTF_8 ) );
		QuietFiles.write( reportDir.resolve( "res" ).resolve( "script.js" ),
				"script".getBytes( UTF_8 ) );
		QuietFiles.write( reportDir.resolve( "res" ).resolve( "text.txt" ),
				"text".getBytes( UTF_8 ) );
		QuietFiles.createDirectories( reportDir.resolve( "res" ).resolve( "dir.css" ) );

		Server server = new Server( null, 0 );
		try {
			server.start();
			server.map( "resourceTypes/", reportDir );
			String url = "http://localhost:" + server.port() + "/resourceTypes/";

			assertEquals( "rc: 200\n"
					+ "icon",
					HttpClient.request( url + "/res/favicon.ico", "GET", null ).toString() );

			assertEquals( "rc: 200\n"
					+ "style",
					HttpClient.request( url + "/res/style.css", "GET", null ).toString() );

			assertEquals( "rc: 200\n"
					+ "script",
					HttpClient.request( url + "/res/script.js", "GET", null ).toString() );

			// text files are not mapped
			assertEquals( "rc: 404\n"
					+ "<html><body><h2>404 Not found</h2></body></html>",
					HttpClient.request( url + "/res/text.txt", "GET", null ).toString() );

			// directories are not mapped
			assertEquals( "rc: 404\n"
					+ "<html><body><h2>404 Not found</h2></body></html>",
					HttpClient.request( url + "/res/dir.css", "GET", null ).toString() );
		}
		finally {
			server.awaitStop();
		}
	}
}
