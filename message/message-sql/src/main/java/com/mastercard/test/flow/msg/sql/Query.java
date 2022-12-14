package com.mastercard.test.flow.msg.sql;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * A generic SQL query message. Note that the wire protocol is specific to the
 * SQL implementation (e.g.: postgres uses a different format to mysql, etc).
 * This class doesn't attempt to replicate any of those implementations, so
 * don't pay too much attention to the bytes of the encoded messages
 */
public class Query extends AbstractMessage<Query> {

	private static final ObjectMapper WIRE = new ObjectMapper();

	/**
	 * The address field to use to update the query text
	 */
	public static final String SQL = "sql";

	private static final String WARNING_KEY = "warning";

	private final Supplier<Map<String, Object>> basis;

	private Query( Supplier<Map<String, Object>> basis ) {
		this.basis = basis;
	}

	/**
	 * @param sql The query text
	 */
	public Query( String sql ) {
		this( HashMap::new );
		set( SQL, sql );
	}

	private Query( byte[] bytes ) {
		this( () -> {
			try {
				Map<String, Object> m = WIRE.readValue( bytes,
						new TypeReference<Map<String, Object>>() {
							// type hint only
						} );
				m.remove( WARNING_KEY );
				return m;
			}
			catch( IOException ioe ) {
				throw new IllegalArgumentException( String.format(
						"Failed to parse '%s' (%s)",
						new String( bytes, UTF_8 ), Arrays.toString( bytes ) ),
						ioe );
			}
		} );
	}

	private Map<String, Object> data() {
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

	@Override
	public byte[] content() {
		Map<String, Object> data = data();
		data.put( WARNING_KEY, "This is not representative of an actual wire protocol" );
		try {
			return WIRE.writeValueAsBytes( data );
		}
		catch( JsonProcessingException e ) {
			throw new IllegalStateException( "Failed to encode " + data, e );
		}
	}

	@Override
	public Set<String> fields() {
		return data().keySet();
	}

	@Override
	public Object get( String field ) {
		return data().get( field );
	}

	/**
	 * Extracts query data
	 *
	 * @param binds A list to put bind variables in
	 * @return the query SQL
	 */
	public String get( List<Object> binds ) {
		Map<String, Object> data = data();
		for( int i = 1; data.containsKey( String.valueOf( i ) ); i++ ) {
			binds.add( data.get( String.valueOf( i ) ) );
		}
		return (String) data.get( SQL );
	}

	@Override
	protected String asHuman() {
		StringBuilder sb = new StringBuilder();
		Map<String, Object> data = data();
		sb.append( "Query:\n" )
				.append( SqlFormatter.format( String.valueOf( data.get( SQL ) ) ) )
				.append( "\nBind variables:" );

		data.entrySet().stream()
				.filter( e -> e.getKey().matches( "\\d+" ) )
				.sorted( Comparator.comparing( e -> Integer.parseInt( e.getKey() ) ) )
				.forEach( e -> sb.append( String.format(
						"\n %2d : %s",
						Integer.parseInt( e.getKey() ), formatValue( e.getValue() ) ) ) );

		return sb.toString();
	}

	/**
	 * Formats a bind variable for display in the assertable message content
	 *
	 * @param value The bind variable
	 * @return The human-useful value to display
	 */
	protected String formatValue( Object value ) {
		if( value instanceof byte[] ) {
			return "bytes: " + Base64.getEncoder().encodeToString( (byte[]) value );
		}
		return String.valueOf( value );
	}

	@Override
	public Query child() {
		return copyMasksTo( new Query( this::data ) );
	}

	@Override
	public Query peer( byte[] content ) {
		return copyMasksTo( new Query( content ) );
	}

}
