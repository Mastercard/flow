package com.mastercard.test.flow.assrt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the runner's diagnostic log records for the duration of a test
 */
public final class Diagnostics extends Handler implements AutoCloseable {

	private final Logger logger;
	private final Level previous;
	private final List<String> messages = new CopyOnWriteArrayList<>();

	/**
	 * Captures at the logger's current level
	 *
	 * @param source The class whose diagnostics should be captured
	 */
	public Diagnostics( Class<?> source ) {
		this( source, null );
	}

	/**
	 * @param source The class whose diagnostics should be captured
	 * @param level  The level to capture at, or <code>null</code> to leave the
	 *               logger's level alone
	 */
	public Diagnostics( Class<?> source, Level level ) {
		logger = Logger.getLogger( source.getName() );
		previous = logger.getLevel();
		if( level != null ) {
			logger.setLevel( level );
			setLevel( level );
		}
		logger.addHandler( this );
	}

	/**
	 * @return The captured messages, in the order they were logged
	 */
	public List<String> messages() {
		return messages;
	}

	@Override
	public void publish( LogRecord logged ) {
		messages.add( logged.getMessage() );
	}

	@Override
	public void flush() {
		// nothing buffered
	}

	@Override
	public void close() {
		logger.removeHandler( this );
		logger.setLevel( previous );
	}
}
