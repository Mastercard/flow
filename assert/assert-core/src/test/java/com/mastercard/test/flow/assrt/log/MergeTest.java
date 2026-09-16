package com.mastercard.test.flow.assrt.log;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Exercises {@link Merge}
 */
@SuppressWarnings("static-method")
class MergeTest {
	@Test
	void duplicateBeginDoesNotReplaceTheActiveSources() {
		List<String> events = new ArrayList<>();
		Merge merge = new Merge().with( source( "first", events, null, null ) );
		Flow flow = new Flw( "active []" );
		merge.start( flow );
		merge.with( source( "later", events, null, null ) );
		assertThrows( IllegalStateException.class, () -> merge.start( flow ) );
		try( Stream<LogEvent> logs = merge.end( flow ) ) {
			assertEquals( 1, logs.count() );
		}
		assertEquals( List.of( "first begin", "first end", "first read", "first close" ), events );
	}

	/**
	 * Failed startup still drains successful sources and preserves the first
	 * failure.
	 *
	 * @param same Whether rollback raises the identical exception
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void rollbackFailureCannotReplaceTheStartFailure( boolean same ) {
		List<String> events = new ArrayList<>();
		var primary = new IllegalStateException( "start failed" );
		var cleanup = same ? primary : new IllegalStateException( "rollback failed" );
		Merge merge = new Merge().with( source( "first", events, null, cleanup ),
				source( "second", events, primary, null ) );
		Flow flow = new Flw( "rollback []" );
		assertSame( primary, assertThrows( IllegalStateException.class, () -> merge.start( flow ) ) );
		assertEquals( same ? List.of() : List.of( cleanup ), List.of( primary.getSuppressed() ) );
		assertEquals(
				List.of( "first begin", "second begin", "first end", "first read", "first close" ),
				events );
		try( Stream<LogEvent> logs = merge.end( flow ) ) {
			assertEquals( 0, logs.count(), "failed rollback still retires the active capture" );
		}
	}

	@Test
	void fatalReadStillClosesEverySourceAndPreservesFailureIdentity() {
		List<String> events = new ArrayList<>();
		var primary = new AssertionError( "fatal capture read" );
		var cleanup = new IllegalStateException( "later read failed" );
		Merge merge = new Merge().with( source( "first", events, null, primary ),
				source( "second", events, null, cleanup ), source( "third", events, null, primary ) );
		Flow flow = new Flw( "fatal []" );
		merge.start( flow );
		assertSame( primary, assertThrows( AssertionError.class, () -> merge.end( flow ) ) );
		assertEquals( List.of( cleanup ), List.of( primary.getSuppressed() ) );
		assertEquals( List.of( "first begin", "second begin", "third begin", "first end", "first read",
				"first close", "second end", "second read", "second close", "third end", "third read",
				"third close" ), events );
	}

	@Test
	void partialBeginBalancesOnlySuccessfulSources() {
		List<String> events = new ArrayList<>();
		IllegalStateException failure = new IllegalStateException( "begin failed" );
		Merge merge = new Merge().with( source( "first", events, null, null ),
				source( "second", events, failure, null ), source( "third", events, null, null ) );
		Flow flow = new Flw( "partial []" );
		assertSame( failure, assertThrows( IllegalStateException.class, () -> merge.start( flow ) ) );
		assertEquals(
				List.of( "first begin", "second begin", "first end", "first read", "first close" ),
				events );
		try( Stream<LogEvent> logs = merge.end( flow ) ) {
			assertEquals( 0, logs.count(), "rolled-back sources must not end twice" );
		}
	}

	@Test
	void failedReadClosesItsStreamAndBalancesRemainingSources() {
		List<String> events = new ArrayList<>();
		IllegalStateException failure = new IllegalStateException( "read failed" );
		Merge merge = new Merge().with( source( "first", events, null, failure ),
				source( "second", events, null, null ) );
		Flow flow = new Flw( "failed read []" );
		merge.start( flow );
		assertSame( failure, assertThrows( IllegalStateException.class, () -> merge.end( flow ) ) );
		assertEquals( List.of( "first begin", "second begin", "first end", "first read", "first close",
				"second end", "second read", "second close" ), events );
	}

	private static LogCapture source( String name, List<String> events, RuntimeException begin,
			Throwable read ) {
		return new LogCapture() {
			@Override
			public void start( Flow flow ) {
				events.add( name + " begin" );
				if( begin != null ) {
					throw begin;
				}
			}

			@Override
			public Stream<LogEvent> end( Flow flow ) {
				events.add( name + " end" );
				return Stream.of( new LogEvent( "time", "INFO", name, "message" ) ).peek( e -> {
					events.add( name + " read" );
					if( read instanceof Error error )
						throw error;
					if( read instanceof RuntimeException runtime )
						throw runtime;
				} ).onClose( () -> events.add( name + " close" ) );
			}
		};
	}

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
