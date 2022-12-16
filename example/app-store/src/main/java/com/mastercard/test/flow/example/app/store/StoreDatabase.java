package com.mastercard.test.flow.example.app.store;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

/**
 * Creates the DB connection and table schema
 */
public class StoreDatabase {

	private StoreDatabase() {
		// no instances
	}

	private static final String TABLE_SCHEMA = ""
			+ "CREATE TABLE IF NOT EXISTS item ("
			+ " id VARCHAR(255) PRIMARY KEY,"
			+ " data TEXT,"
			+ " hash BINARY VARYING"
			+ ");";

	/**
	 * @return A source of connections to the DB
	 */
	public static DataSource connect() {
		String path = System.getProperty( "db", "target/db" );
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL( "jdbc:h2:./" + path + ";mode=MySQL" );
		ds.setUser( "sa" );
		ds.setPassword( "sa" );

		DB.update( ds, TABLE_SCHEMA );

		return ds;
	}
}
