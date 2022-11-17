
package com.mastercard.test.flow.msg.http;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.msg.AbstractMessage;
import com.mastercard.test.flow.msg.ExposedMasking;
import com.mastercard.test.flow.msg.Mask;

/**
 * Common features of HTTP requests and responses
 *
 * @param <T> self
 */
public abstract class HttpMsg<T extends HttpMsg<T>> extends AbstractMessage<T> {

	/**
	 * Use this as the field path to set the HTTP request version
	 */
	public static final String VERSION = "HTTP_VERSION";

	/**
	 * Use this as the field path to set the HTTP request body
	 */
	public static final String BODY = "HTTP_BODY";

	/**
	 * Start and end the field path with this to add a header
	 */
	public static final String HEADER_PRESUFIX = "^";

	/**
	 * Matches header lines
	 */
	protected static final Pattern HEADER_LINE_PATTERN = Pattern
			.compile( "(?<name>[^:]*?):(?<value>.*)" );

	private final Supplier<Map<String, Object>> basis;
	private Optional<ExposedMasking> body = Optional.empty();

	/**
	 * Empty message
	 */
	protected HttpMsg() {
		this.basis = TreeMap::new;
	}

	/**
	 * @param parent basis for message definition
	 */
	protected HttpMsg( T parent ) {
		basis = parent::data;
		body = parent.body().map( ExposedMasking::child );
	}

	@Override
	public String assertable( Unpredictable... sources ) {
		// see if there are any masks on the wrapped message
		Set<Mask> bodyMasks = Stream.of( sources )
				.map( s -> body().map( b -> b.masks().get( s ) ).orElse( null ) )
				.filter( Objects::nonNull )
				.collect( Collectors.toSet() );

		if( !bodyMasks.isEmpty() ) {
			// there are!

			// create child message
			T child = child();

			// directly apply masks to the child's body
			child.body().ifPresent( cb -> bodyMasks.forEach( m -> m.accept( cb ) ) );

			// now we can use standard behaviour to mask the HttpMsg itself
			List<Mask> toApply = Stream.of( sources )
					.map( child.masks::get )
					.filter( Objects::nonNull )
					.collect( Collectors.toList() );

			if( toApply.isEmpty() ) {
				return child.asHuman();
			}

			for( Mask mask : toApply ) {
				mask.accept( child );
			}

			return child.asHuman();
		}

		return super.assertable( sources );
	}

	@Override
	public T set( String field, Object value ) {
		if( BODY.equals( field ) ) {
			if( value == DELETE ) {
				body = Optional.empty();
			}
			else {
				body = Optional.ofNullable( (ExposedMasking) value );
			}
		}
		else if( isHttpField( field ) ) {
			super.set( field, value );
		}
		else {
			body().ifPresent( b -> b.set( field, value ) );
		}
		return self();
	}

	@Override
	public Object get( String field ) {
		if( BODY.equals( field ) ) {
			return body.orElse( null );
		}
		if( isHttpField( field ) ) {
			return data().get( field );
		}
		return body()
				.map( b -> b.get( field ) )
				.orElse( null );
	}

	/**
	 * @param field a field name
	 * @return <code>true</code> if the supplied field relates to the HTTP message
	 *         and not the body message
	 */
	protected abstract boolean isHttpField( String field );

	@Override
	protected String asHuman() {
		return serialise( body.map( Message::assertable ).orElse( "" ), false );
	}

	@Override
	public byte[] content() {
		return serialise(
				body.map( b -> new String( b.content(), UTF_8 ) ).orElse( "" ),
				true )
						.getBytes( UTF_8 );
	}

	/**
	 * @param bodyContent The message body content
	 * @param wireFormat  <code>true</code> if we should try to create an accurate
	 *                    representation of the bytes that go on the wire,
	 *                    <code>false</code> for a more human-friendly output
	 * @return The complete formatted message
	 */
	protected abstract String serialise( String bodyContent, boolean wireFormat );

	/**
	 * Formats body content if we're using chunked Transfer-Encoding
	 *
	 * @param content the body content
	 * @param wire    <code>true</code> if we should try to create an accurate
	 *                representation of the bytes that go on the wire,
	 *                <code>false</code> for a more human-friendly output
	 * @return The complete formatted body
	 */
	protected String enchunken( String content, boolean wire ) {
		if( wire && "chunked".equals( get( header( "Transfer-Encoding" ) ) ) ) {
			return String.format( ""
					+ "%s\r\n" // chunk length
					+ "%s\r\n" // chunk
					+ "0\r\n" // end chunk
					+ "\r\n",
					Integer.toHexString( content.length() ),
					content );
		}
		return content;
	}

	private static Pattern chunkLength = Pattern.compile( "\r\n([0-9a-fA-F]+)\r\n" );

	/**
	 * Extracts enchunked body content
	 *
	 * @param chunked A chunked body
	 * @return The actual content
	 */
	protected String dechunken( String chunked ) {
		if( "chunked".equals( get( header( "Transfer-Encoding" ) ) ) ) {
			StringBuilder unchunked = new StringBuilder();
			String input = "\r\n" + chunked; // so we can catch the first chunk with our regex
			Matcher m = chunkLength.matcher( input );
			int searchIndex = 0;
			while( m.find( searchIndex ) ) {
				int l = Integer.parseInt( m.group( 1 ), 16 );
				unchunked.append( input.substring( m.end(), m.end() + l ) );
				searchIndex = m.end() + l;
			}
			return unchunked.toString();
		}
		return chunked;
	}

	/**
	 * @return The final message data
	 */
	protected Map<String, Object> data() {
		Map<String, Object> data = basis.get();
		for( Update update : updates ) {
			if( update.value() == DELETE ) {
				data.remove( update.field() );
			}
			else {
				data.put( update.field(), update.value() );
			}
		}
		return data;
	}

	/**
	 * @return message headers
	 */
	public Map<String, String> headers() {
		Map<String, String> headers = new TreeMap<>();
		data().entrySet()
				.stream().filter( e -> isHeaderField( e.getKey() ) )
				.forEach( e -> headers.put( unheader( e.getKey() ), String.valueOf( e.getValue() ) ) );
		return headers;
	}

	/**
	 * @param field a field name
	 * @return <code>true</code> if the supplied field should be populated in the
	 *         header
	 */
	protected static boolean isHeaderField( String field ) {
		return field.startsWith( HEADER_PRESUFIX ) && field.endsWith( HEADER_PRESUFIX );
	}

	/**
	 * @param name The desired header name
	 * @return The value to pass as the field name in {@link #set(String, Object)}
	 *         and {@link #get(String)}
	 */
	public static String header( String name ) {
		return HEADER_PRESUFIX + name + HEADER_PRESUFIX;
	}

	/**
	 * The inverse of {@link #header(String)}
	 *
	 * @param name The value returned from {@link #header(String)}
	 * @return The value passed to that invocation of {@link #header(String)}
	 */
	public static String unheader( String name ) {
		return name.substring( HEADER_PRESUFIX.length(), name.length() - HEADER_PRESUFIX.length() );
	}

	/**
	 * @return message HTTP version
	 */
	public String version() {
		return data().getOrDefault( VERSION, "" ).toString();
	}

	/**
	 * @return message body as a {@link Message} object
	 */
	public Optional<ExposedMasking> body() {
		return body;
	}

	/**
	 * @return The text of the message body as a string
	 */
	public String bodyText() {
		return body().map( ExposedMasking::assertable ).orElse( "" );
	}
}
