package com.mastercard.test.flow.msg.sql;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * A generic SQL query result. Note that the wire protocol is specific to the
 * SQL implementation (e.g.: postgres uses a different format to mysql, etc).
 * This class doesn't attempt to replicate any of those implementations, so
 * don't pay too much attention to the bytes of the encoded messages
 */
public class Result extends AbstractMessage<Result> {

	private static final ObjectMapper WIRE = new ObjectMapper();

	/**
	 * Field address to use to update the column list
	 */
	public static final String COLUMNS = "columns";
	private static final Pattern ROW_COL_PTRN = Pattern.compile(
			"(\\d+):(\\d+)" );

	private final Supplier<ResultSetStructure> basis;

	private Result( Supplier<ResultSetStructure> basis ) {
		this.basis = basis;
	}

	/**
	 * @param columns The names of the result columns
	 */
	public Result( String... columns ) {
		this( () -> new ResultSetStructure(
				new ArrayList<>( Arrays.asList( columns ) ),
				new ArrayList<>() ) );
	}

	private Result( byte[] bytes ) {
		this( () -> {
			try {
				return WIRE.readValue( bytes, ResultSetStructure.class );
			}
			catch( Exception e ) {
				throw new IllegalStateException( "Failed to parse\n"
						+ new String( bytes, StandardCharsets.UTF_8 ) + "\n"
						+ Arrays.toString( bytes ), e );
			}
		} );
	}

	@Override
	public Result child() {
		return copyMasksTo( new Result( this::data ) );
	}

	@Override
	public Result peer( byte[] content ) {
		return copyMasksTo( new Result( content ) );
	}

	private ResultSetStructure data() {
		ResultSetStructure data = basis.get();

		for( Update update : updates ) {
			if( COLUMNS.equals( update.field() ) ) {
				data.columns.clear();
				Stream.of( String.valueOf( update.value() ).split( "," ) )
						.forEach( data.columns::add );
			}
			else if( update.field().matches( "\\d+" ) ) {
				// whole-row operations
				int row = Integer.parseInt( update.field() );
				if( update.value() == DELETE ) {
					if( data.maps.size() > row ) {
						data.maps.remove( row );
					}
				}
				else {
					while( data.maps.size() <= row ) {
						data.maps.add( new TreeMap<>() );
					}
					data.maps.get( row ).clear();
					@SuppressWarnings("unchecked")
					List<Object> values = (List<Object>) update.value();
					for( int i = 0; i < values.size(); i++ ) {
						data.maps.get( row ).put( i, values.get( i ) );
					}
				}
			}
			else {
				Matcher m = ROW_COL_PTRN.matcher( update.field() );
				if( m.matches() ) {

					int row = Integer.parseInt( m.group( 1 ) );
					int column = Integer.parseInt( m.group( 2 ) );

					if( update.value() == DELETE ) {
						if( data.maps.size() > row ) {
							data.maps.get( row ).remove( column );
						}
					}
					else {
						while( data.maps.size() <= row ) {
							data.maps.add( new TreeMap<>() );
						}
						data.maps.get( row ).put( column, update.value() );
					}
				}
			}
		}

		return data;
	}

	@Override
	protected Object validateValueType( String field, Object value ) {
		// We want to allow adding rows with a list. We copy the list values anyway, so
		// have no fears about mutability
		if( field.matches( "\\d+" ) && value instanceof List<?> ) {
			// need to check it's not a nested list though
			int idx = 0;
			for( Object o : (List<?>) value ) {
				super.validateValueType( field + ":" + idx, o );
				idx++;
			}
			return value;
		}
		return super.validateValueType( field, value );
	}

	@Override
	public byte[] content() {
		try {
			return WIRE.writeValueAsBytes( data().prepForSerialisation() );
		}
		catch( JsonProcessingException e ) {
			throw new IllegalStateException( "Failed to encode", e );
		}
	}

	@Override
	public Set<String> fields() {
		Set<String> fields = new TreeSet<>();
		fields.add( COLUMNS );
		List<Map<Integer, Object>> rows = data().maps;
		for( int i = 0; i < rows.size(); i++ ) {
			for( Integer k : rows.get( i ).keySet() ) {
				fields.add( i + ":" + k );
			}
		}
		return fields;
	}

	@Override
	protected Object access( String field ) {
		ResultSetStructure data = data();
		if( COLUMNS.equals( field ) ) {
			return new ArrayList<>( data.columns );
		}

		if( field.matches( "\\d+" ) ) {
			int row = Integer.parseInt( field );
			if( data.maps.size() > row ) {
				List<Object> values = new ArrayList<>();
				for( int i = 0; i < data.columns.size(); i++ ) {
					values.add( data.maps.get( row ).get( i ) );
				}
				return values;
			}
			return null;
		}

		Matcher m = ROW_COL_PTRN.matcher( field );
		if( m.matches() ) {
			int row = Integer.parseInt( m.group( 1 ) );
			int column = Integer.parseInt( m.group( 2 ) );
			if( data.maps.size() > row ) {
				return data.maps.get( row ).get( column );
			}
		}

		return null;
	}

	/**
	 * @return The rows of this result, in column/value maps
	 */
	public List<Map<String, Object>> get() {
		ResultSetStructure data = data();
		return data.maps.stream()
				.map( row -> row.entrySet().stream()
						.collect( toMap(
								e -> data.columns.get( e.getKey() ),
								Entry::getValue ) ) )
				.collect( toList() );
	}

	@Override
	protected String asHuman() {
		StringBuilder sb = new StringBuilder();
		ResultSetStructure data = data();
		String colfmt = "%" + data.columns.stream()
				.mapToInt( String::length )
				.max().orElse( 0 ) + "s";
		int rowIdx = 0;
		for( Map<Integer, Object> row : data.maps ) {
			if( rowIdx != 0 ) {
				sb.append( "\n" );
			}
			sb.append( " --- Row " ).append( rowIdx ).append( " ---" );

			for( int columnIdx = 0; columnIdx < data.columns.size(); columnIdx++ ) {
				String column = data.columns.get( columnIdx );
				Object value = row.get( columnIdx );

				sb.append( "\n " )
						.append( String.format( colfmt, column ) )
						.append( " : " )
						.append( formatValue( value ) );
			}
			rowIdx++;
		}
		return sb.toString();
	}

	/**
	 * Formats a result column value for display in the assertable message content
	 *
	 * @param value The value
	 * @return The human-useful value to display
	 */
	private static String formatValue( Object value ) {
		if( value instanceof byte[] ) {
			return Base64.getEncoder().encodeToString( (byte[]) value );
		}
		return String.valueOf( value );
	}

	private static class ResultSetStructure {
		@JsonProperty("warning")
		private final String warning = "This is not representative of an actual wire protocol";

		@JsonProperty("columns")
		final List<String> columns;

		final List<Map<Integer, Object>> maps;

		@JsonProperty("rows")
		private final List<List<TypedKVP<Integer>>> rows;

		public ResultSetStructure(
				@JsonProperty("columns") List<String> columns,
				@JsonProperty("rows") List<List<TypedKVP<Integer>>> rows ) {
			this.columns = columns;
			this.rows = rows;

			maps = new ArrayList<>();
			rows.forEach( row -> {
				Map<Integer, Object> m = new TreeMap<>();
				row.forEach( kvp -> m.put( kvp.key(), kvp.value() ) );
				maps.add( m );
			} );
		}

		ResultSetStructure prepForSerialisation() {
			maps.stream()
					.map( row -> row.entrySet().stream()
							.map( e -> new TypedKVP<>( e.getKey(), e.getValue() ) )
							.collect( Collectors.toList() ) )
					.forEach( rows::add );
			return this;
		}
	}

}
