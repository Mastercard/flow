package com.mastercard.test.flow.assrt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 * Routing is guarded by this object's monitor; source calls (open/flush/close)
 * are serialised on a separate lock so that a source may deliver events via
 * {@link #accept} while one of its flushes is in progress.
 * <p>
 * Events that reach no flow are counted and the first few retained for the
 * completion diagnostic; every one is also traced at {@link Level#FINE} on this
 * class's logger, for when the summary is not enough to diagnose a pattern or
 * identifier mismatch.
 */
final class LogCollector implements LogCapture, Collector {

	/** Binding for identifiers claimed by more than one execution */
	private static final Object AMBIGUOUS = new Object();
	/** Unrouted events quoted in the completion diagnostic */
	private static final int SAMPLES = 5;
	/** Longest quoted message excerpt: messages may be multi-line stack traces */
	private static final int EXCERPT = 200;
	private static final Logger UNROUTED = Logger.getLogger( LogCollector.class.getName() );

	private final CorrelatedCapture source;
	private final Object sourceLock = new Object();
	private boolean opened;
	private boolean closed;

	/** Routing by identifier: a {@link Buffer}, or ambiguous */
	private final Map<String, Object> bindings = new LinkedHashMap<>();
	private final Map<Flow, Buffer> buffers = new IdentityHashMap<>();
	/** Events that reached no flow, by cause */
	private int unattributed;
	private int ambiguousCount;
	private int refused;
	private final List<String> sampledEvents = new ArrayList<>();

	/**
	 * Events for one execution. After the execution ends its snapshot is frozen;
	 * events that still carry its identifier accumulate as late evidence.
	 */
	private static final class Buffer {
		final List<LogEvent> events = new ArrayList<>();
		final List<LogEvent> late = new ArrayList<>();
		boolean ended;
	}

	/** @param source The configured source */
	LogCollector( CorrelatedCapture source ) {
		this.source = source;
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
			buffers.put( flow, new Buffer() );
		}
	}

	/**
	 * @param flow The executing flow
	 * @param id   An identifier that events for this execution will carry
	 */
	synchronized void bind( Flow flow, String id ) {
		Buffer buffer = buffers.get( flow );
		if( buffer == null || buffer.ended || id == null ) {
			return;
		}
		Object existing = bindings.get( id );
		if( existing == null ) {
			bindings.put( id, buffer );
		}
		else if( existing != buffer ) {
			// two executions claim the same identifier, so its events belong to neither
			bindings.put( id, AMBIGUOUS );
		}
	}

	/**
	 * Flushes the source, then freezes this execution's snapshot; events for it
	 * that arrive afterwards are kept as late evidence. A flush failure is thrown
	 * when the returned stream is closed, after the captured events have been read.
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
			Buffer buffer = buffers.get( flow );
			if( buffer == null ) {
				snapshot = Collections.emptyList();
			}
			else {
				buffer.ended = true;
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
			refused++;
			return unrouted( "after close", correlation, event, Outcome.CLOSED );
		}
		Object binding = correlation == null ? null : bindings.get( correlation );
		if( binding instanceof Buffer buffer ) {
			if( buffer.ended ) {
				buffer.late.add( event );
				return Outcome.LATE;
			}
			buffer.events.add( event );
			return Outcome.ACCEPTED;
		}
		if( binding == AMBIGUOUS ) {
			ambiguousCount++;
			return unrouted( "ambiguous", correlation, event, Outcome.UNATTRIBUTED );
		}
		unattributed++;
		return unrouted( correlation == null ? "no identifier" : "unknown identifier",
				correlation, event, Outcome.UNATTRIBUTED );
	}

	private Outcome unrouted( String cause, String correlation, LogEvent event, Outcome outcome ) {
		// most unrouted events are neither quoted nor traced, so describe only those
		if( sampledEvents.size() < SAMPLES || UNROUTED.isLoggable( Level.FINE ) ) {
			String description = String.format( "%s [%s] %s %s %s %s",
					cause, correlation, event.time, event.level, event.source,
					excerpt( String.valueOf( event.message ) ) );
			if( sampledEvents.size() < SAMPLES ) {
				sampledEvents.add( description );
			}
			UNROUTED.fine( () -> "Unrouted event: " + description );
		}
		return outcome;
	}

	private static String excerpt( String message ) {
		int end = message.indexOf( '\n' );
		if( end < 0 || end > EXCERPT ) {
			end = Math.min( message.length(), EXCERPT );
		}
		return end == message.length() ? message : message.substring( 0, end ) + "...";
	}

	/**
	 * Drains the record of events that reached no flow. A repeated call describes
	 * only the events that arrived since the previous one.
	 *
	 * @return A description of the events that reached no flow, or
	 *         <code>null</code> if every event was attributed
	 */
	synchronized String unrouted() {
		if( unattributed == 0 && ambiguousCount == 0 && refused == 0 ) {
			return null;
		}
		StringBuilder summary = new StringBuilder( String.format(
				"Correlated capture attributed no flow to %d events: "
						+ "%d without a known identifier, %d with an identifier claimed by more than one flow, "
						+ "%d delivered after the run closed. First %d:",
				unattributed + ambiguousCount + refused, unattributed, ambiguousCount, refused,
				sampledEvents.size() ) );
		sampledEvents.forEach( sample -> summary.append( "\n  " ).append( sample ) );
		summary.append( "\nEnable FINE logging on " ).append( UNROUTED.getName() )
				.append( " to see every unrouted event" );
		unattributed = 0;
		ambiguousCount = 0;
		refused = 0;
		sampledEvents.clear();
		return summary.toString();
	}

	/**
	 * Final cut: flushes and closes the source. Events that the source delivers
	 * while closing are routed as usual; only those arriving after it has closed
	 * are refused. Ordinary source faults are swallowed after the source is closed;
	 * anything else propagates. Repeated calls do nothing.
	 *
	 * @param ordinary Whether a fault may be swallowed rather than thrown
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
				finally {
					synchronized( this ) {
						closed = true;
					}
				}
			}
			if( failure != null && !ordinary.test( failure ) ) {
				throw failure;
			}
		}
	}

	/**
	 * Drains events that arrived for executions after they had ended, so the report
	 * can be completed with them.
	 *
	 * @return Late events by flow, in arrival order
	 */
	synchronized Map<Flow, List<LogEvent>> late() {
		Map<Flow, List<LogEvent>> drained = new IdentityHashMap<>();
		buffers.forEach( ( flow, buffer ) -> {
			if( !buffer.late.isEmpty() ) {
				drained.put( flow, List.copyOf( buffer.late ) );
				buffer.late.clear();
			}
		} );
		return drained;
	}
}
