package com.mastercard.test.flow.assrt;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * A source of log events that are attributed to {@link Flow} executions by a
 * correlation identifier carried in the events themselves, rather than by the
 * time interval in which they were observed. This is the capture mechanism that
 * remains truthful when flows execute concurrently.
 * <p>
 * The identifier is supplied to the test through
 * {@link Assertion#correlation()}; the test puts it on the request (typically
 * as a header) and the system under test propagates it into its logging
 * context. Sources push each event to the run-owned {@link Collector} with the
 * identifier they observed. Attribution is exact or absent - never guessed from
 * thread names, timestamps or file offsets.
 * <p>
 * One instance is opened once per test run and closed at completion. All
 * methods are invoked by the runner; {@link Collector#accept} may be called
 * from any thread while the run is open.
 */
public interface CorrelatedCapture {

	/**
	 * Receives correlated events. Implemented by the runner; acceptance is
	 * serialised against flow completion so that each event belongs either to a
	 * flow's frozen snapshot or to the run's late diagnostics, never both.
	 */
	interface Collector {
		/**
		 * @param correlation The identifier carried by the event, or <code>null</code>
		 *                    when the source could not extract one
		 * @param event       The event
		 * @return How the event was routed
		 */
		Outcome accept( String correlation, LogEvent event );
	}

	/**
	 * How the collector routed an event
	 */
	enum Outcome {
		/** Retained in the snapshot of the currently-executing flow it identifies */
		ACCEPTED,
		/**
		 * Identifies a flow whose execution has already ended. Counted against that
		 * flow in the run diagnostics, not added to any flow's snapshot
		 */
		LATE,
		/** Identifier absent, unknown to this run, or bound to more than one flow */
		UNATTRIBUTED,
		/** Would exceed a finite per-flow or run budget; counted, payload dropped */
		OMITTED,
		/** The run has been closed; nothing is retained */
		CLOSED
	}

	/**
	 * Called once when the run begins capturing. The source may deliver events from
	 * this point until {@link #close()} returns.
	 *
	 * @param collector The destination for events during this run
	 */
	void open( Collector collector );

	/**
	 * Called when a flow's execution ends, before its snapshot is frozen, and again
	 * at run completion. Sources that must be polled (e.g. a file) deliver
	 * everything that exists up to their current boundary; sources that push events
	 * as they happen need do nothing. Work here must be bounded.
	 */
	default void flush() {
		// push sources have nothing to do
	}

	/**
	 * Called once at run completion, after the final {@link #flush()}. The source
	 * must not deliver events after this returns; the collector will refuse them.
	 */
	void close();

	/**
	 * Called after {@link #close()} to describe source-side problems (unreadable or
	 * truncated files, bounded reads) for the run's report diagnostics.
	 *
	 * @return Bounded human-readable lines, or empty when nothing went wrong
	 */
	default String summary() {
		return "";
	}
}
