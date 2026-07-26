package com.mastercard.test.flow.msg.json;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.msg.AbstractMessage;
import com.mastercard.test.flow.msg.Forest;

/**
 * A JSON message. Fields are addressed by simple jsonpath-ish strings
 */
public class Json extends AbstractMessage<Json> {
	private static final ObjectMapper COMPACT = new ObjectMapper()
			.enable( DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS )
			.enable( SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS );
	private static final ObjectWriter INDENT = new ObjectMapper()
			.enable( DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS )
			.enable( SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS )
			.enable( SerializationFeature.INDENT_OUTPUT )
			.writer( new DefaultPrettyPrinter().withObjectIndenter(
					new DefaultIndenter().withLinefeed( "\n" ) ) );

	/**
	 * Supply this as a field value to populate an empty map
	 */
	public static final Object EMPTY_MAP = new Object() {
		@Override
		public String toString() {
			return "Json.EMPTY_MAP";

		}
	};

	/**
	 * Supply this as a field value to populate an empty list
	 */
	public static final Object EMPTY_LIST = new Object() {
		@Override
		public String toString() {
			return "Json.EMPTY_LIST";
		}
	};

	private final Supplier<Object> basis;

	private Json( Supplier<Object> basis ) {
		this.basis = basis;
	}

	/**
	 * Assumes the root json element is an object
	 */
	public Json() {
		this( HashMap::new );
	}

	/**
	 * @param bytes json content
	 */
	public Json( byte[] bytes ) {
		this( () -> {
			try {
				return COMPACT.readValue( bytes, Object.class );
			}
			catch( IOException ioe ) {
				throw new IllegalArgumentException( String.format(
						"Failed to parse '%s' (%s)",
						new String( bytes, UTF_8 ), Arrays.toString( bytes ) ),
						ioe );
			}
		} );
	}

	@Override
	public Json child() {
		return copyMasksTo( new Json( this::data ) );
	}

	@Override
	public Json peer( byte[] content ) {
		return copyMasksTo( new Json( content ) );
	}

	@Override
	protected Object validateValueType( String field, Object value ) {
		if( value == EMPTY_MAP || value == EMPTY_LIST ) {
			return value;
		}
		return super.validateValueType( field, value );
	}

	private Object data() {
		Object o = basis.get();
		for( Update update : updates ) {
			Object value = value( update );
			o = traverse( o, update.field(),
					// no sense in vivifying path elements if we're on our way to delete something
					// that doesn't exist yet
					value != DELETE,
					( map, key ) -> {
						if( value == DELETE ) {
							map.remove( key );
						}
						else {
							map.put( key, value );
						}
					},
					( list, idx ) -> {
						if( value == DELETE ) {
							list.remove( idx );
						}
						else {
							list.set( idx, value );
						}
					} );
		}
		return o;
	}

	private static Object value( Update update ) {
		Object value = update.value();
		if( value == EMPTY_MAP ) {
			value = new TreeMap<>();
		}
		else if( value == EMPTY_LIST ) {
			value = new ArrayList<>();
		}
		return value;
	}

	@Override
	protected Object access( String field ) {
		AtomicReference<Object> result = new AtomicReference<>();
		traverse( data(), field, false,
				( map, key ) -> result.set( map.get( key ) ),
				( list, idx ) -> result.set( list.get( idx ) ) );
		return result.get();
	}

	private static Object traverse( Object data, String field,
			boolean vivify,
			BiConsumer<Map<String, Object>, String> oa,
			ObjIntConsumer<List<Object>> la ) {
		Deque<String> path = new ArrayDeque<>();
		Collections.addAll( path, field.split( "\\." ) );
		if( !path.getFirst().isEmpty() && !path.getFirst().startsWith( "[" ) ) {
			path.addFirst( "" );
		}

		Map<String, Object> root = new TreeMap<>();
		root.put( "", data );
		Forest.traverse( root, path, vivify, oa, la );
		return root.get( "" );
	}

	@Override
	public Set<String> fields() {
		Set<String> fields = new TreeSet<>();
		Forest.leaves( ".", data(),
				( path, value ) -> fields.add( path ) );
		return fields;
	}

	@Override
	public byte[] content() {
		Object data = data();
		try {
			return COMPACT.writeValueAsBytes( data );
		}
		catch( JsonProcessingException jpe ) {
			throw new IllegalStateException( "Failed to serialise " + data, jpe );
		}
	}

	@Override
	protected String asHuman() {
		Object data = data();
		try {
			return INDENT.writeValueAsString( data );
		}
		catch( JsonProcessingException jpe ) {
			throw new IllegalStateException( "Failed to serialise " + data, jpe );
		}
	}

}
