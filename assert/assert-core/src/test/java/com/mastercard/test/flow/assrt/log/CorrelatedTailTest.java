package com.mastercard.test.flow.assrt.log;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mastercard.test.flow.assrt.CorrelatedCapture.Collector;
import com.mastercard.test.flow.assrt.CorrelatedCapture.Outcome;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Incremental, correlation-attributed extraction from a growing log file.
 */
@SuppressWarnings("static-method")
class CorrelatedTailTest {

	/**
	 * Uncaptured header text (here the brackets) stays in the content, as in Tail.
	 */
	private static final String PATTERN = "^(?<time>\\d+) \\[(?<correlation>[^\\]]*)\\] (?<level>[A-Z]+) (?<source>\\S+)";

	/** Records every delivery in order */
	static class Sink implements Collector {
		final List<String> delivered = new ArrayList<>();

		@Override
		public Outcome accept( String correlation, LogEvent event ) {
			delivered.add( correlation + "|" + event.time + "|" + event.level + "|" + event.source
					+ "|" + event.message );
			return Outcome.ACCEPTED;
		}
	}

	private static void append( Path file, String... lines ) throws IOException {
		StringBuilder sb = new StringBuilder();
		for( String line : lines ) {
			sb.append( line ).append( '\n' );
		}
		Files.write( file, sb.toString().getBytes( UTF_8 ),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND );
	}

	@Test
	void readsIncrementallyFromOpenAndFramesMultilineEvents( @TempDir Path dir )
			throws IOException {
		Path file = dir.resolve( "app.log" );
		append( file, "001 [x] INFO src before open is never read" );
		CorrelatedTail tail = new CorrelatedTail( file, PATTERN );
		Sink sink = new Sink();
		tail.open( sink );

		append( file,
				"002 [a] INFO src first for a",
				"003 [b] WARN src multi",
				"    line",
				"    content",
				"004 [] INFO src no correlation" );
		tail.flush();
		assertEquals( List.of(
				"a|002|INFO|src|[]   first for a",
				"b|003|WARN|src|[]   multi\n    line\n    content",
				"null|004|INFO|src|[]   no correlation" ), sink.delivered );

		// a second flush delivers only what arrived since
		append( file, "005 [a] INFO src second for a" );
		tail.flush();
		tail.flush();
		assertEquals( 4, sink.delivered.size() );
		assertEquals( "a|005|INFO|src|[]   second for a", sink.delivered.get( 3 ) );
		tail.close();
		assertEquals( "", tail.summary() );
	}

	@Test
	void holdsBackAnIncompleteTrailingEvent( @TempDir Path dir ) throws IOException {
		Path file = dir.resolve( "app.log" );
		Files.createFile( file );
		CorrelatedTail tail = new CorrelatedTail( file, PATTERN );
		Sink sink = new Sink();
		tail.open( sink );

		// the last line has no terminator: the writer may still be mid-event
		Files.write( file, "006 [a] INFO src complete\n007 [b] INFO src partial".getBytes( UTF_8 ),
				StandardOpenOption.APPEND );
		tail.flush();
		assertEquals( List.of( "a|006|INFO|src|[]   complete" ), sink.delivered );

		Files.write( file, " tail\n    and more\n".getBytes( UTF_8 ), StandardOpenOption.APPEND );
		tail.flush();
		assertEquals(
				List.of( "a|006|INFO|src|[]   complete", "b|007|INFO|src|[]   partial tail\n    and more" ),
				sink.delivered );

		// close delivers whatever remains, terminated or not
		Files.write( file, "008 [a] INFO src final".getBytes( UTF_8 ), StandardOpenOption.APPEND );
		tail.close();
		assertEquals( 3, sink.delivered.size() );
		assertEquals( "a|008|INFO|src|[]   final", sink.delivered.get( 2 ) );
	}

	@Test
	void contentBeforeAnyEventIsDeliveredUnattributed( @TempDir Path dir ) throws IOException {
		Path file = dir.resolve( "app.log" );
		Files.createFile( file );
		CorrelatedTail tail = new CorrelatedTail( file, PATTERN );
		Sink sink = new Sink();
		tail.open( sink );
		append( file, "garbage that matches nothing", "009 [a] INFO src ok" );
		tail.close();
		assertEquals( List.of( "null|?|?|?|garbage that matches nothing", "a|009|INFO|src|[]   ok" ),
				sink.delivered );
	}

	@Test
	void boundedReadsCountSkippedBytesInsteadOfInventingEvents( @TempDir Path dir )
			throws IOException {
		Path file = dir.resolve( "app.log" );
		Files.createFile( file );
		CorrelatedTail tail = new CorrelatedTail( file, PATTERN ).readLimit( 64 );
		Sink sink = new Sink();
		tail.open( sink );
		// 5 events of 25 bytes: only the first two fit in the 64-byte flush budget
		for( int i = 0; i < 5; i++ ) {
			append( file, String.format( "%03d [a] INFO src event %d", 10 + i, i ) );
		}
		tail.flush();
		assertEquals( List.of( "a|010|INFO|src|[]   event 0", "a|011|INFO|src|[]   event 1" ),
				sink.delivered );
		// the next flush resumes from where the budget stopped, not from the end
		tail.flush();
		assertEquals( 5, sink.delivered.size() );
		assertEquals( "a|014|INFO|src|[]   event 4", sink.delivered.get( 4 ) );
		tail.close();
		assertEquals( 5, sink.delivered.size() );
		assertEquals( "", tail.summary() );
	}

	@Test
	void truncationAndMissingFileAreReportedNotGuessed( @TempDir Path dir ) throws IOException {
		Path file = dir.resolve( "app.log" );
		Files.createFile( file );
		CorrelatedTail tail = new CorrelatedTail( file, PATTERN );
		Sink sink = new Sink();
		tail.open( sink );
		append( file, "012 [a] INFO src one", "013 [a] INFO src two" );
		tail.flush();
		// rotation: the file is now shorter than the offset we had reached
		Files.write( file, "014 [a] INFO src after rotation\n".getBytes( UTF_8 ) );
		tail.flush();
		Files.delete( file );
		tail.flush();
		tail.close();
		assertEquals( List.of( "a|012|INFO|src|[]   one", "a|013|INFO|src|[]   two",
				"a|014|INFO|src|[]   after rotation" ), sink.delivered );
		String summary = tail.summary();
		assertTrue( summary.contains( "truncated" ), summary );
		assertTrue( summary.contains( "NoSuchFileException" ), summary );
	}

	/**
	 * Headers are recognised only at the line start, so an unanchored pattern
	 * cannot match header-like text embedded in a body line, and long body lines
	 * are scanned once rather than from every offset.
	 */
	@Test
	void unanchoredPatternMatchesOnlyAtLineStart( @TempDir Path dir ) throws IOException {
		Path file = dir.resolve( "app.log" );
		Files.createFile( file );
		CorrelatedTail tail = new CorrelatedTail( file, PATTERN.substring( 1 ) );
		Sink sink = new Sink();
		tail.open( sink );
		String body = "x".repeat( 4000 ) + " 999 [b] ERROR fake header inside a body line";
		append( file, "016 [a] INFO src request", body, "017 [a] INFO src response" );
		tail.close();
		assertEquals( List.of(
				"a|016|INFO|src|[]   request\n" + body,
				"a|017|INFO|src|[]   response" ), sink.delivered );
	}

	@Test
	void patternMustCaptureAllGroups() {
		assertThrows( IllegalArgumentException.class,
				() -> new CorrelatedTail( Path.of( "x" ),
						"^(?<time>\\d+) (?<level>[A-Z]+) (?<source>\\S+)" ) );
		assertThrows( IllegalArgumentException.class,
				() -> new CorrelatedTail( Path.of( "x" ),
						"^(?<time>\\d+) (?<correlation>\\S+) (?<source>\\S+)" ) );
	}

	@Test
	void invalidUtf8IsReplacedNotFatal( @TempDir Path dir ) throws IOException {
		Path file = dir.resolve( "app.log" );
		Files.createFile( file );
		CorrelatedTail tail = new CorrelatedTail( file, PATTERN );
		Sink sink = new Sink();
		tail.open( sink );
		Files.write( file, new byte[] { '0', '1', '5', ' ', '[', 'a', ']', ' ', 'I', 'N', 'F', 'O', ' ',
				's', 'r', 'c', ' ', (byte) 0xC3, (byte) 0x28, '\n' }, StandardOpenOption.APPEND );
		tail.close();
		assertEquals( 1, sink.delivered.size() );
		assertTrue( sink.delivered.get( 0 ).startsWith( "a|015|INFO|src|[]   " ),
				sink.delivered.get( 0 ) );
	}
}
