package com.mastercard.test.flow.report.data;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link LogEvent}
 */
@SuppressWarnings("static-method")
class LogEventTest {

	/**
	 * Exercises time formats
	 */
	@Test
	void timeFormat() {
		assertEquals( "1970-01-02T03:04:05.678Z level source message",
				new LogEvent( Instant.parse( "1970-01-02T03:04:05.678Z" ),
						"level", "source", "message" ).toString() );

		assertEquals( "1970-01-02T03:04:05Z level source message",
				new LogEvent( "1970-01-02T03:04:05Z",
						"level", "source", "message" ).toString() );

		assertEquals( "recently level source message",
				new LogEvent( "recently", "level", "source", "message" ).toString() );
	}

	/**
	 * Exercises {@link LogEvent#stackTrace(Throwable)}
	 */
	@Test
	void stacktrace() {
		try {
			throw new Exception( "kaboom!" );
		}
		catch( Exception e ) {
			assertEquals( ""
					+ "java.lang.Exception: kaboom!\n"
					+ "	at com.mastercard.test.flow.report.data.LogEventTest.stacktrace(LogEventTest.java:##)",
					Stream.of( LogEvent.stackTrace( e ).split( "\n" ) )
							.limit( 2 )
							.map( s -> s.replaceAll( "(\\.java:)\\d+", "$1##" ) )
							.collect( joining( "\n" ) )
							.replace( "\r", "" )
							.trim() );
		}
	}

	/**
	 * Exercises the behaviour when stack trace extraction fails
	 */
	@Test
	void stacktraceFailure() {
		Throwable th = new Throwable() {
			private static final long serialVersionUID = 1L;

			@Override
			public void printStackTrace( PrintWriter s ) {
				throw new RuntimeException( "stack trace failure!" );
			}
		};

		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> LogEvent.stackTrace( th ) );

		assertEquals( "Failed to capture stacktrace", ise.getMessage() );
		assertEquals( "stack trace failure!", ise.getCause().getMessage() );
	}

}
