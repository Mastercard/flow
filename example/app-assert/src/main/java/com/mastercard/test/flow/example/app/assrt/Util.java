package com.mastercard.test.flow.example.app.assrt;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.mastercard.test.flow.assrt.log.Tail;

/**
 * Collection of utilities for testing the services
 */
public class Util {

	/**
	 * The log file location
	 */
	public static final Path LOG_FILE_PATH = Paths.get( "target/test_log.txt" );

	private Util() {
		// no instances
	}

	/**
	 * Extracts content from the log format produced by
	 * src/main/resources/simplelogger.properties
	 */
	public static final Tail LOG_CAPTURE = new Tail( LOG_FILE_PATH,
			"(?<time>\\S+) \\[[^\\]]+\\] (?<level>[A-Z]+) (?<source>\\S+)" );

}
