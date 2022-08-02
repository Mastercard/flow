package com.mastercard.test.flow.example.app.core;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Application entrypoint
 */
@Command(name = "core",
		mixinStandardHelpOptions = true,
		version = "0.1",
		description = "Launches an instance of the core service")
public class Main extends com.mastercard.test.flow.example.app.Main {

	/***/
	public Main() {
		super( CoreImp::new );
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
