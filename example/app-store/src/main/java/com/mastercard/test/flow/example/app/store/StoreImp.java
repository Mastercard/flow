package com.mastercard.test.flow.example.app.store;

import static com.mastercard.test.flow.example.app.store.DB.one;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

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
		LOG.info( "Storing '{}' to '{}'", data, key );
		byte[] hash;
		try {
			hash = MessageDigest.getInstance( "MD5" ).digest( data.getBytes( UTF_8 ) );
		}
		catch( NoSuchAlgorithmException e ) {
			throw new IllegalStateException( e );
		}
		DB.update( db, "INSERT INTO item ( id, data, hash ) VALUES ( ?, ?, ? ) "
				+ "ON DUPLICATE KEY UPDATE data = ?, hash = ?",
				key, data, hash, data, hash );
	}

	@Override
	public String retrieve( String key ) {
		LOG.info( "Retrieving {}", key );
		Map<String, Object> m = DB.query( db, "SELECT data, hash FROM item WHERE id = ?" )
				.bind( key )
				.extract( one( DB.mapFrom( "data", "hash" ) ) );
		LOG.info( "Retrieved {}", DB.bytesToText( m ) );
		String data = String.valueOf( m.get( "data" ) );
		byte[] hash;
		try {
			hash = MessageDigest.getInstance( "MD5" ).digest( data.getBytes( UTF_8 ) );
		}
		catch( NoSuchAlgorithmException e ) {
			throw new IllegalStateException( e );
		}

		if( !Arrays.equals( hash, (byte[]) m.get( "hash" ) ) ) {
			throw new IllegalStateException( "Data integrity failure!" );
		}

		return data;
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
