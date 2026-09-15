package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.LogEvent;
import com.mastercard.test.flow.util.Option.Temporary;
import java.nio.file.Path;
import java.nio.file.Files;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Execution-owned capture through the serial core and public source interface.
 */
@SuppressWarnings("static-method")
class CaptureScopeTest {
	/** Independently injected source faults, without timing or background work. */
	enum Phase {
		BEGIN, END, READ, CLOSE
	}

	static class Source implements LogCapture {
		final List<String> events = new ArrayList<>();
		Phase phase;
		Throwable failure = new IllegalStateException( "capture unavailable" );
		String message = "live";

		@Override
		public void start( Flow flow ) {
			events.add( "begin" );
			fail( Phase.BEGIN );
		}

		@Override
		public Stream<LogEvent> end( Flow flow ) {
			events.add( "end" );
			fail( Phase.END );
			return Stream.of( new LogEvent( "time", "INFO", "source", message ) ).peek( e -> {
				events.add( "read" );
				fail( Phase.READ );
			} ).onClose( () -> {
				events.add( "close" );
				fail( Phase.CLOSE );
			} );
		}

		void fail( Phase at ) {
			if( phase == at ) {
				raise( failure );
			}
		}
	}

	private static void raise( Throwable failure ) {
		if( failure instanceof Error error ) {
			throw error;
		}
		throw (RuntimeException) failure;
	}

	private static TestFlocessor runner( String title, Source source ) {
		return new TestFlocessor( title, TestModel.abc() )
				.system( State.LESS, B ).reporting( Reporting.QUIETLY ).logs( source )
				.behaviour( a -> a.actual().response( a.expected().response().content() ) );
	}

	@Test
	void reportDecorationCannotReplaceSutFailure() {
		Source source = new Source();
		IllegalStateException primary = new IllegalStateException( "SUT failed" );
		IllegalArgumentException decoration = new IllegalArgumentException( "decoration failed" );
		TestFlocessor runner = runner( "capture primary", source )
				.behaviour( a -> {
					throw primary;
				} )
				.motivation( ( text, a ) -> {
					throw decoration;
				} );
		Flow flow = runner.flows().findFirst().orElseThrow();
		assertSame( primary,
				assertThrows( IllegalStateException.class, () -> runner.process( flow ) ) );
		assertEquals( List.of( decoration ), List.of( primary.getSuppressed() ) );
		assertEquals( List.of( "begin", "end", "read", "close" ), source.events );
		assertSame( decoration,
				assertThrows( IllegalStateException.class, runner::completeProcessing ).getCause() );
		assertSame( decoration,
				assertThrows( IllegalStateException.class, runner::completeProcessing ).getCause() );
		runner.reporting( Reporting.NEVER ).behaviour( a -> {
			throw new AssertionError( "A failed close must still prevent SUT use" );
		} );
		assertThrows( IllegalStateException.class, () -> runner.process( flow ) );
		assertEquals( List.of( "begin", "end", "read", "close" ), source.events );
	}

	@ParameterizedTest
	@MethodSource("protectedFailures")
	void controlFatalAndUnknownCaptureFailuresAreNotSwallowed( Throwable failure ) {
		for( Phase phase : Phase.values() ) {
			Source source = new Source();
			source.phase = phase;
			source.failure = failure;
			TestFlocessor runner = runner( "protected capture", source );
			Flow flow = runner.flows().findFirst().orElseThrow();
			try {
				assertSame( failure, assertThrows( Throwable.class, () -> runner.process( flow ) ) );
				assertEquals( phase == Phase.BEGIN ? List.of( "begin" )
						: phase == Phase.END ? List.of( "begin", "end" )
								: List.of( "begin", "end", "read", "close" ),
						source.events );
			}
			finally {
				runner.completeProcessing();
			}
		}
	}

	static Stream<Throwable> protectedFailures() {
		IllegalStateException suppressed = new IllegalStateException( "source" );
		suppressed.addSuppressed( new AssertionError( "unsafe close" ) );
		return Stream.of( new AssertionError( "assertion" ), new LinkageError( "fatal" ),
				new CancellationException( "stop" ), new RuntimeException( "unknown" ) {
					private static final long serialVersionUID = 1L;
				}, new IllegalStateException( new CancellationException( "nested stop" ) ),
				new IllegalStateException( new InterruptedException( "interrupted" ) ),
				new IllegalStateException( new AssertionError( "nested assertion" ) ), suppressed );
	}

	@ParameterizedTest
	@MethodSource("protectedFailures")
	void primaryExecutionFailureSurvivesCaptureFault( Throwable primary ) {
		Source source = new Source();
		source.phase = Phase.READ;
		TestFlocessor runner = runner( "primary capture fault", source )
				.behaviour( a -> raise( primary ) );
		Flow flow = runner.flows().findFirst().orElseThrow();
		try {
			assertSame( primary, assertThrows( Throwable.class, () -> runner.process( flow ) ) );
			assertEquals( List.of( "begin", "end", "read", "close" ), source.events );
		}
		finally {
			runner.completeProcessing();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "flow", "interaction", "complete" })
	void progressFailureStillClosesCapture( String at ) {
		Source source = new Source();
		IllegalStateException primary = new IllegalStateException( "progress" );
		TestFlocessor runner = runner( "capture progress " + at, source ).listening( new Listener() {
			@Override
			public void flow( Flow flow ) {
				if( "flow".equals( at ) ) {
					throw primary;
				}
			}

			@Override
			public void interaction( Interaction interaction ) {
				if( "interaction".equals( at ) ) {
					throw primary;
				}
			}

			@Override
			public void flowComplete( Flow flow ) {
				if( "complete".equals( at ) ) {
					throw primary;
				}
			}
		} );
		Flow flow = runner.flows().findFirst().orElseThrow();
		try {
			assertSame( primary,
					assertThrows( IllegalStateException.class, () -> runner.process( flow ) ) );
			assertEquals( List.of( "begin", "end", "read", "close" ), source.events );
		}
		finally {
			runner.completeProcessing();
		}
	}

	@Test
	void absentWriterStillMaterializesAndPreservesAssertions( @TempDir Path directory )
			throws Exception {
		Path blocked = Files.createFile( directory.resolve( "not-a-directory" ) );
		Source source = new Source();
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( blocked.toString() ) ) {
			TestFlocessor runner = runner( "absent writer", source ).behaviour( a -> {
				a.actual().request( "wrong request".getBytes( UTF_8 ) );
				a.actual().response( "wrong response".getBytes( UTF_8 ) );
			} );
			Flow flow = runner.flows().findFirst().orElseThrow();
			AssertionError failure = assertThrows( AssertionError.class, () -> runner.process( flow ) );
			assertEquals( 1, failure.getSuppressed().length );
			assertEquals( 2, runner.events().split( "COMPARE", -1 ).length - 1 );
			assertEquals( List.of( "begin", "end", "read", "close" ), source.events );
			assertNull( runner.report() );
			runner.completeProcessing();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "no assertions", "no interactions", "not observed", "precondition" })
	void genuineSkipsBalanceEnteredCapture( String kind ) {
		Source source = new Source();
		TestFlocessor runner = new TestFlocessor( "capture " + kind,
				"precondition".equals( kind ) ? TestModel.abcWithImplicit() : TestModel.abc() )
						.system( State.LESS, "no interactions".equals( kind ) || "not observed".equals( kind )
								? TestModel.Actors.E
								: B )
						.reporting( Reporting.QUIETLY ).logs( source ).behaviour( a -> {
						} );
		if( "not observed".equals( kind ) ) {
			runner.system( State.LESS, TestModel.Actors.A ).autonomous( TestModel.Actors.A );
		}
		try {
			runner.execute();
			assertEquals( "abc [] " + ("not observed".equals( kind ) ? "NOT_OBSERVED" : "SKIP"),
					runner.results() );
			assertEquals( List.of( "begin", "end", "read", "close" ), source.events );
		}
		finally {
			runner.completeProcessing();
		}
	}

	@Test
	void enumerationAndDisabledReportingDoNotBeginCapture() {
		Source source = new Source();
		TestFlocessor runner = runner( "unentered capture", source );
		assertEquals( 1, runner.flows().count() );
		assertEquals( List.of(), source.events );
		runner.reporting( Reporting.NEVER );
		runner.execute();
		assertEquals( "abc [] SUCCESS", runner.results() );
		assertEquals( List.of(), source.events );
		assertNull( runner.report() );
	}

	@Test
	void replayUsesHistoricBytesButStillCapturesLiveLogs() {
		Source historic = new Source();
		historic.message = "historic";
		TestFlocessor original = runner( "capture replay original", historic );
		try {
			original.execute();
		}
		finally {
			original.completeProcessing();
		}
		Source live = new Source();
		try( Temporary replay = AssertionOptions.REPLAY.temporarily( original.report().toString() ) ) {
			TestFlocessor runner = runner( "capture replay live", live )
					.behaviour( a -> {
						throw new AssertionError( "Replay must not call the SUT" );
					} );
			try {
				runner.execute();
				assertEquals( "abc [] SUCCESS", runner.results() );
				assertEquals( List.of( "begin", "end", "read", "close" ), live.events );
				Reader reader = new Reader( runner.report() );
				List<String> messages = reader.detail( reader.read().entries.get( 0 ) ).logs.stream()
						.map( e -> e.message ).toList();
				assertTrue( messages.contains( "live" ) );
				assertTrue( !messages.contains( "historic" ) );
			}
			finally {
				runner.completeProcessing();
			}
		}
	}

	@ParameterizedTest
	@EnumSource(Phase.class)
	void ordinaryCaptureFaultDoesNotFailPassingWork( Phase phase ) {
		Source source = new Source();
		source.phase = phase;
		TestFlocessor runner = runner( "capture fault " + phase, source );
		try {
			runner.execute();
			assertEquals( "abc [] SUCCESS", runner.results() );
			assertEquals( phase == Phase.BEGIN ? List.of( "begin" )
					: phase == Phase.END ? List.of( "begin", "end" )
							: List.of( "begin", "end", "read", "close" ),
					source.events );
			Reader reader = new Reader( runner.report() );
			assertTrue( reader.detail( reader.read().entries.get( 0 ) ).logs.stream()
					.anyMatch( e -> e.message.contains( "Log capture" ) ), "source fault is diagnosed" );
		}
		finally {
			runner.completeProcessing();
		}
	}
}
