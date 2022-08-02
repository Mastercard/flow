package com.mastercard.test.flow.assrt.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

	private List<LogCapture> sources = new ArrayList<>();

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
		sources.forEach( s -> s.start( flow ) );
	}

	@Override
	public Stream<LogEvent> end( Flow flow ) {
		List<LogEvent> combined = new ArrayList<>();
		sources.forEach( s -> s.end( flow ).forEach( combined::add ) );
		combined.sort( Comparator.comparing( e -> e.time ) );
		return combined.stream();
	}
}
