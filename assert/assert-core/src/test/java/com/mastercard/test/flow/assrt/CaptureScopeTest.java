package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.TestAbortedException;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Classification of log capture faults: which fail the test and which are
 * reduced to a diagnostic
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
	void controlFatalAndInterruptedCaptureFailuresAreNotSwallowed( Throwable failure ) {
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

	/**
	 * Errors, test-control signals and interruptions, at any depth of the cause
	 * chain, are never reduced to diagnostics
	 */
	static Stream<Throwable> protectedFailures() {
		return Stream.of( new AssertionError( "assertion" ), new LinkageError( "fatal" ),
				new TestAbortedException( "assumption" ), new CancellationException( "stop" ),
				new IllegalStateException( new CancellationException( "nested stop" ) ),
				new IllegalStateException( new InterruptedException( "interrupted" ) ),
				new IllegalStateException( new AssertionError( "nested assertion" ) ),
				new IllegalStateException( new TestAbortedException( "nested assumption" ) ) );
	}

	/**
	 * Any other runtime exception is an ordinary fault, whatever its subtype and
	 * whatever it carries as suppressed: the test passes and the fault is diagnosed
	 *
	 * @param phase Where the source fails
	 */
	@ParameterizedTest
	@EnumSource(Phase.class)
	void ordinaryCaptureFaultDoesNotFailPassingWork( Phase phase ) {
		Source source = new Source();
		source.phase = phase;
		source.failure = new RuntimeException( "unknown subtype" ) {
			private static final long serialVersionUID = 1L;
		};
		source.failure.addSuppressed( new AssertionError( "suppressed" ) );
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
