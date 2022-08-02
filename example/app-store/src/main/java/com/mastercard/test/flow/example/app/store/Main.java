package com.mastercard.test.flow.example.app.store;

import javax.sql.DataSource;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Application entrypoint
 */
@Command(name = "histogram",
		mixinStandardHelpOptions = true,
		version = "0.1",
		description = "Launches an instance of the store service")
public class Main extends com.mastercard.test.flow.example.app.Main {

	/**
	 * Uses an actual database
	 */
	public Main() {
		this( StoreDatabase.connect() );
	}

	/**
	 * @param db How to connect to the database
	 */
	public Main( DataSource db ) {
		super( () -> new StoreImp( db ) );
	}

	/**
	 * Starts the service
	 *
	 * @param args command line args
	 */
	public static void main( String[] args ) {
		new CommandLine( new Main() ).execute( args );
	}
}
