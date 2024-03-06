
package com.mastercard.test.flow.assrt.log;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.report.QuietFiles;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Exercises log file tailing
 */
@SuppressWarnings("static-method")
class TailTest {

	private static final Path LOG_PATH = QuietFiles.createTempFile( null, null );
	static {
		LOG_PATH.toFile().deleteOnExit();
	}

	private static final Tail LOG_TAIL = new Tail( LOG_PATH,
			"^(?<level>[A-Z]+) \\[\\w+\\] (?<time>\\d+) (?<source>[a-z]+)" );

	/**
	 * Exercises extracting content from a file that grows as {@link Flow}s are
	 * processed
	 */
	@Test
	void tail() {
		Flow a = new Flw( "a []" );
		Flow b = new Flw( "b []" );

		log( "INFO [main] 001 foosrc This line is before the tail started" );

		LOG_TAIL.start( a );

		log( "INFO [main] 002 foosrc This line is after the tail started",
				"WARN [thread1] 003 barsrc there's about to be some multline content!",
				"    Here is content from the previous line's event!",
				"    and there's more!",
				"INFO [thread2] 004 foosrc here's another event" );

		LOG_TAIL.start( b );

		log( "    here is more content from the previous event!",
				"TRACE [thread1] 005 bazsrc This event will be shared by both flows" );

		Stream<LogEvent> ae = LOG_TAIL.end( a );

		log( "TRACE [main] 006 bazsrc This event is just for b" );

		Stream<LogEvent> be = LOG_TAIL.end( b );

		log( "ERROR [main] 007 bazsrc No-one sees this one" );

		Assertions.assertEquals( ""
				+ "INFO/foosrc/002/[main]   This line is after the tail started\n"
				+ "WARN/barsrc/003/[thread1]   there's about to be some multline content!\n"
				+ "    Here is content from the previous line's event!\n"
				+ "    and there's more!\n"
				+ "INFO/foosrc/004/[thread2]   here's another event\n"
				+ "    here is more content from the previous event!\n"
				+ "TRACE/bazsrc/005/[thread1]   This event will be shared by both flows",
				ae.map( e -> String.format( "%s/%s/%s/%s", e.level, e.source, e.time, e.message ) )
						.collect( Collectors.joining( "\n" ) ) );

		Assertions.assertEquals( ""
				+ "?/?/?/    here is more content from the previous event!\n"
				+ "TRACE/bazsrc/005/[thread1]   This event will be shared by both flows\n"
				+ "TRACE/bazsrc/006/[main]   This event is just for b",
				be.map( e -> String.format( "%s/%s/%s/%s", e.level, e.source, e.time, e.message ) )
						.collect( Collectors.joining( "\n" ) ) );
	}

	/**
	 * Exercises default behaviour in the face of errors - they are ignored and no
	 * content is extracted
	 */
	@Test
	void failuresIgnored() {

		Flow a = new Flw( "a []" );

		Tail tail = new Tail( Paths.get( "nonexistentfile.txt" ), "" );

		tail.start( a );
		long events = tail.end( a ).count();
		assertEquals( 0, events );
	}

	/**
	 * Exercises error capture
	 */
	@Test
	void failuresCaptured() {

		Flow a = new Flw( "a []" );

		StringBuilder errLog = new StringBuilder();
		Tail tail = new Tail( Paths.get( "nonexistentfile.txt" ), "" )
				.errors( ( msg, ioe ) -> errLog
						.append( msg )
						.append( " " )
						.append( ioe.getClass().getSimpleName() )
						.append( " " )
						.append( ioe.getMessage() )
						.append( "\n" ) );

		tail.start( a );
		long events = tail.end( a ).count();

		assertEquals( 0, events );
		assertEquals( ""
				+ "Failed to get start size NoSuchFileException nonexistentfile.txt\n"
				+ "Failed to extract content NoSuchFileException nonexistentfile.txt\n",
				errLog.toString() );
	}

	private static void log( String... lines ) {
		QuietFiles.write( LOG_PATH, Stream.of( lines )
				.map( l -> l + "\n" )
				.collect( Collectors.joining() ).getBytes( UTF_8 ),
				StandardOpenOption.APPEND );
	}
}
