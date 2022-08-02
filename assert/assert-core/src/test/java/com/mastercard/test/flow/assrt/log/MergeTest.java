package com.mastercard.test.flow.assrt.log;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Exercises {@link Merge}
 */
@SuppressWarnings("static-method")
class MergeTest {

	/**
	 * No source captures
	 */
	@Test
	void empty() {
		Merge merge = new Merge();

		Flow flw = new Flw( "flw []" );

		merge.start( flw );
		long events = merge.end( flw ).count();

		assertEquals( 0, events );
	}

	/**
	 * Show that start and end calls are delegated
	 */
	@Test
	void delegate() {

		StringBuilder log = new StringBuilder();

		LogCapture lc = new LogCapture() {

			@Override
			public void start( Flow flow ) {
				log.append( "start " ).append( flow.meta().description() ).append( "\n" );
			}

			@Override
			public Stream<LogEvent> end( Flow flow ) {
				log.append( "end " ).append( flow.meta().description() ).append( "\n" );
				return Stream.empty();
			}
		};

		Flow flw = new Flw( "flw []" );
		Merge merge = new Merge().with( lc );
		merge.start( flw );
		long events = merge.end( flw ).count();

		assertEquals( 0, events );
		assertEquals( ""
				+ "start flw\n"
				+ "end flw\n", log.toString() );
	}

	/**
	 * Shows that events from disparate sources are merged
	 */
	@Test
	void merge() {
		LogCapture a = new LogCapture() {

			@Override
			public void start( Flow flow ) {
				// no-op
			}

			@Override
			public Stream<LogEvent> end( Flow flow ) {
				return Stream.of(
						new LogEvent( "time a", "lvl", "src", "msg" ),
						new LogEvent( "time c", "lvl", "src", "msg" ) );
			}
		};
		LogCapture b = new LogCapture() {

			@Override
			public void start( Flow flow ) {
				// no-op
			}

			@Override
			public Stream<LogEvent> end( Flow flow ) {
				return Stream.of(
						new LogEvent( "time b", "lvl", "src", "msg" ),
						new LogEvent( "time d", "lvl", "src", "msg" ) );
			}
		};

		Merge merge = new Merge()
				.with( a, b );

		Flow flw = new Flw( "flw []" );
		merge.start( flw );
		Stream<LogEvent> events = merge.end( flw );

		assertEquals( "time a, time b, time c, time d",
				events.map( e -> e.time )
						.collect( joining( ", " ) ) );
	}
}
