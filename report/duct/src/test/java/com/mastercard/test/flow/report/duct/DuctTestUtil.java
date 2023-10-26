package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.Meta;

/**
 * Utility for exercising {@link Duct}
 */
class DuctTestUtil {

	private static final String INDEX_TEMPLATE = Duct.resource( "no-node/index.html" );
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	/**
	 * Creates a mock report
	 *
	 * @param dir       where to create the rpeort
	 * @param model     syste model title
	 * @param test      test title
	 * @param timestamp report creation time
	 * @param flows     a sequence of comma-separated tag string, one for each flow
	 *                  in the report
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

			return dir;
		}
		catch( IOException e ) {
			throw new UncheckedIOException( "Failed to create report at " + dir, e );
		}
	}

	/**
	 * Polls for a successful /heartbeat
	 */
	static void waitForLife() {
		long expiry = System.currentTimeMillis() + 5000;

		Response<String> resp;
		do {
			if( System.currentTimeMillis() > expiry ) {
				throw new IllegalStateException( "duct startup failure" );
			}

			resp = heartbeat();
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
	 * Smashes /shutdown until it fails
	 */
	static void ensureDeath() {
		long expiry = System.currentTimeMillis() + 5000;

		Response<String> resp;
		do {
			if( System.currentTimeMillis() > expiry ) {
				throw new IllegalStateException( "duct shutdown failure" );
			}

			resp = shutdown();
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
	 * Issues a <code>/heartbeat</code> request
	 *
	 * @return the response
	 */
	static Response<String> heartbeat() {
		return request( "http://127.0.0.1:2276/heartbeat", "GET", null,
				b -> new String( b, UTF_8 ) );
	}

	/**
	 * Issues a <code>/shutdown</code> request
	 *
	 * @return the response
	 */
	static Response<String> shutdown() {
		return request( "http://127.0.0.1:2276/shutdown", "GET", null,
				b -> new String( b, UTF_8 ) );
	}

	/**
	 * Does a HTTP request
	 *
	 * @param method request method
	 * @param body   request body
	 * @return response body
	 */
	private static <T> Response<T> request( String url, String method, String body,
			Function<byte[], T> parse ) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL( url )
					.openConnection();
			connection.setRequestMethod( method );
			connection.setDoInput( true );
			connection.setConnectTimeout( 3000 );
			connection.setReadTimeout( 3000 );

			if( body != null ) {
				connection.setDoOutput( true );
				try( OutputStream out = connection.getOutputStream() ) {
					out.write( body.getBytes( UTF_8 ) );
				}
				catch( IOException ioe ) {
					throw new UncheckedIOException( ioe );
				}
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buff = new byte[1024];
			int read = 0;

			try( InputStream in = response( connection ) ) {
				while( (read = in.read( buff )) != -1 ) {
					baos.write( buff, 0, read );
				}
			}

			return new Response<>( connection.getResponseCode(), parse.apply( baos.toByteArray() ) );
		}
		catch( IOException e ) {
			return new Response<>( -1, null );
		}
	}

	public static class Response<T> {
		public int code;
		public final T body;

		Response( int code, T body ) {
			this.code = code;
			this.body = body;
		}

		@Override
		public String toString() {
			return String.format( "rc: %s\n%s", code, body );
		}
	}

	private static InputStream response( HttpURLConnection conn ) throws IOException {
		if( conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST ) {
			return conn.getInputStream();
		}
		return conn.getErrorStream();
	}
}
