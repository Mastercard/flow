
package com.mastercard.test.flow.example.app.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DB interaction methods
 */
public class DB {

	private static final Logger log = LoggerFactory.getLogger( DB.class );

	/**
	 * @param db            How to connect to the DB
	 * @param query         A select statement
	 * @param bindVariables Bind variables for the query
	 * @return a list of column/value maps
	 */
	public static List<Map<String, Object>> select( DataSource db, String query,
			Object... bindVariables ) {
		return query( db, query ).bind( bindVariables ).extract( listOf( columnValueMap ) );
	}

	/**
	 * @param db            How to connect to the DB
	 * @param sql           An update/insert statement
	 * @param bindVariables Bind variables for the query
	 * @return the number of rows affected
	 */
	public static int update( DataSource db, String sql, Object... bindVariables ) {
		return query( db, sql ).bind( bindVariables ).execute();
	}

	/**
	 * Builds a query. Variables can then be bound with <code>bind()</code>, and the
	 * query executed with {@link #execute()} or
	 * {@link #extract(ResultSetExtractor)}
	 *
	 * @param db  How to connect to the DB
	 * @param sql The query
	 * @return A query object, ready for variable binds and execution
	 */
	public static DB query( DataSource db, String sql ) {
		return new DB( db, sql );
	}

	private final DataSource datasource;
	private final String sql;
	private Object[] bindVariables = {};

	private DB( DataSource db, String sql ) {
		datasource = db;
		this.sql = sql;
	}

	/**
	 * @param binds Bind variables for the query
	 * @return <code>this</code>
	 */
	public DB bind( Object... binds ) {
		bindVariables = binds;
		return this;
	}

	/**
	 * @param binds Bind variables for the query
	 * @return <code>this</code>
	 */
	public DB bind( Collection<Object> binds ) {
		bindVariables = binds.toArray( new Object[binds.size()] );
		return this;
	}

	/**
	 * Executes the query and extracts some data
	 *
	 * @param <T>       Extracted type
	 * @param extractor the data to extract
	 * @return the extracted data
	 */
	public <T> T extract( ResultSetExtractor<T> extractor ) {
		return doSQL( datasource, queryExecutor( extractor ), sql, bindVariables );
	}

	/**
	 * Executes the update
	 *
	 * @return the number of rows affected
	 */
	public int execute() {
		return doSQL( datasource, updateExecutor, sql, bindVariables );
	}

	@Override
	public String toString() {
		return sql + " " + Arrays.toString( bindVariables );
	}

	/**
	 * Executes an SQL query
	 *
	 * @param <T>           Extracted type
	 * @param db            How to connect to the DB
	 * @param executor      How to execute the query
	 * @param sql           The query
	 * @param bindVariables The bind variables
	 * @return the result of the executor
	 */
	public static <T> T doSQL( DataSource db, Executor<T> executor, String sql,
			Object... bindVariables ) {
		if( log.isInfoEnabled() ) {
			log.info( "Executing '{}' : {}", sql,
					Stream.of( bindVariables )
							.map( DB::bytesToText )
							.collect( Collectors.toList() ) );
		}

		try( Connection c = db.getConnection();
				PreparedStatement ps = c.prepareStatement( sql ) ) {

			for( int i = 0; i < bindVariables.length; i++ ) {
				ps.setObject( i + 1, bindVariables[i] );
			}

			return executor.execute( ps );
		}
		catch( SQLException sqle ) {
			throw new IllegalStateException( sqle );
		}
	}

	/**
	 * @param v An object
	 * @return The same object, or a useful string representation if the object was
	 *         a byte array
	 */
	public static Object bytesToText( Object v ) {
		if( v instanceof byte[] ) {
			return Base64.getEncoder().encodeToString( (byte[]) v );
		}
		return v;
	}

	/**
	 * @param m a map
	 * @return A string of the map contents, but with byte arrays in a useful format
	 */
	public static String bytesToText( Map<String, Object> m ) {
		return m.entrySet().stream().map( e -> e.getKey() + "=" + bytesToText( e.getValue() ) )
				.collect( Collectors.joining( ", ", "{", "}" ) );
	}

	/**
	 * Converts a single row into some object
	 *
	 * @param <T> The extracted type
	 */
	public abstract static class RowMapper<T> {

		/**
		 * Indicates if we should stop mapping rows
		 */
		protected boolean stop = false;

		/**
		 * @param rs     The results from the db
		 * @param rowNum The current row number
		 * @return the extracted data
		 * @throws SQLException If extraction fails
		 */
		public abstract T mapRow( ResultSet rs, int rowNum ) throws SQLException;
	}

	/**
	 * Converts a {@link ResultSet} into some object
	 *
	 * @param <T> extracted type
	 */
	public interface ResultSetExtractor<T> {

		/**
		 * @param rs Data from the DB
		 * @return The extracted value
		 * @throws SQLException if extraction fails
		 */
		T extractData( ResultSet rs ) throws SQLException;
	}

	/**
	 * Executes a {@link PreparedStatement} and returns some result
	 *
	 * @param <T>
	 */
	private interface Executor<T> {

		T execute( PreparedStatement ps ) throws SQLException;
	}

	/**
	 * Executes a SQL query
	 *
	 * @param rse
	 * @return the result extracted from the {@link ResultSet}
	 */
	private static <T> Executor<T> queryExecutor( final ResultSetExtractor<T> rse ) {
		return ps -> {
			try( ResultSet rs = ps.executeQuery() ) {
				return rse.extractData( rs );
			}
		};
	}

	/**
	 * Executes an update - returns the number of rows affected
	 */
	private static Executor<Integer> updateExecutor = PreparedStatement::executeUpdate;

	/**
	 * Maps rows to column/object value map
	 */
	public static final RowMapper<
			Map<String, Object>> columnValueMap = new RowMapper<Map<String, Object>>() {

				@Override
				public Map<String, Object> mapRow( ResultSet rs, int rowNum ) throws SQLException {
					Map<String, Object> row = new TreeMap<>();
					ResultSetMetaData rsmd = rs.getMetaData();
					for( int i = 1; i <= rsmd.getColumnCount(); i++ ) {
						row.put( rsmd.getColumnLabel( i ), rs.getObject( i ) );
					}
					return row;
				}
			};

	/**
	 * @param name the column name
	 * @return A {@link RowMapper} that will extract a single column as a string
	 */
	public static RowMapper<String> stringColumn( final String name ) {
		return new RowMapper<String>() {

			@Override
			public String mapRow( ResultSet rs, int rowNum ) throws SQLException {
				return rs.getString( name );
			}
		};
	}

	/**
	 * @param columns column names
	 * @return A {@link RowMapper} that will extract those columns into a name/value
	 *         map
	 */
	public static RowMapper<Map<String, Object>> mapFrom( String... columns ) {
		return new RowMapper<Map<String, Object>>() {

			@Override
			public Map<String, Object> mapRow( ResultSet rs, int rowNum ) throws SQLException {
				Map<String, Object> m = new TreeMap<>();
				for( String column : columns ) {
					m.put( column, rs.getObject( column ) );
				}
				return m;
			}
		};
	}

	/**
	 * @param <T> Extracted type
	 * @param rm  how to map each row to an object
	 * @return An extractor to build a per-row list of results
	 */
	public static <T> ResultSetExtractor<List<T>> listOf( final RowMapper<T> rm ) {
		return new RowMappingExtractor<>( rm );
	}

	/**
	 * Extracts a single row's value
	 *
	 * @param <T> the extracted type
	 * @param rm  How to map a row into a value
	 * @return How to extract the first single row from a {@link ResultSet}
	 */
	public static <T> ResultSetExtractor<T> one( RowMapper<T> rm ) {
		return rs -> {
			if( rs.next() ) {
				return rm.mapRow( rs, 0 );
			}
			return null;
		};
	}

	/**
	 * Extracts a value from each row
	 *
	 * @param <T> The extracted type
	 */
	public static class RowMappingExtractor<T> implements ResultSetExtractor<List<T>> {

		private final RowMapper<T> mapper;

		/**
		 * @param mapper How to extract a value from a row
		 */
		public RowMappingExtractor( RowMapper<T> mapper ) {
			this.mapper = mapper;
		}

		@Override
		public List<T> extractData( ResultSet rs ) throws SQLException {
			List<T> results = new ArrayList<>();

			while( rs.next() && !mapper.stop ) {
				results.add( mapper.mapRow( rs, results.size() ) );
			}

			return results;
		}
	}
}
