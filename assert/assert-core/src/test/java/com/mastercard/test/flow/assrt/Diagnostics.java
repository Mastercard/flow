package com.mastercard.test.flow.assrt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the runner's diagnostic log records for the duration of a test
 */
public final class Diagnostics extends Handler implements AutoCloseable {

	private final Logger logger;
	private final List<String> messages = new CopyOnWriteArrayList<>();

	/**
	 * @param source The class whose diagnostics should be captured
	 */
	public Diagnostics( Class<?> source ) {
		logger = Logger.getLogger( source.getName() );
		logger.addHandler( this );
	}

	/**
	 * @return The captured messages, in the order they were logged
	 */
	public List<String> messages() {
		return messages;
	}

	@Override
	public void publish( LogRecord record ) {
		messages.add( record.getMessage() );
	}

	@Override
	public void flush() {
		// nothing buffered
	}

	@Override
	public void close() {
		logger.removeHandler( this );
	}
}
