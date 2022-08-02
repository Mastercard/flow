package com.mastercard.test.flow.msg.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.util.Bytes;

/**
 * An HTTP response message, which probably encapsulates another {@link Message}
 * as the response body
 */
public class HttpRes extends HttpMsg<HttpRes> {

	private static final Pattern REQ_PATTERN = Pattern
			.compile( "^(?<version>.*?)\\s+(?<status>.*?)\\s+(?<text>.*?)\r\n"
					+ "(?<headers>.*?)\r\n"
					+ "\r\n"
					+ "(?<body>.*)$", Pattern.DOTALL );
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

		Matcher m = REQ_PATTERN.matcher( new String( content, UTF_8 ) );
		if( m.matches() ) {
			set( VERSION, m.group( "version" ).trim() );
			set( HttpRes.STATUS, m.group( "status" ).trim() );
			set( HttpRes.STATUS_TEXT, m.group( "text" ).trim() );

			for( String line : m.group( "headers" ).split( "\r\n" ) ) {
				Matcher h = HEADER_PATTERN.matcher( line );
				if( h.matches() ) {
					set( HEADER_PRESUFIX + h.group( "name" ).trim() + HEADER_PRESUFIX,
							h.group( "value" ).trim() );
				}
			}

			set( BODY, bodyParse.apply( dechunken( m.group( "body" ) ).getBytes( UTF_8 ) ) );
		}
	}

	private HttpRes( HttpRes parent ) {
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
		return String.format( ""
				+ "%s %s %s\r\n" // response line
				+ "%s" // headers (will supply their own line endings)
				+ "\r\n" // empty line
				+ "%s", // body,
				version(), status(), statusText(),
				headers().entrySet().stream()
						.map( e -> String.format( "%s: %s\r\n",
								e.getKey(),
								e.getValue() ) )
						.collect( joining() ),
				enchunken( bodyContent, wireFormat ) );
	}

	private String status() {
		return data().getOrDefault( HttpRes.STATUS, "" ).toString();
	}

	private String statusText() {
		return data().getOrDefault( HttpRes.STATUS_TEXT, "" ).toString();
	}

}
