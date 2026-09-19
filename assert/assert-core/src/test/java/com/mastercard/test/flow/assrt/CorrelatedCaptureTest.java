package com.mastercard.test.flow.assrt;

import java.io.IOException;
import java.io.UncheckedIOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import static com.mastercard.test.flow.assrt.CorrelatedCapture.Outcome.ACCEPTED;
import static com.mastercard.test.flow.assrt.CorrelatedCapture.Outcome.CLOSED;
import static com.mastercard.test.flow.assrt.CorrelatedCapture.Outcome.LATE;
import static com.mastercard.test.flow.assrt.CorrelatedCapture.Outcome.UNATTRIBUTED;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import com.mastercard.test.flow.assrt.log.CorrelatedTail;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.LogEvent;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Correlation-attributed capture through the public source and runner seams,
 * with flows executing concurrently on the shared serial core.
 */
@SuppressWarnings("static-method")
class CorrelatedCaptureTest {

	/** A push source: events arrive as the "system" emits them. */
	static class Source implements CorrelatedCapture {
		final List<String> lifecycle = new ArrayList<>();
		Collector collector;
		RuntimeException flushFailure;
		RuntimeException closeFailure;
		/** Emits events while the source is closing, as a file source drains */
		Runnable onClose;

		@Override
		public void open( Collector c ) {
			lifecycle.add( "open" );
			collector = c;
		}

		@Override
		public void flush() {
			lifecycle.add( "flush" );
			if( flushFailure != null ) {
				throw flushFailure;
			}
		}

		@Override
		public void close() {
			lifecycle.add( "close" );
			if( onClose != null ) {
				onClose.run();
			}
			if( closeFailure != null ) {
				throw closeFailure;
			}
		}

		CorrelatedCapture.Outcome emit( String correlation, String message ) {
			return collector.accept( correlation,
					new LogEvent( "time", "INFO", "sut", message ) );
		}
	}

	private static TestFlocessor runner( String title, Source source,
			Consumer<Assertion> behaviour ) {
		TestFlocessor runner = new TestFlocessor( title, TestModel.triple() )
				.system( State.LESS, B ).reporting( Reporting.QUIETLY ).logs( source )
				.correlation( flow -> flow.meta().description() )
				.behaviour( behaviour );
		runner.finalOnlyReporting();
		return runner;
	}

	private static Flow flow( TestFlocessor runner, String description ) {
		return runner.flows().filter( f -> description.equals( f.meta().description() ) )
				.findFirst().orElseThrow();
	}

	private static Map<String, List<String>> flowLogs( Path report ) {
		Reader reader = new Reader( report );
		Map<String, List<String>> logs = new ConcurrentHashMap<>();
		for( Entry entry : reader.read().entries ) {
			logs.put( entry.description, reader.detail( entry ).logs.stream()
					.map( e -> e.level + " " + e.message ).toList() );
		}
		return logs;
	}

	private static void await( CountDownLatch latch ) throws InterruptedException {
		assertTrue( latch.await( 10, TimeUnit.SECONDS ), "coordination timed out" );
	}

	@Test
	void concurrentFlowsAreAttributedByIdentifierNotArrival( @TempDir Path directory )
			throws Exception {
		Source source = new Source();
		CountDownLatch entered = new CountDownLatch( 2 );
		Map<String, CountDownLatch> release = Map.of(
				"first", new CountDownLatch( 1 ), "second", new CountDownLatch( 1 ) );
		List<Throwable> failures = new ArrayList<>();
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = runner( "concurrent capture", source, a -> {
					entered.countDown();
					try {
						await( release.get( a.correlation().id() ) );
					}
					catch( InterruptedException e ) {
						throw new IllegalStateException( e );
					}
					a.actual().response( a.expected().response().content() );
				} ) ) {
			Thread first = new Thread( () -> runner.process( flow( runner, "first" ) ) );
			Thread second = new Thread( () -> runner.process( flow( runner, "second" ) ) );
			first.setUncaughtExceptionHandler( ( t, e ) -> failures.add( e ) );
			second.setUncaughtExceptionHandler( ( t, e ) -> failures.add( e ) );
			first.start();
			second.start();
			await( entered );

			// both flows are executing: arrival order and thread say nothing
			assertEquals( ACCEPTED, source.emit( "second", "s1" ) );
			assertEquals( ACCEPTED, source.emit( "first", "f1" ) );
			assertEquals( ACCEPTED, source.emit( "second", "s2" ) );
			assertEquals( UNATTRIBUTED, source.emit( "nobody", "orphan" ) );
			assertEquals( UNATTRIBUTED, source.emit( null, "anonymous" ) );

			release.get( "first" ).countDown();
			first.join( 10_000 );
			// first has ended while second is still active
			assertEquals( LATE, source.emit( "first", "f-late" ) );
			assertEquals( ACCEPTED, source.emit( "second", "s3" ) );

			release.get( "second" ).countDown();
			second.join( 10_000 );
			assertEquals( List.of(), failures );

			// a third flow processed on this thread after the others
			runner.behaviour( a -> a.actual().response( a.expected().response().content() ) );
			runner.process( flow( runner, "third" ) );
			assertEquals( List.of( "open", "flush", "flush", "flush" ), source.lifecycle );

			runner.completeProcessing();
			assertEquals( List.of( "open", "flush", "flush", "flush", "flush", "close" ),
					source.lifecycle );
			assertEquals( CLOSED, source.emit( "third", "too late" ) );

			Map<String, List<String>> logs = flowLogs( runner.report() );
			// f-late arrived after first had finished, but still carried its identifier
			assertEquals( List.of( "INFO f1", "INFO f-late" ), logs.get( "first" ) );
			assertEquals( List.of( "INFO s1", "INFO s2", "INFO s3" ), logs.get( "second" ) );
			assertEquals( List.of(), logs.get( "third" ) );

		}
	}

	@Test
	void identifiersAreGeneratedWhenNotExtracted( @TempDir Path directory ) {
		Source source = new Source();
		List<String> ids = new ArrayList<>();
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = runner( "generated ids", source, a -> {
					ids.add( a.correlation().id() );
					// the same execution presents the same identity to every assertion
					a.assertChildren( i -> true ).forEach( c -> ids.add( c.correlation().id() ) );
					assertEquals( ACCEPTED, source.emit( a.correlation().id(), "hello" ) );
					a.actual().response( a.expected().response().content() );
				} ).correlation( null ) ) {
			runner.process( flow( runner, "first" ) );
			runner.process( flow( runner, "second" ) );
			assertEquals( 4, ids.size() );
			assertEquals( ids.get( 0 ), ids.get( 1 ) );
			assertEquals( ids.get( 2 ), ids.get( 3 ) );
			assertNotEquals( ids.get( 0 ), ids.get( 2 ) );
			assertTrue( ids.get( 0 ).length() >= 8, ids.get( 0 ) );
			runner.completeProcessing();
			Map<String, List<String>> logs = flowLogs( runner.report() );
			assertEquals( List.of( "INFO hello" ), logs.get( "first" ) );
			assertEquals( List.of( "INFO hello" ), logs.get( "second" ) );
		}
	}

	@Test
	void correlationIsAvailableWithoutCapture() {
		List<String> ids = new ArrayList<>();
		try( TestFlocessor runner = new TestFlocessor( "no capture", TestModel.triple() )
				.system( State.LESS, B )
				.behaviour( a -> {
					ids.add( a.correlation().id() );
					a.correlation().alias( "ignored" );
					a.actual().response( a.expected().response().content() );
				} ) ) {
			runner.execute();
			assertEquals( 3, ids.stream().distinct().count() );
			assertEquals( 3, ids.stream().filter( Objects::nonNull ).count() );
			assertNull( runner.report() );
		}
	}

	@Test
	void reportingOffNeverOpensTheSource() {
		Source source = new Source();
		try( TestFlocessor runner = new TestFlocessor( "reporting off", TestModel.triple() )
				.system( State.LESS, B ).logs( source )
				.behaviour( a -> a.actual().response( a.expected().response().content() ) ) ) {
			runner.execute();
			assertEquals( List.of(), source.lifecycle );
			assertNull( source.collector );
		}
	}

	@Test
	void aliasesAttributeAndReusedIdentifiersAreAmbiguous( @TempDir Path directory ) {
		Source source = new Source();
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = runner( "aliases", source, a -> {
					a.correlation().alias( "txn" );
					assertEquals( ACCEPTED, source.emit( "txn", "by alias " + a.correlation().id() ) );
					assertEquals( ACCEPTED, source.emit( "same", "by shared " + a.correlation().id() ) );
					a.actual().response( a.expected().response().content() );
				} ).correlation( flow -> "same" ) ) {
			runner.process( flow( runner, "first" ) );
			// "same" and "txn" were bound to an execution that has ended
			runner.behaviour( a -> {
				a.correlation().alias( "txn" );
				assertEquals( UNATTRIBUTED, source.emit( "txn", "by alias" ) );
				assertEquals( UNATTRIBUTED, source.emit( "same", "by shared" ) );
				a.actual().response( a.expected().response().content() );
			} );
			runner.process( flow( runner, "second" ) );
			runner.completeProcessing();

			Map<String, List<String>> logs = flowLogs( runner.report() );
			assertEquals( List.of( "INFO by alias same", "INFO by shared same" ), logs.get( "first" ) );
			assertEquals( List.of(), logs.get( "second" ) );
		}
	}

	/**
	 * Events that reach no flow are counted by cause and reported at completion
	 * with the first few quoted, so a misconfigured pattern or reused identifier
	 * does not produce a silently empty report. Quoted messages are cut to a single
	 * bounded line. Every event is traced at FINE for when the samples are not
	 * enough. A repeated completion describes only the events that arrived since
	 * the previous one.
	 *
	 * @param directory Isolated artifact directory
	 */
	@Test
	void unroutedEventsAreSummarisedAndTraced( @TempDir Path directory ) {
		Source source = new Source();
		String longLine = "x".repeat( 250 );
		try( Diagnostics diagnostics = new Diagnostics( Faults.class );
				Diagnostics trace = new Diagnostics( LogCollector.class, Level.FINE );
				Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = runner( "unrouted", source,
						a -> a.actual().response( a.expected().response().content() ) )
								.correlation( flow -> "same" ) ) {
			runner.process( flow( runner, "first" ) );
			// "same" is now claimed by two executions
			runner.behaviour( a -> {
				assertEquals( UNATTRIBUTED, source.emit( "same", "by shared" ) );
				assertEquals( UNATTRIBUTED, source.emit( "unknown", "no such flow" ) );
				assertEquals( UNATTRIBUTED, source.emit( null, "no identifier" ) );
				assertEquals( UNATTRIBUTED, source.emit( "unknown", "first line\n\tat second line" ) );
				assertEquals( UNATTRIBUTED, source.emit( "unknown", longLine ) );
				assertEquals( UNATTRIBUTED, source.emit( "unknown", "sixth, beyond the samples" ) );
				a.actual().response( a.expected().response().content() );
			} );
			runner.process( flow( runner, "second" ) );
			assertEquals( List.of(), diagnostics.messages() );

			runner.completeProcessing();
			String excerpt = "unknown identifier [unknown] time INFO sut " + longLine.substring( 0, 200 )
					+ "...";
			assertEquals( List.of( String.join( "\n",
					"Correlated capture attributed no flow to 6 events: 5 without a known identifier, "
							+ "1 with an identifier claimed by more than one flow, 0 delivered after the run closed. First 5:",
					"  ambiguous [same] time INFO sut by shared",
					"  unknown identifier [unknown] time INFO sut no such flow",
					"  no identifier [null] time INFO sut no identifier",
					"  unknown identifier [unknown] time INFO sut first line...",
					"  " + excerpt,
					"Enable FINE logging on com.mastercard.test.flow.assrt.LogCollector to see every unrouted event" ) ),
					diagnostics.messages() );
			assertEquals( List.of(
					"Unrouted event: ambiguous [same] time INFO sut by shared",
					"Unrouted event: unknown identifier [unknown] time INFO sut no such flow",
					"Unrouted event: no identifier [null] time INFO sut no identifier",
					"Unrouted event: unknown identifier [unknown] time INFO sut first line...",
					"Unrouted event: " + excerpt,
					"Unrouted event: unknown identifier [unknown] time INFO sut sixth, beyond the samples" ),
					trace.messages() );

			// the record was drained: a repeated completion reports only what arrived since
			assertEquals( CLOSED, source.emit( "same", "too late" ) );
			runner.completeProcessing();
			assertEquals( 2, diagnostics.messages().size(), diagnostics.messages().toString() );
			assertEquals(
					"""
							Correlated capture attributed no flow to 1 events: 0 without a known identifier, \
							0 with an identifier claimed by more than one flow, 1 delivered after the run closed. First 1:
							  after close [same] time INFO sut too late
							Enable FINE logging on com.mastercard.test.flow.assrt.LogCollector to see every unrouted event""",
					diagnostics.messages().get( 1 ) );
			assertEquals( "Unrouted event: after close [same] time INFO sut too late",
					trace.messages().get( 6 ) );
		}
	}

	/**
	 * A file source delivers whatever remains in the file as it closes. Those
	 * events belong to the flows that carried their identifiers, so the source must
	 * be closed before the collector refuses events.
	 *
	 * @param directory Isolated artifact directory
	 */
	@Test
	void eventsDeliveredWhileClosingAreLateEvidence( @TempDir Path directory ) {
		Source source = new Source();
		source.onClose = () -> assertEquals( LATE, source.emit( "first", "drained at close" ) );
		try( Diagnostics diagnostics = new Diagnostics( Faults.class );
				Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = runner( "closing", source,
						a -> a.actual().response( a.expected().response().content() ) ) ) {
			runner.process( flow( runner, "first" ) );
			runner.completeProcessing();
			assertEquals( CLOSED, source.emit( "first", "after close" ) );
			assertEquals( List.of(), diagnostics.messages() );
			assertEquals( List.of( "INFO drained at close" ),
					flowLogs( runner.report() ).get( "first" ) );
		}
	}

	@Test
	void fullyAttributedRunReportsNothing( @TempDir Path directory ) {
		Source source = new Source();
		try( Diagnostics diagnostics = new Diagnostics( Faults.class );
				Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = runner( "attributed", source, a -> {
					assertEquals( ACCEPTED, source.emit( a.correlation().id(), "hello" ) );
					a.actual().response( a.expected().response().content() );
				} ) ) {
			runner.process( flow( runner, "first" ) );
			runner.completeProcessing();
			assertEquals( List.of(), diagnostics.messages() );
		}
	}

	@Test
	void ordinarySourceFaultIsVisibleAndNonFatal( @TempDir Path directory ) {
		Source source = new Source();
		source.flushFailure = new UncheckedIOException( new IOException( "disk" ) );
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = runner( "source fault", source, a -> {
					assertEquals( ACCEPTED, source.emit( a.correlation().id(), "before" ) );
					a.actual().response( a.expected().response().content() );
				} ) ) {
			runner.process( flow( runner, "first" ) );
			runner.completeProcessing();
			Map<String, List<String>> logs = flowLogs( runner.report() );
			assertEquals( List.of( "INFO before",
					"WARN Log capture end/materialize/close failed: java.io.UncheckedIOException" ),
					logs.get( "first" ) );
			assertEquals( List.of( "open", "flush", "flush", "close" ), source.lifecycle );
		}
	}

	/**
	 * The file source attributes by the logged identifier, including an
	 * unterminated final line that only its close can deliver. Lines for other
	 * identifiers are counted in the completion diagnostic.
	 *
	 * @param directory Isolated artifact directory
	 * @throws Exception on file failure
	 */
	@Test
	void fileSourceAttributesByLoggedIdentifier( @TempDir Path directory ) throws Exception {
		Path log = directory.resolve( "sut.log" );
		Files.writeString( log, "0 [-] INFO boot started before the run\n" );
		CorrelatedTail source = new CorrelatedTail( log,
				"^(?<time>\\d+) \\[(?<correlation>[^\\]]*)\\] (?<level>[A-Z]+) (?<source>\\S+) " );
		List<String> ids = new ArrayList<>();
		try( Diagnostics diagnostics = new Diagnostics( Faults.class );
				Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				TestFlocessor runner = new TestFlocessor( "file source", TestModel.triple() )
						.system( State.LESS, B ).reporting( Reporting.QUIETLY ).logs( source )
						.behaviour( a -> {
							// the "system" logs for this request, and stray lines for others
							String id = a.correlation().id();
							ids.add( id );
							append( log, "1 [" + id + "] INFO sut handling " + id,
									"2 [" + id + "] DEBUG sut detail", "   with continuation",
									"3 [other] WARN sut not mine" );
							a.actual().response( a.expected().response().content() );
						} ) ) {
			runner.finalOnlyReporting();
			runner.process( flow( runner, "first" ) );
			runner.process( flow( runner, "second" ) );
			// the system is still writing: its last line has no terminator yet
			Files.writeString( log, "4 [" + ids.get( 1 ) + "] INFO sut unterminated", UTF_8,
					java.nio.file.StandardOpenOption.APPEND );
			runner.completeProcessing();
			Map<String, List<String>> logs = flowLogs( runner.report() );
			for( String flow : List.of( "first", "second" ) ) {
				String id = "flow-";
				assertTrue( logs.get( flow ).get( 0 ).startsWith( "INFO []   handling " + id ),
						logs.toString() );
				assertEquals( "DEBUG []   detail\n   with continuation", logs.get( flow ).get( 1 ) );
			}
			assertEquals( 2, logs.get( "first" ).size(), logs.toString() );
			assertEquals( 3, logs.get( "second" ).size(), logs.toString() );
			assertEquals( "INFO []   unterminated", logs.get( "second" ).get( 2 ) );
			assertEquals( 1, diagnostics.messages().size(), diagnostics.messages().toString() );
			assertTrue(
					diagnostics.messages().get( 0 ).startsWith(
							"""
									Correlated capture attributed no flow to 2 events: 2 without a known identifier, \
									0 with an identifier claimed by more than one flow, 0 delivered after the run closed. First 2:
									  unknown identifier [other] 3 WARN sut []   not mine
									""" ),
					diagnostics.messages().get( 0 ) );
		}
	}

	private static void append( Path file, String... lines ) {
		try {
			Files.writeString( file, String.join( "\n", lines ) + "\n", UTF_8,
					java.nio.file.StandardOpenOption.APPEND );
		}
		catch( IOException e ) {
			throw new UncheckedIOException( e );
		}
	}

	@Test
	void controlFailureFromSourcePropagates( @TempDir Path directory ) {
		Source source = new Source();
		AssertionError failure = new AssertionError( "not peripheral" );
		source.flushFailure = new IllegalStateException( failure );
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() ) ) {
			// not auto-closed: completion is expected to keep failing
			TestFlocessor runner = runner( "fatal source", source,
					a -> a.actual().response( a.expected().response().content() ) );
			Flow flow = flow( runner, "first" );
			assertSame( source.flushFailure,
					assertThrows( IllegalStateException.class, () -> runner.process( flow ) ) );
			assertSame( source.flushFailure,
					assertThrows( IllegalStateException.class, runner::completeProcessing ) );
			assertEquals( List.of( "open", "flush", "flush", "close" ), source.lifecycle );
		}
	}

	/**
	 * The source is closed even when its final flush fails, and a close failure is
	 * retained alongside the flush failure rather than replacing it
	 *
	 * @param flushFails Whether the final flush fails too
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void closeFailureIsRetained( boolean flushFails ) {
		Source source = new Source();
		LogCollector collector = new LogCollector( source );
		Flow flow = TestModel.abc().flows().findFirst().orElseThrow();
		collector.start( flow );
		assertEquals( List.of( "open" ), source.lifecycle );
		source.closeFailure = new IllegalStateException( new AssertionError( "close" ) );
		if( flushFails ) {
			source.flushFailure = new IllegalStateException( new AssertionError( "flush" ) );
		}
		IllegalStateException thrown = assertThrows( IllegalStateException.class,
				() -> collector.close( f -> false ) );
		assertEquals( List.of( "open", "flush", "close" ), source.lifecycle );
		if( flushFails ) {
			assertSame( source.flushFailure, thrown );
			assertEquals( List.of( source.closeFailure ), List.of( thrown.getSuppressed() ) );
		}
		else {
			assertSame( source.closeFailure, thrown );
			assertEquals( 0, thrown.getSuppressed().length );
		}
		assertEquals( CLOSED, source.emit( "any", "after close" ) );
		// repeated close does nothing
		collector.close( f -> false );
		assertEquals( List.of( "open", "flush", "close" ), source.lifecycle );
	}

	/** Late events are handed over once, then forgotten */
	@Test
	void lateEventsAreDrainedOnce() {
		Source source = new Source();
		LogCollector collector = new LogCollector( source );
		Flow flow = TestModel.abc().flows().findFirst().orElseThrow();
		collector.start( flow );
		collector.bind( flow, "id" );
		assertEquals( ACCEPTED, source.emit( "id", "during" ) );
		try( var events = collector.end( flow ) ) {
			assertEquals( List.of( "during" ), events.map( e -> e.message ).toList() );
		}
		assertEquals( Map.of(), collector.late() );
		assertEquals( LATE, source.emit( "id", "after" ) );
		Map<Flow, List<LogEvent>> late = collector.late();
		assertEquals( List.of( "after" ), late.get( flow ).stream().map( e -> e.message ).toList() );
		assertEquals( Map.of(), collector.late() );
	}
}
