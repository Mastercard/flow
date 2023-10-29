package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Function;

/**
 * A very simple HTTP client
 */
class HttpClient {

	private HttpClient() {
		// no instances
	}

	/**
	 * Does a HTTP request
	 *
	 * @param url    request URL
	 * @param method request method
	 * @param body   request body
	 * @return The response body string
	 */
	static Response<String> request( String url, String method, String body ) {
		return request( url, method, body, b -> new String( b, UTF_8 ) );
	}

	/**
	 * Does a HTTP request
	 *
	 * @param <T>    response body type
	 * @param url    request URL
	 * @param method request method
	 * @param body   request body
	 * @param parse  How to parse the response body
	 * @return The parsed response, which will have response code -1 in the event of
	 *         failure
	 */
	static <T> Response<T> request( String url, String method, String body,
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
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buff = new byte[1024];
			int read = 0;

			try( InputStream in = response( connection ) ) {
				while( (read = in.read( buff )) != -1 ) {
					baos.write( buff, 0, read );
				}
			}

			return new Response<>( connection.getResponseCode(), baos.toByteArray(), parse );
		}
		catch( IOException e ) {
			return new Response<>( -1, e.getMessage().getBytes( UTF_8 ), parse );
		}
	}

	/**
	 * Response details
	 *
	 * @param <T> parsed body type
	 */
	static class Response<T> {
		/**
		 * Response code
		 */
		int code;
		/**
		 * body text
		 */
		final String raw;
		/**
		 * parsed body
		 */
		final T body;

		/**
		 * @param code  Response code
		 * @param data  body bytes
		 * @param parse how to parse the body
		 */
		Response( int code, byte[] data, Function<byte[], T> parse ) {
			this.code = code;
			String r = new String( data, UTF_8 );
			T parsed = null;
			try {
				parsed = parse.apply( data );
			}
			catch( Exception e ) {
				r += "\nParse failure : " + e.getMessage();
			}
			body = parsed;
			raw = r;
		}

		@Override
		public String toString() {
			return "rc: " + code + "\n" + raw;
		}
	}

	private static InputStream response( HttpURLConnection conn ) throws IOException {
		if( conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST ) {
			return conn.getInputStream();
		}
		return conn.getErrorStream();
	}
}
