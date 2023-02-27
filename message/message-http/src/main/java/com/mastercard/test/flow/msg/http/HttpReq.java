package com.mastercard.test.flow.msg.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.util.Bytes;

/**
 * An HTTP request message, which probably encapsulates another {@link Message}
 * as the request body
 */
public class HttpReq extends HttpMsg<HttpReq> {

	/**
	 * Use this as the field path to set the HTTP request method
	 */
	public static final String METHOD = "HTTP_METHOD";

	/**
	 * Use this as the field path to set the HTTP request path. path fragments that
	 * start and end with {@link HttpReq#PATH_VAR_PRESUFIX} are subject to
	 * independent updating
	 */
	public static final String PATH = "HTTP_PATH";

	/**
	 * Start and end the field path with this to add a path variable
	 */
	public static final String PATH_VAR_PRESUFIX = "%";

	/**
	 * An empty request
	 */
	public HttpReq() {
	}

	/**
	 * @param content   request bytes
	 * @param bodyParse How to parse body bytes as a message
	 */
	public HttpReq( byte[] content, Function<byte[], Message> bodyParse ) {

		Deque<String> lines = new ArrayDeque<>(
				Arrays.asList( new String( content, UTF_8 ).split( "\r\n" ) ) );

		// start line
		Deque<String> startFields = new ArrayDeque<>(
				Arrays.asList( lines.removeFirst().split( " " ) ) );

		if( !startFields.isEmpty() ) {
			set( METHOD, startFields.removeFirst() );
		}
		if( !startFields.isEmpty() ) {
			set( PATH, startFields.removeFirst() );
		}
		if( !startFields.isEmpty() ) {
			set( VERSION, startFields.stream().collect( joining( " " ) ) );
		}

		// zero or more headers
		String headerLine;
		while( !lines.isEmpty() && !(headerLine = lines.removeFirst()).isEmpty() ) {
			Matcher h = HEADER_LINE_PATTERN.matcher( headerLine );
			if( h.matches() ) {
				set( header( h.group( "name" ).trim() ), h.group( "value" ).trim() );
			}
		}

		// body
		String body = lines.stream().collect( joining( "\r\n" ) );
		set( BODY, bodyParse.apply( dechunken( body ).getBytes( UTF_8 ) ) );
	}

	/**
	 * @param parent content basis
	 */
	protected HttpReq( HttpReq parent ) {
		super( parent );
	}

	@Override
	public HttpReq child() {
		return copyMasksTo( new HttpReq( this ) );
	}

	@Override
	public HttpReq peer( byte[] content ) {
		return copyMasksTo( new HttpReq( content, bytes -> body()
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
				.orElse( null ) ) )
						.applyPathPattern( rawPath() );
	}

	/**
	 * Parses the current path to populate variables based on the supplied pattern
	 *
	 * @param pattern The desired path pattern
	 * @return <code>this</code>
	 */
	private HttpReq applyPathPattern( String pattern ) {

		// convert the path pattern, where variables are delimited by %, into a regex
		// that captures those variables
		Pattern p = Pattern.compile( pattern
				// careful of the query delimiter!
				.replace( "?", "\\?" )
				.replaceAll(
						HttpReq.PATH_VAR_PRESUFIX + ".*?" + HttpReq.PATH_VAR_PRESUFIX,
						"(.*?)" ) );

		Matcher pm = p.matcher( pattern );
		Matcher rm = p.matcher( rawPath() );
		if( pm.matches() && rm.matches() ) {
			// That regex matches both:
			// * the pattern (from where we'll get the variable names)
			// * the path of this message (from where we'll get the variable values)
			for( int i = 1; i <= pm.groupCount(); i++ ) {
				set( pm.group( i ), rm.group( i ) );
			}
			set( HttpReq.PATH, pattern );
		}

		return this;
	}

	@Override
	public Set<String> fields() {
		Set<String> names = new TreeSet<>();
		names.add( HttpReq.METHOD );
		names.add( HttpReq.PATH );
		names.add( VERSION );
		names.add( BODY );
		headers().keySet().stream()
				.map( HttpMsg::header ) // headers() strips the presuffix, so we have to put it back
				.forEach( names::add );
		names.addAll( pathVars().keySet() );
		body().ifPresent( b -> names.addAll( b.fields() ) );
		return names;
	}

	@Override
	protected boolean isHttpField( String field ) {
		return HttpReq.METHOD.equals( field )
				|| HttpReq.PATH.equals( field )
				|| VERSION.equals( field )
				|| BODY.equals( field )
				|| isHeaderField( field )
				|| isPathField( field );
	}

	@Override
	protected String serialise( String bodyContent, boolean wireFormat ) {
		return String.format( ""
				+ "%s %s %s\r\n" // request line
				+ "%s" // headers (will supply their own line endings)
				+ "\r\n" // empty line
				+ "%s", // body
				method(), path(), version(),
				headers().entrySet().stream()
						.map( e -> String.format( "%s: %s\r\n", e.getKey(), e.getValue() ) )
						.collect( joining() ),
				enchunken( bodyContent, wireFormat ) );
	}

	/**
	 * @return request method
	 */
	public String method() {
		return data().getOrDefault( HttpReq.METHOD, "" ).toString();
	}

	/**
	 * @param name The desired path variable name
	 * @return The value to pass as the field name in {@link #set(String, Object)}
	 *         and {@link #get(String)}
	 */
	public static String path( String name ) {
		return HttpReq.PATH_VAR_PRESUFIX + name + HttpReq.PATH_VAR_PRESUFIX;
	}

	private static boolean isPathField( String field ) {
		return field.startsWith( HttpReq.PATH_VAR_PRESUFIX )
				&& field.endsWith( HttpReq.PATH_VAR_PRESUFIX );
	}

	private String rawPath() {
		return data().getOrDefault( HttpReq.PATH, "" ).toString();
	}

	private Map<String, String> pathVars() {
		return data().entrySet().stream()
				.filter( e -> isPathField( e.getKey() ) )
				.collect( toMap( Map.Entry::getKey, e -> String.valueOf( e.getValue() ) ) );
	}

	/**
	 * @return The request path
	 */
	public String path() {
		Map<String, String> vars = pathVars();
		String p = rawPath();
		for( Map.Entry<String, String> e : vars.entrySet() ) {
			p = p.replace( e.getKey(), e.getValue() );
		}
		return p;
	}
}
