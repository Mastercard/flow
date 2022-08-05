package com.mastercard.test.flow.example.app.assrt.rsd;

import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.RNG;
import static java.util.stream.Collectors.joining;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.assrt.Assertion;
import com.mastercard.test.flow.assrt.Checker;
import com.mastercard.test.flow.example.app.model.rsd.DBItems;
import com.mastercard.test.flow.example.app.model.rsd.DBItems.Update;
import com.mastercard.test.flow.msg.txt.Text;

/**
 * Queries the store service's DB to confirm that the expected DB updates
 * occurred
 */
public class DBItemsChecker extends Checker<DBItems> {

	private static final Logger LOG = LoggerFactory.getLogger( DBItemsChecker.class );

	private static JdbcDataSource ds;

	private final Map<DBItems, Map<String, String>> preProcessingItems = new HashMap<>();

	/***/
	public DBItemsChecker() {
		super( DBItems.class );
	}

	private static DataSource ds() {
		if( ds == null ) {
			String path = System.getProperty( "db", "target/db" );
			ds = new JdbcDataSource();
			ds.setURL( "jdbc:h2:./" + path + ";mode=MySQL" );
			ds.setUser( "sa" );
			ds.setPassword( "sa" );
		}
		return ds;
	}

	private static Map<String, String> dumpItems() {
		try( Connection c = ds().getConnection();
				PreparedStatement ps = c.prepareStatement( "SELECT id, data FROM item" );
				ResultSet rs = ps.executeQuery() ) {
			Map<String, String> items = new TreeMap<>();
			while( rs.next() ) {
				items.put( rs.getString( "id" ), rs.getString( "data" ) );
			}
			LOG.info( "Dumped {}", items );
			return items;
		}
		catch( SQLException e ) {
			throw new IllegalStateException( "Failed to dump items", e );
		}
	}

	@Override
	public Message expected( DBItems residue ) {
		preProcessingItems.put( residue, dumpItems() );
		return format( residue.updates() );
	}

	@Override
	public byte[] actual( DBItems residue, List<Assertion> behaviour ) {
		Map<String, String> before = Optional.ofNullable( preProcessingItems.remove( residue ) )
				.orElseThrow( () -> new IllegalStateException(
						"Missing items dump from before flow processing" ) );
		Map<String, String> after = dumpItems();

		Map<String, Update> updates = new TreeMap<>();

		{
			Set<String> added = new TreeSet<>( after.keySet() );
			added.removeAll( before.keySet() );
			added.forEach( id -> updates.put( id, new Update( null, after.get( id ) ) ) );
		}
		{
			Set<String> removed = new TreeSet<>( before.keySet() );
			removed.removeAll( after.keySet() );
			removed.forEach( id -> updates.put( id, new Update( before.get( id ), null ) ) );
		}
		{
			Set<String> retained = new TreeSet<>( before.keySet() );
			retained.retainAll( after.keySet() );
			retained.forEach( id -> {
				String oldValue = before.get( id );
				String newValue = after.get( id );
				if( !newValue.equals( oldValue ) ) {
					updates.put( id, new Update( oldValue, newValue ) );
				}
			} );
		}

		Message msg = format( updates );
		return msg.content();
	}

	private static Message format( Map<String, DBItems.Update> updates ) {
		return new Text( updates.entrySet().stream()
				.sorted( ( a, b ) -> {
					int d = String.valueOf( a.getValue().before )
							.compareTo( String.valueOf( b.getValue().before ) );
					if( d == 0 ) {
						d = String.valueOf( a.getValue().after )
								.compareTo( String.valueOf( b.getValue().after ) );
					}
					if( d == 0 ) {
						d = String.valueOf( a.getKey() )
								.compareTo( String.valueOf( b.getKey() ) );
					}
					return d;
				} )
				.map( e -> String.format( "%s : %s -> %s",
						e.getKey(), e.getValue().before, e.getValue().after ) )
				.collect( joining( "\n" ) ) )
						.masking( RNG, m -> m
								.replace( "^.* :", "_uuid_ :" ) );
	}

}
