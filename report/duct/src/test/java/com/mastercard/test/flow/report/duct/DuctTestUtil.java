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
	public static void createReport( Path dir, String model, String test, Instant timestamp,
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
		}
		catch( IOException e ) {
			throw new UncheckedIOException( "Failed to create report at " + dir, e );
		}
	}

	/**
	 * Does a HTTP request
	 *
	 * @param method request method
	 * @param body   request body
	 * @return response body
	 */
	private static String request( String url, String method, String body ) {
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

			return new String( baos.toByteArray(), UTF_8 );
		}
		catch( IOException e ) {
			throw new UncheckedIOException( e );
		}
	}

	private static InputStream response( HttpURLConnection conn ) throws IOException {
		if( conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST ) {
			return conn.getInputStream();
		}
		return conn.getErrorStream();
	}
}
