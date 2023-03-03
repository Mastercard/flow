package com.mastercard.test.flow.msg.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.util.Bytes;

/**
 * An HTTP response message, which probably encapsulates another {@link Message}
 * as the response body
 */
public class HttpRes extends HttpMsg<HttpRes> {

	/**
	 * Use this as the field path to set the HTTP status code
	 */
	public static final String STATUS = "HTTP_STATUS";
	/**
	 * Use this as the field path to set the HTTP status code text
	 */
	public static final String STATUS_TEXT = "HTTP_STATUS_TEXT";

	/**
	 * An empty response
	 */
	public HttpRes() {
	}

	/**
	 * @param content   response bytes
	 * @param bodyParse How to parse body bytes as a message
	 */
	public HttpRes( byte[] content, Function<byte[], Message> bodyParse ) {
		this();

		Deque<String> lines = new ArrayDeque<>(
				Arrays.asList( new String( content, UTF_8 ).split( "\r\n" ) ) );

		// status line
		Deque<String> statusFields = new ArrayDeque<>(
				Arrays.asList( lines.removeFirst().split( " " ) ) );

		if( !statusFields.isEmpty() ) {
			set( VERSION, statusFields.removeFirst() );
		}
		if( !statusFields.isEmpty() ) {
			set( STATUS, statusFields.removeFirst() );
		}
		if( !statusFields.isEmpty() ) {
			set( STATUS_TEXT, statusFields.stream().collect( joining( " " ) ) );
		}

		// zero or more headers
		String headerLine;
		while( !lines.isEmpty() && !(headerLine = lines.removeFirst()).isEmpty() ) {
			Matcher h = HEADER_LINE_PATTERN.matcher( headerLine );
			if( h.matches() ) {
				set( header( h.group( "name" ).trim() ),
						h.group( "value" ).trim() );
			}
		}

		// body
		String body = lines.stream().collect( joining( "\r\n" ) );
		set( BODY, bodyParse.apply( dechunken( body ).getBytes( UTF_8 ) ) );
	}

	/**
	 * @param parent content basis
	 */
	protected HttpRes( HttpRes parent ) {
		super( parent );
	}

	@Override
	public HttpRes child() {
		return copyMasksTo( new HttpRes( this ) );
	}

	@Override
	public HttpRes peer( byte[] content ) {
		return copyMasksTo( new HttpRes( content, bytes -> body()
				.map( b -> {
					try {
						return b.peer( bytes );
					}
					catch( Exception e ) {
						throw new IllegalArgumentException(
								String.format( "Failed to parse %s from body content"
										+ "\nUTF8:[%s]"
										+ "\n hex:[%s]",
										b.getClass().getSimpleName(),
										new String( bytes, StandardCharsets.UTF_8 ),
										Bytes.toHex( bytes ) ),
								e );
					}
				} )
				.orElse( null ) ) );
	}

	@Override
	public Set<String> fields() {
		Set<String> names = new TreeSet<>();
		names.add( VERSION );
		names.add( HttpRes.STATUS );
		names.add( HttpRes.STATUS_TEXT );
		names.add( BODY );
		headers().keySet().stream()
				.map( HttpMsg::header ) // headers() strips the presuffix, so we have to put it back
				.forEach( names::add );
		body().ifPresent( b -> names.addAll( b.fields() ) );
		return names;
	}

	@Override
	protected boolean isHttpField( String field ) {
		return VERSION.equals( field )
				|| HttpRes.STATUS.equals( field )
				|| HttpRes.STATUS_TEXT.equals( field )
				|| BODY.equals( field )
				|| isHeaderField( field );
	}

	@Override
	protected String serialise( String bodyContent, boolean wireFormat ) {
		StringBuilder sb = new StringBuilder();
		// status
		sb.append( Stream.of(
				version(), status(), statusText() )
				.collect( joining( " " ) ) )
				.append( "\r\n" );

		// headers
		headers().entrySet().stream()
				.map( e -> String.format( "%s: %s\r\n",
						e.getKey(),
						e.getValue() ) )
				.forEach( sb::append );

		sb.append( "\r\n" );

		// body
		sb.append( enchunken( bodyContent, wireFormat ) );

		return sb.toString();
	}

	private String status() {
		return data().getOrDefault( HttpRes.STATUS, "" ).toString();
	}

	private String statusText() {
		return data().getOrDefault( HttpRes.STATUS_TEXT, "" ).toString();
	}

}
