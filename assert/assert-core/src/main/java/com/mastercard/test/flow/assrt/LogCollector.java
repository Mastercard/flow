package com.mastercard.test.flow.assrt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Routing is guarded by this object's monitor; source calls (open/flush/close)
 * are serialised on a separate lock so that a source may deliver events via
 * {@link #accept} while one of its flushes is in progress.
 */
final class LogCollector implements LogCapture, Collector {

	/** Binding for identifiers claimed by more than one execution */
	private static final Object AMBIGUOUS = new Object();

	private final CorrelatedCapture source;
	private final Object sourceLock = new Object();
	private boolean opened;
	private boolean closed;

	/** Routing by identifier: a {@link Buffer}, or ambiguous */
	private final Map<String, Object> bindings = new LinkedHashMap<>();
	private final Map<Flow, Buffer> buffers = new IdentityHashMap<>();

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
			return Outcome.CLOSED;
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
		return Outcome.UNATTRIBUTED;
	}

	/**
	 * Final cut: flushes and closes the source. Ordinary source faults are
	 * swallowed after the source is closed; anything else propagates. Repeated
	 * calls do nothing.
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
