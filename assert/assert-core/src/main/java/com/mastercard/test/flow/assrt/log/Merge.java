package com.mastercard.test.flow.assrt.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Multiplexes multiple {@link LogCapture} instances into one stream of events.
 * It is strongly recommended that all merged {@link LogCapture} instance
 * produce events where the {@link LogEvent#time} values can be meaningfully
 * compared.
 */
public class Merge implements LogCapture {

	private final List<LogCapture> sources = new ArrayList<>();
	private final Map<Flow, List<LogCapture>> active = new IdentityHashMap<>();

	/**
	 * Adds a source of {@link LogEvent}s.
	 *
	 * @param source The source of events
	 * @return <code>this</code>
	 */
	public Merge with( LogCapture... source ) {
		Collections.addAll( sources, source );
		return this;
	}

	@Override
	public void start( Flow flow ) {
		if( active.containsKey( flow ) ) {
			throw new IllegalStateException( "Capture already active for flow" );
		}
		List<LogCapture> begun = new ArrayList<>();
		active.put( flow, begun );
		try {
			for( LogCapture source : new ArrayList<>( sources ) ) {
				source.start( flow );
				begun.add( source );
			}
		}
		catch( RuntimeException | Error primary ) {
			// The outer owner cannot end a start that threw. Roll back only children
			// that returned successfully, preserving the original failure.
			try( Stream<LogEvent> ignored = end( flow ) ) {
				// end has already materialized and closed the child streams.
			}
			catch( RuntimeException | Error cleanup ) {
				if( primary != cleanup ) {
					primary.addSuppressed( cleanup );
				}
			}
			throw primary;
		}
	}

	@Override
	public Stream<LogEvent> end( Flow flow ) {
		List<LogEvent> combined = new ArrayList<>();
		List<LogCapture> begun = active.remove( flow );
		Throwable failure = null;
		if( begun != null ) {
			for( LogCapture source : begun ) {
				try( Stream<LogEvent> events = source.end( flow ) ) {
					events.forEach( combined::add );
				}
				catch( RuntimeException | Error e ) {
					if( failure == null ) {
						failure = e;
					}
					else if( failure != e ) {
						failure.addSuppressed( e );
					}
				}
			}
		}
		if( failure instanceof Error error ) {
			throw error;
		}
		if( failure instanceof RuntimeException runtime ) {
			throw runtime;
		}
		combined.sort( Comparator.comparing( e -> e.time ) );
		return combined.stream();
	}
}
