package com.mastercard.test.flow.assrt;

import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Interface for objects that can provide logging data
 */
public interface LogCapture {

	/**
	 * Called immediately before a {@link Flow} is exercised
	 *
	 * @param flow The flow being exercised
	 */
	void start( Flow flow );

	/**
	 * Called immediately after a {@link Flow} is exercised
	 *
	 * @param flow The flow being exercised
	 * @return The events that have happened since {@link #start(Flow)} was called,
	 *         in chronological order
	 */
	Stream<LogEvent> end( Flow flow );

	/**
	 * Captures no logs
	 */
	LogCapture NO_OP = new LogCapture() {

		@Override
		public void start( Flow flow ) {
			// no-op
		}

		@Override
		public Stream<LogEvent> end( Flow flow ) {
			return Stream.empty();
		}
	};
}
