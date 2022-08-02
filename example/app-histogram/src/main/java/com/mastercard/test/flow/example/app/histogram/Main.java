package com.mastercard.test.flow.example.app.histogram;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Application entrypoint
 */
@Command(name = "histogram",
		mixinStandardHelpOptions = true,
		version = "0.1",
		description = "Launches an instance of the histogram service")
public class Main extends com.mastercard.test.flow.example.app.Main {

	/***/
	public Main() {
		super( HistogramImp::new );
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
