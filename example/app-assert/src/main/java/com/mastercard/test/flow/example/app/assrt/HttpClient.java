package com.mastercard.test.flow.example.app.assrt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map.Entry;

import com.mastercard.test.flow.msg.http.HttpReq;

/**
 * Utility methods for exercising HTTP endpoints.
 */
public class HttpClient {

	private HttpClient() {
		// no instances
	}

	/**
	 * Sends this request to a host
	 *
	 * @param protocol URL protocol
	 * @param host     URL host
	 * @param port     URL port
	 * @param request  The request data
	 * @return The bytes of the response
	 * @throws UncheckedIOException if something goes wrong
	 */
	public static byte[] send( String protocol, String host, int port, HttpReq request ) {
		try {
			return send( new URL( protocol, host, port, "" ), request );
		}
		catch( MalformedURLException mue ) {
			throw new UncheckedIOException( mue );
		}
	}

	/**
	 * Sends a HTTP request to a host
	 *
	 * @param url     The host URL. The path element on this URL (if any) will be
	 *                ignored in favour of the {@link HttpReq#path()} of the message
	 * @param request The request data
	 * @return The bytes of the response
	 * @throws UncheckedIOException if something goes wrong
	 */
	public static byte[] send( URL url, HttpReq request ) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL( url, request.path() )
					.openConnection();
			connection.setRequestMethod( request.method() );
			request.headers().forEach( connection::setRequestProperty );
			connection.setDoInput( true );
			connection.setConnectTimeout( 3000 );
			connection.setReadTimeout( 3000 );

			request.body().ifPresent( body -> {
				connection.setDoOutput( true );
				try( OutputStream out = connection.getOutputStream() ) {
					out.write( body.content() );
				}
				catch( IOException ioe ) {
					throw new UncheckedIOException( ioe );
				}
			} );

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buff = new byte[1024];
			int read = 0;

			try( InputStream in = response( connection ) ) {
				while( (read = in.read( buff )) != -1 ) {
					baos.write( buff, 0, read );
				}
			}

			// The java http api doesn't allow access to the raw bytes of the response, so
			// we have to reform them based on what the api parsed
			boolean chunked = "chunked".equals( connection.getHeaderField( "Transfer-Encoding" ) );
			String resp = String.format( ""
					+ "%s\r\n" // response line
					+ "%s\r\n" // headers
					+ "%s" // optional chunk length
					+ "%s" // body
					+ "%s", // optional end chunk,
					connection.getHeaderField( null ), // response line, apparently
					connection.getHeaderFields().entrySet().stream()
							.filter( e -> e.getKey() != null )
							.sorted( comparing( Entry::getKey ) )
							.map( e -> String.format( "%s: %s\r\n",
									e.getKey(),
									e.getValue().stream()
											.sorted()
											.collect( joining( "," ) ) ) )
							.collect( joining() ),
					chunked
							? Integer.toHexString( baos.toByteArray().length ) + "\r\n"
							: "",
					new String( baos.toByteArray(), UTF_8 ),
					chunked
							? "\r\n0\r\n\r\n"
							: "" );

			return resp.getBytes( UTF_8 );
		}
		catch( Exception ioe ) {
			throw new UncheckedIOException( (IOException) ioe );
		}
	}

	private static InputStream response( HttpURLConnection conn ) throws IOException {
		if( conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST ) {
			return conn.getInputStream();
		}
		return conn.getErrorStream();
	}
}
