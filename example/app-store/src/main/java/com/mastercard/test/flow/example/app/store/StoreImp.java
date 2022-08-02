package com.mastercard.test.flow.example.app.store;

import static com.mastercard.test.flow.example.app.store.DB.one;
import static com.mastercard.test.flow.example.app.store.DB.stringColumn;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.app.Queue;
import com.mastercard.test.flow.example.app.Store;

/**
 * Implementation of the {@link Queue} service
 */
public class StoreImp implements Store {

	private static final Logger LOG = LoggerFactory.getLogger( StoreImp.class );

	private final DataSource db;

	/**
	 * @param db How to connect to the DB
	 */
	public StoreImp( DataSource db ) {
		this.db = db;
	}

	@Override
	public void store( String key, String data ) {
		LOG.info( "Storing {} to {}", data, key );
		DB.update( db, "INSERT INTO item ( id, data ) VALUES ( ?, ? ) "
				+ "ON DUPLICATE KEY UPDATE data = ?",
				key, data, data );
	}

	@Override
	public String retrieve( String key ) {
		LOG.info( "Retrieving {}", key );
		String value = DB.query( db, "SELECT data FROM item WHERE id = ?" )
				.bind( key )
				.extract( one( stringColumn( "data" ) ) );
		LOG.info( "Retrieved {}", value );
		return value;
	}

	@Override
	public String delete( String key ) {
		LOG.info( "Deleting {}", key );
		String value = retrieve( key );
		DB.update( db, "DELETE FROM item WHERE id = ?", key );
		return value;
	}

	@Override
	public void clear() {
		LOG.info( "Clearing DB" );
		DB.update( db, "TRUNCATE item" );
	}

}
