package com.mastercard.test.flow.assrt.log;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mastercard.test.flow.assrt.CorrelatedCapture;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Extracts log events from a file on the local system and attributes each to
 * the flow whose correlation identifier the event carries. Unlike {@link Tail}
 * this is safe when flows execute concurrently: the file is read once,
 * incrementally, from the position at which the run opened it, and attribution
 * comes from the log content rather than from when it was written.
 * <p>
 * The system under test must include the correlation identifier in each log
 * line, e.g. via an MDC field in its logging pattern.
 */
public class CorrelatedTail implements CorrelatedCapture {

	private static final String CORRELATION_GROUP = "correlation";
	private static final String SOURCE_GROUP = "source";
	private static final String LEVEL_GROUP = "level";
	private static final String TIME_GROUP = "time";
	/** Source problems reported before further ones are dropped */
	private static final int REPORTED_PROBLEMS = 20;
	private static final Logger LOG = Logger.getLogger( CorrelatedTail.class.getName() );

	private final Path file;
	private final Pattern pattern;
	private int readLimit = 1 << 20;

	private Collector collector;
	/** Next byte to read */
	private long offset;
	/** Bytes of an unterminated final line, awaiting the rest */
	private byte[] carry = new byte[0];
	/** Header of the most recent event, for continuation lines in a later read */
	private String[] lastHeader;
	private int reportedProblems;

	/**
	 * @param file    The path to the file to extract from
	 * @param pattern A regular expression that matches the <em>start</em> of a new
	 *                log line and captures named groups <code>time</code>,
	 *                <code>level</code>, <code>source</code> and
	 *                <code>correlation</code>. It is matched at the beginning of
	 *                each line only (as if anchored with <code>^</code>), so a line
	 *                is scanned once whether or not the pattern is anchored. The
	 *                remaining content of the line and following lines until the
	 *                next match are the event content. An empty
	 *                <code>correlation</code> capture means the event carries no
	 *                identifier.
	 */
	public CorrelatedTail( Path file, String pattern ) {
		for( String group : new String[] { TIME_GROUP, LEVEL_GROUP, SOURCE_GROUP,
				CORRELATION_GROUP } ) {
			if( !pattern.contains( "(?<" + group + ">" ) ) {
				throw new IllegalArgumentException(
						"Pattern must capture a named group '" + group + "'" );
			}
		}
		this.file = file;
		this.pattern = Pattern.compile( pattern );
	}

	/**
	 * Bounds the bytes read from the file in a single {@link #flush()}. Unread
	 * bytes are read by later flushes and by {@link #close()}; nothing is skipped.
	 * The default is 1 MiB.
	 *
	 * @param bytes The maximum bytes to read per flush
	 * @return <code>this</code>
	 */
	public CorrelatedTail readLimit( int bytes ) {
		if( bytes < 1 ) {
			throw new IllegalArgumentException( "Read limit must be positive" );
		}
		readLimit = bytes;
		return this;
	}

	@Override
	public void open( Collector c ) {
		collector = c;
		try {
			offset = Files.exists( file ) ? Files.size( file ) : 0;
		}
		catch( IOException e ) {
			problem( "open: " + e );
			offset = 0;
		}
	}

	@Override
	public void flush() {
		read( false );
	}

	@Override
	public void close() {
		// Everything that exists at the boundary is delivered, terminated or not
		while( read( true ) ) {
			// bounded by the file's size at the cut
		}
		if( carry.length > 0 ) {
			deliver( Arrays.asList( new String( carry, UTF_8 ).split( "\n", -1 ) ) );
			carry = new byte[0];
		}
		collector = null;
	}

	/**
	 * @param drain Whether the caller wants to be told that more remains
	 * @return true if the read hit the limit and more bytes remain
	 */
	private boolean read( boolean drain ) {
		if( collector == null ) {
			return false;
		}
		try {
			long size = Files.size( file );
			if( size < offset ) {
				problem( String.format( "truncated: size %d is below read offset %d; restarting", size,
						offset ) );
				offset = 0;
				carry = new byte[0];
				lastHeader = null;
			}
			int length = (int) Math.min( size - offset, readLimit );
			if( length <= 0 ) {
				return false;
			}
			byte[] data = new byte[carry.length + length];
			System.arraycopy( carry, 0, data, 0, carry.length );
			try( RandomAccessFile raf = new RandomAccessFile( file.toFile(), "r" ) ) {
				raf.seek( offset );
				raf.readFully( data, carry.length, length );
			}
			offset += length;
			// Only complete lines are decoded: the writer may be mid-line, and a
			// multi-byte character may straddle this read's boundary.
			int end = data.length;
			while( end > 0 && data[end - 1] != '\n' ) {
				end--;
			}
			carry = Arrays.copyOfRange( data, end, data.length );
			if( end > 0 ) {
				String text = new String( data, 0, end - 1, UTF_8 );
				deliver( Arrays.asList( text.split( "\n", -1 ) ) );
			}
			if( carry.length > readLimit ) {
				// A single line longer than a whole read: deliver what we have as a fragment
				// so that memory stays bounded and the collector's budgets engage. The rest
				// arrives as a continuation on a later read.
				int cut = characterBoundary( carry );
				deliver( List.of( new String( carry, 0, cut, UTF_8 ) ) );
				carry = Arrays.copyOfRange( carry, cut, carry.length );
			}
			return drain && size - offset > 0;
		}
		catch( IOException e ) {
			problem( "read: " + e );
			return false;
		}
	}

	private void deliver( List<String> lines ) {
		String[] header = null;
		StringBuilder content = new StringBuilder();
		for( String line : lines ) {
			Matcher m = pattern.matcher( line );
			// lookingAt, not find: a header is only ever at the line start, and find()
			// would retry from every offset of long body lines (quadratic per flush).
			if( m.lookingAt() ) {
				emit( header, content );
				header = new String[] { m.group( CORRELATION_GROUP ), m.group( TIME_GROUP ),
						m.group( LEVEL_GROUP ), m.group( SOURCE_GROUP ) };
				lastHeader = header;
				content.append( uncapturedContent( line, m ) );
			}
			else if( header != null ) {
				content.append( '\n' ).append( line );
			}
			else if( lastHeader != null ) {
				// continuation of an event whose header was delivered by an earlier read
				header = lastHeader;
				content.append( line );
			}
			else if( !line.isEmpty() ) {
				// content before any recognisable event, likely a pattern mismatch: it
				// must not vanish, but it cannot be attributed
				collector.accept( null, new LogEvent( "?", "?", "?", line ) );
			}
		}
		emit( header, content );
	}

	private void emit( String[] header, StringBuilder content ) {
		if( header != null ) {
			String correlation = header[0] == null || header[0].isEmpty() ? null : header[0];
			collector.accept( correlation,
					new LogEvent( header[1], header[2], header[3], content.toString() ) );
			content.setLength( 0 );
		}
	}

	/**
	 * Problems with the source file cannot be attributed to a flow, so they are
	 * reported like the runner's own diagnostics, up to a limit.
	 */
	private void problem( String description ) {
		if( reportedProblems++ < REPORTED_PROBLEMS ) {
			LOG.warning( () -> "Log capture source " + file + ": " + description );
		}
	}

	/**
	 * @param bytes UTF-8 content
	 * @return The largest index at or before the end that does not split a
	 *         multi-byte sequence
	 */
	private static int characterBoundary( byte[] bytes ) {
		int lead = bytes.length - 1;
		while( lead > 0 && (bytes[lead] & 0xC0) == 0x80 ) {
			lead--;
		}
		int b = bytes[lead] & 0xFF;
		int width;
		if( b < 0x80 ) {
			width = 1;
		}
		else if( b >= 0xF0 ) {
			width = 4;
		}
		else if( b >= 0xE0 ) {
			width = 3;
		}
		else {
			width = 2;
		}
		return bytes.length - lead >= width ? bytes.length : lead;
	}

	private static String uncapturedContent( String line, Matcher m ) {
		String[] groups = { CORRELATION_GROUP, TIME_GROUP, LEVEL_GROUP, SOURCE_GROUP };
		int[] ranges = new int[groups.length * 2];
		int captured = 0;
		for( String group : groups ) {
			// a group that did not participate in the match (start -1) captures nothing
			if( m.start( group ) >= 0 ) {
				ranges[captured++] = m.start( group );
				ranges[captured++] = m.end( group );
			}
		}
		Arrays.sort( ranges, 0, captured );
		StringBuilder unmatched = new StringBuilder();
		int from = 0;
		for( int i = 0; i < captured; i += 2 ) {
			unmatched.append( line, from, ranges[i] );
			from = ranges[i + 1];
		}
		unmatched.append( line, from, line.length() );
		return unmatched.toString().trim();
	}
}
