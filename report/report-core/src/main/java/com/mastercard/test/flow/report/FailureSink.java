package com.mastercard.test.flow.report;

/**
 * Functional interface to a logging framework
 */
@FunctionalInterface
public interface FailureSink {

	/**
	 * Consumes a logging event
	 *
	 * @param msg    The log message
	 * @param params Log message parameters
	 */
	void log( String msg, Object... params );

	/**
	 * A sink that does nothing
	 */
	FailureSink SILENT = ( msg, params ) -> {
		// hush!
	};
}
