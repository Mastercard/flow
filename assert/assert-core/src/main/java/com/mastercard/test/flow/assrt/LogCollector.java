package com.mastercard.test.flow.assrt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.CorrelatedCapture.Collector;
import com.mastercard.test.flow.assrt.CorrelatedCapture.Outcome;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Run-owned routing of correlated log events to flow executions. Presents the
 * {@link LogCapture} shape to the processor's per-invocation capture so that
 * the existing begin/end balancing applies unchanged.
 * <p>
 * Bookkeeping is guarded by this object's monitor and never performs IO or
 * calls the source. Source calls (open/flush/close) are serialised separately
 * so that a polled source is never read by two workers at once; a source may
 * call {@link #accept} while its own flush is in progress.
 */
final class LogCollector implements LogCapture, Collector {

	/** The most late/unattributed events kept verbatim for the run diagnostics. */
	static final int RETAINED_DIAGNOSTICS = 100;
	private static final String ELLIPSIS = "...";
	/** Binding for identifiers claimed by more than one execution */
	private static final Object AMBIGUOUS = new Object();

	private final CorrelatedCapture source;
	private final CaptureBudget budget;
	private final String logSource;
	private final Object sourceLock = new Object();
	private boolean opened;
	private boolean closed;

	/**
	 * Routing by identifier: an open {@link Buffer}, a frozen origin label, or
	 * ambiguous
	 */
	private final Map<String, Object> bindings = new LinkedHashMap<>();
	private final Map<Flow, Buffer> open = new IdentityHashMap<>();
	private final TreeSet<String> ambiguous = new TreeSet<>();
	private final Map<String, Integer> late = new LinkedHashMap<>();
	private final List<String> diagnostics = new ArrayList<>();
	private final List<String> faults = new ArrayList<>();
	/** Retained in flow snapshots */
	private int retainedEvents;
	private long retainedBytes;
	/** Retained verbatim in {@link #diagnostics}; shares the run budget */
	private long diagnosticBytes;
	private int unattributed;
	private int omittedEvents;
	private long omittedBytes;

	private static final class Buffer {
		final String label;
		final List<LogEvent> events = new ArrayList<>();
		final List<String> ids = new ArrayList<>();
		long bytes;
		int omitted;
		long omittedBytes;
		String omissionReason;

		Buffer( String label ) {
			this.label = label;
		}
	}

	/**
	 * @param source    The configured source
	 * @param budget    Retention limits
	 * @param logSource Source name for runner-generated events
	 */
	LogCollector( CorrelatedCapture source, CaptureBudget budget, String logSource ) {
		this.source = source;
		this.budget = budget;
		this.logSource = logSource;
	}

	/** Opens the source on first use and opens this execution's buffer. */
	@Override
	public void start( Flow flow ) {
		synchronized( sourceLock ) {
			if( !opened ) {
				opened = true;
				source.open( this );
			}
		}
		synchronized( this ) {
			open.put( flow, new Buffer( flow.meta().id() ) );
		}
	}

	/**
	 * @param flow The executing flow
	 * @param id   An identifier that events for this execution will carry
	 */
	synchronized void bind( Flow flow, String id ) {
		Buffer buffer = open.get( flow );
		if( buffer == null || id == null ) {
			return;
		}
		Object existing = bindings.get( id );
		if( existing == null ) {
			bindings.put( id, buffer );
			buffer.ids.add( id );
		}
		else if( existing != buffer ) {
			// Two executions in this run claim the same identifier. Neither can be
			// trusted with its events, and old events must never move to new work.
			bindings.put( id, AMBIGUOUS );
			ambiguous.add( id );
		}
	}

	/**
	 * Flushes the source, then freezes this execution's snapshot; events for it
	 * that arrive afterwards are late diagnostics. A source flush failure is
	 * surfaced when the returned stream is closed, so the caller still materialises
	 * the events that were captured.
	 */
	@Override
	public Stream<LogEvent> end( Flow flow ) {
		RuntimeException flushFailure = null;
		try {
			flush();
		}
		catch( RuntimeException e ) {
			flushFailure = e;
		}
		List<LogEvent> snapshot;
		synchronized( this ) {
			Buffer buffer = open.remove( flow );
			if( buffer == null ) {
				snapshot = Collections.emptyList();
			}
			else {
				buffer.ids.forEach( id -> bindings.replace( id, buffer, buffer.label ) );
				if( buffer.omitted > 0 ) {
					buffer.events.add( new LogEvent( Instant.now(), "WARN", logSource,
							String.format( "Log capture omitted %s (%d bytes) beyond the %s",
									plural( buffer.omitted, "event" ), buffer.omittedBytes,
									buffer.omissionReason ) ) );
				}
				snapshot = buffer.events;
			}
		}
		RuntimeException deferred = flushFailure;
		return deferred == null ? snapshot.stream() : snapshot.stream().onClose( () -> {
			throw deferred;
		} );
	}

	private void flush() {
		synchronized( sourceLock ) {
			if( opened && !closed ) {
				source.flush();
			}
		}
	}

	@Override
	public synchronized Outcome accept( String correlation, LogEvent event ) {
		if( closed ) {
			return Outcome.CLOSED;
		}
		Object binding = correlation == null ? null : bindings.get( correlation );
		if( binding instanceof Buffer buffer ) {
			return retain( buffer, event );
		}
		if( binding instanceof String origin ) {
			late.merge( origin, 1, Integer::sum );
			diagnose( "late " + origin, event );
			return Outcome.LATE;
		}
		unattributed++;
		diagnose( correlation == null ? "unattributed" : "unattributed " + correlation, event );
		return Outcome.UNATTRIBUTED;
	}

	private Outcome retain( Buffer buffer, LogEvent event ) {
		int size = event.message.length();
		int retainedSize = Math.min( size, budget.recordBytes() );
		String exceeded = null;
		if( buffer.events.size() >= budget.flowEvents()
				|| buffer.bytes + retainedSize > budget.flowBytes() ) {
			exceeded = "flow budget";
		}
		else if( runEvents() >= budget.runEvents() || runBytes() + retainedSize > budget.runBytes() ) {
			exceeded = "run budget";
		}
		if( exceeded != null ) {
			buffer.omitted++;
			buffer.omittedBytes += size;
			buffer.omissionReason = exceeded;
			omittedEvents++;
			omittedBytes += size;
			return Outcome.OMITTED;
		}
		if( size > budget.recordBytes() ) {
			event = new LogEvent( event.time, event.level, event.source,
					event.message.substring( 0, Math.max( 0, budget.recordBytes() - ELLIPSIS.length() ) )
							+ ELLIPSIS );
		}
		buffer.events.add( event );
		buffer.bytes += retainedSize;
		retainedEvents++;
		retainedBytes += retainedSize;
		return Outcome.ACCEPTED;
	}

	/** Verbatim late/unattributed evidence shares the run's retention budget. */
	private void diagnose( String label, LogEvent event ) {
		int size = event.message.length();
		if( diagnostics.size() < RETAINED_DIAGNOSTICS
				&& runEvents() < budget.runEvents() && runBytes() + size <= budget.runBytes() ) {
			diagnostics.add( label + ": " + event );
			diagnosticBytes += size;
		}
	}

	private int runEvents() {
		return retainedEvents + diagnostics.size();
	}

	private long runBytes() {
		return retainedBytes + diagnosticBytes;
	}

	/**
	 * Final cut: flushes and closes the source. Ordinary source faults are recorded
	 * for the summary; anything else propagates after the source is closed.
	 * Repeated calls do nothing.
	 *
	 * @param ordinary Whether a fault may be recorded rather than thrown
	 */
	void close( Predicate<Throwable> ordinary ) {
		synchronized( sourceLock ) {
			if( !opened || closed ) {
				return;
			}
			RuntimeException failure = null;
			try {
				source.flush();
			}
			catch( RuntimeException e ) {
				failure = e;
			}
			finally {
				synchronized( this ) {
					closed = true;
				}
				try {
					source.close();
				}
				catch( RuntimeException e ) {
					if( failure == null ) {
						failure = e;
					}
					else {
						failure.addSuppressed( e );
					}
				}
			}
			if( failure != null ) {
				if( !ordinary.test( failure ) ) {
					throw failure;
				}
				synchronized( this ) {
					faults.add( "close failed: " + failure.getClass().getName() );
				}
			}
			String sourceSummary;
			try {
				sourceSummary = source.summary();
			}
			catch( RuntimeException e ) {
				sourceSummary = "summary failed: " + e.getClass().getName();
			}
			if( !sourceSummary.isEmpty() ) {
				synchronized( this ) {
					faults.add( sourceSummary.strip().replace( "\n", "\nCapture fault: " ) );
				}
			}
		}
	}

	/** @return Bounded run-level capture accounting for the report diagnostics */
	synchronized String summary() {
		StringBuilder sb = new StringBuilder();
		sb.append( String.format( "Capture: %s retained (%d bytes)%n",
				plural( retainedEvents, "event" ), retainedBytes ) );
		if( !late.isEmpty() ) {
			StringBuilder origins = new StringBuilder();
			late.forEach( ( origin, count ) -> origins.append( origins.length() == 0 ? "" : ", " )
					.append( origin ).append( " (" ).append( count ).append( ")" ) );
			sb.append( String.format( "Capture late: %s: %s%n",
					plural( late.values().stream().mapToInt( Integer::intValue ).sum(), "late event" ),
					origins ) );
		}
		if( unattributed > 0 ) {
			sb.append( String.format( "Capture unattributed: %s%n",
					plural( unattributed, "unattributed event" ) ) );
		}
		if( !ambiguous.isEmpty() ) {
			sb.append( String.format( "Capture ambiguous: %s: %s%n",
					plural( ambiguous.size(), "ambiguous identifier" ), String.join( ", ", ambiguous ) ) );
		}
		if( omittedEvents > 0 ) {
			sb.append( String.format( "Capture omitted: %s (%d bytes)%n",
					plural( omittedEvents, "omitted event" ), omittedBytes ) );
		}
		if( !open.isEmpty() ) {
			sb.append( String.format( "Capture unbalanced: %s never ended%n",
					plural( open.size(), "execution" ) ) );
		}
		faults.forEach( fault -> sb.append( "Capture fault: " ).append( fault ).append( '\n' ) );
		if( !diagnostics.isEmpty() ) {
			sb.append( String.format( "Capture diagnostics (first %d):%n", RETAINED_DIAGNOSTICS ) );
			diagnostics.forEach( d -> sb.append( "  " ).append( d ).append( '\n' ) );
		}
		return sb.toString();
	}

	private static String plural( int count, String noun ) {
		return count + " " + noun + (count == 1 ? "" : "s");
	}
}
