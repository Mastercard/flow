package com.mastercard.test.flow.assrt.log;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
	/** Recorded source problems kept for the run summary */
	private static final int RETAINED_PROBLEMS = 20;

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
	private final List<String> problems = new ArrayList<>();
	private int omittedProblems;

	/**
	 * @param file    The path to the file to extract from
	 * @param pattern A regular expression that matches the start of a new log line
	 *                and captures named groups <code>time</code>,
	 *                <code>level</code>, <code>source</code> and
	 *                <code>correlation</code>. The remaining content of the line
	 *                and following lines until the next match are the event
	 *                content. An empty <code>correlation</code> capture means the
	 *                event carries no identifier.
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

	@Override
	public String summary() {
		StringBuilder sb = new StringBuilder();
		problems.forEach( p -> sb.append( "source " ).append( file ).append( ": " ).append( p )
				.append( '\n' ) );
		if( omittedProblems > 0 ) {
			sb.append( "source " ).append( file ).append( ": " ).append( omittedProblems )
					.append( " further problems\n" );
		}
		return sb.toString();
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
			if( m.find() ) {
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

	private void problem( String description ) {
		if( problems.size() < RETAINED_PROBLEMS ) {
			problems.add( description );
		}
		else {
			omittedProblems++;
		}
	}

	private static String uncapturedContent( String line, Matcher m ) {
		String[] groups = { CORRELATION_GROUP, TIME_GROUP, LEVEL_GROUP, SOURCE_GROUP };
		int[] ranges = new int[groups.length * 2];
		for( int i = 0; i < groups.length; i++ ) {
			ranges[i * 2] = m.start( groups[i] );
			ranges[i * 2 + 1] = m.end( groups[i] );
		}
		Arrays.sort( ranges );
		StringBuilder unmatched = new StringBuilder( line.substring( 0, ranges[0] ) );
		for( int i = 1; i < ranges.length - 1; i += 2 ) {
			unmatched.append( line.substring( ranges[i], ranges[i + 1] ) );
		}
		unmatched.append( line.substring( ranges[ranges.length - 1] ) );
		return unmatched.toString().trim();
	}
}
