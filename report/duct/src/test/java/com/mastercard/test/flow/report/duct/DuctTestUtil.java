package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.Meta;
import com.mastercard.test.flow.report.duct.HttpClient.Response;

/**
 * Utility for exercising {@link Duct}
 */
class DuctTestUtil {

	private static final String INDEX_TEMPLATE = resource( "no-node/index.html" );
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	/**
	 * Creates a mock report
	 *
	 * @param dir       where to create the rpeort
	 * @param model     system model title
	 * @param test      test title
	 * @param timestamp report creation time
	 * @param flows     a sequence of comma-separated tag string, one for each flow
	 *                  in the report
	 * @return The directory that was created
	 */
	public static Path createReport( Path dir, String model, String test, Instant timestamp,
			String... flows ) {
		try {
			Files.createDirectories( dir );
			Index idx = new Index(
					new Meta( model, test, timestamp.toEpochMilli() ),
					Stream.of( flows )
							.map( spec -> new Entry( "desc", Arrays.asList( spec.split( "," ) ), "detail" ) )
							.collect( toList() ) );
			String index = INDEX_TEMPLATE
					.replace( "// START_JSON_DATA\n\n// END_JSON_DATA",
							""
									+ "// START_JSON_DATA\n"
									+ JSON.writeValueAsString( idx )
									+ "\n// END_JSON_DATA" )
					.replace( "\r", "" );
			Files.write( dir.resolve( Writer.INDEX_FILE_NAME ), index.getBytes( UTF_8 ) );
			Files.createDirectories( dir.resolve( Writer.DETAIL_DIR_NAME ) );
			Files.createDirectories( dir.resolve( "res" ) );

			return dir;
		}
		catch( IOException e ) {
			throw new UncheckedIOException( "Failed to create report at " + dir, e );
		}
	}

	/**
	 * Polls for a <code>successful</code> /heartbeat
	 *
	 * @param port The poort where we expect duct to appear
	 */
	static void waitForLife( int port ) {
		long expiry = System.currentTimeMillis() + 5000;

		Response<String> resp;
		do {
			if( System.currentTimeMillis() > expiry ) {
				throw new IllegalStateException( "duct startup failure" );
			}

			resp = heartbeat( port );
			try {
				Thread.sleep( 100 );
			}
			catch( InterruptedException e ) {
				throw new IllegalStateException( e );
			}
		}
		while( resp.code != 200 );
	}

	/**
	 * Deletes the list of index directories from user prefs
	 */
	static void clearSavedIndex() {
		Duct.PREFS.remove( Duct.SERVED_REPORT_PATHS_PREF );
		try {
			Duct.PREFS.flush();
		}
		catch( BackingStoreException e ) {
			throw new IllegalStateException( e );
		}
	}

	/**
	 * Smashes <code>/shutdown</code> until it fails
	 */
	static void ensureDeath() {
		long expiry = System.currentTimeMillis() + 5000;

		Response<String> resp;
		do {
			if( System.currentTimeMillis() > expiry ) {
				throw new IllegalStateException( "duct shutdown failure" );
			}

			resp = shutdown( Duct.PORT );
			try {
				Thread.sleep( 100 );
			}
			catch( InterruptedException e ) {
				throw new IllegalStateException( e );
			}
		}
		while( resp.code == 200 );
	}

	/**
	 * Issues a <code>/heartbeat</code> request to 127.0.0.1
	 *
	 * @param port The port number to send to
	 * @return the response
	 */
	static Response<String> heartbeat( int port ) {
		return heartbeat( "127.0.0.1", port );
	}

	/**
	 * Issues a <code>/heartbeat</code> request
	 *
	 * @param ip   The IP address to request to
	 * @param port The port to address to
	 * @return the response
	 */
	static Response<String> heartbeat( String ip, int port ) {
		return HttpClient.request( "http://" + ip + ":" + port + "/heartbeat", "GET", null );
	}

	/**
	 * Gets the report index
	 *
	 * @param port The port number at which duct is running
	 * @return The report index
	 */
	static Response<String> index( int port ) {
		return HttpClient.request( "http://localhost:" + port + "/list", "GET", null );
	}

	/**
	 * Issues a <code>/shutdown</code> request
	 *
	 * @param port The port number at which duct is running
	 * @return the response
	 */
	static Response<String> shutdown( int port ) {
		return HttpClient.request( "http://localhost:" + port + "/shutdown", "POST", null );
	}

	/**
	 * Loads a classpath resource
	 *
	 * @param name The resource name
	 * @return resource content
	 */
	static String resource( String name ) {
		try( InputStream resource = Duct.class.getClassLoader()
				.getResourceAsStream( name ); ) {
			return read( resource );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	private static String read( InputStream is ) {
		try( ByteArrayOutputStream data = new ByteArrayOutputStream() ) {
			byte[] buff = new byte[1024];
			int read = 0;
			while( (read = is.read( buff )) >= 0 ) {
				data.write( buff, 0, read );
			}
			return new String( data.toByteArray(), UTF_8 );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	public static String copypasta( String... content ) {
		return copypasta( Stream.of( content ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	public static String copypasta( Stream<String> content ) {
		return content
				.map( s -> s.replaceAll( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replaceAll( "\"", "'" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}
}
