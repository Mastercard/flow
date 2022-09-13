
package com.mastercard.test.flow.assrt.log;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Extracts log data from a file on the local system
 */
public class Tail implements LogCapture {

	private static final String SOURCE_CAPTURE_GROUP = "source";
	private static final String LEVEL_CAPTURE_GROUP = "level";
	private static final String TIME_CAPTURE_GROUP = "time";

	private final Path file;
	private final Pattern pattern;

	private BiConsumer<String, IOException> errorHandler = ( msg, e ) -> {
		// default to silently dropping failures
	};

	/**
	 * Maps from {@link Flow}s to the size of the file when {@link #start(Flow)} was
	 * called
	 */
	private final Map<Flow, Long> startSizes = new HashMap<>();

	/**
	 * @param file    The path to the file to extract from
	 * @param pattern A regular expression that matches the start of a new log line
	 *                and captures the event time, level and source (capture groups
	 *                named <code>time</code>, <code>level</code> and
	 *                <code>source</code> are assumed to exist). The remaining
	 *                content of the line and the following lines until the start of
	 *                a new log event are used as event content.
	 */
	public Tail( Path file, String pattern ) {
		this.file = file;
		this.pattern = Pattern.compile( pattern );
	}

	/**
	 * Sets the error handling behaviour. The default behaviour is to silently
	 * ignore errors.
	 *
	 * @param handler How to deal with failures
	 * @return <code>this</code>
	 */
	public Tail errors( BiConsumer<String, IOException> handler ) {
		errorHandler = handler;
		return this;
	}

	@Override
	public void start( Flow flow ) {
		try {
			startSizes.put( flow, Files.size( file ) );
		}
		catch( IOException ioe ) {
			errorHandler.accept( "Failed to get start size", ioe );
		}
	}

	@Override
	public Stream<LogEvent> end( Flow flow ) {
		List<LogEvent> events = new ArrayList<>();
		String[] lines = extractLineRange( flow );

		String time = null;
		String level = null;
		String source = null;
		StringBuilder content = new StringBuilder();

		for( String line : lines ) {
			Matcher m = pattern.matcher( line );

			if( m.find() ) {
				// we've found an event!
				if( time != null ) {
					// the previous event is complete
					events.add( new LogEvent( time, level, source, content.toString() ) );
					content.delete( 0, content.length() );
				}
				time = m.group( TIME_CAPTURE_GROUP );
				level = m.group( LEVEL_CAPTURE_GROUP );
				source = m.group( SOURCE_CAPTURE_GROUP );
				content.append( uncapturedContent( line, m,
						TIME_CAPTURE_GROUP, LEVEL_CAPTURE_GROUP, SOURCE_CAPTURE_GROUP ) );
			}
			else if( time != null ) {
				// It's not the start of a new event, but we have found an event previously
				// Assume this line is still content from the previously-found event
				content.append( "\n" ).append( line );
			}
			else if( !line.isEmpty() ) {
				// we've not found an event yet but we *are* seeing content. This is likely to
				// happen when the event-finding pattern is incorrect. We can't just ignore the
				// line, so add content-only events
				events.add( new LogEvent( "?", "?", "?", line ) );
			}
		}

		if( time != null ) {
			// the final event is complete
			events.add( new LogEvent( time, level, source, content.toString() ) );
			content.delete( 0, content.length() );
		}

		return events.stream();
	}

	private String[] extractLineRange( Flow flow ) {
		try {
			long start = startSizes.getOrDefault( flow, 0L );
			long end = Files.size( file );
			byte[] data = new byte[(int) (end - start)];
			try( RandomAccessFile raf = new RandomAccessFile( file.toFile(), "r" ) ) {
				raf.seek( start );
				raf.readFully( data );
			}
			return new String( data, UTF_8 ).split( "\n" );
		}
		catch( IOException ioe ) {
			errorHandler.accept( "Failed to extract content", ioe );
		}
		return new String[0];
	}

	private static String uncapturedContent( String line, Matcher m, String... groups ) {
		int[] ranges = new int[groups.length * 2];
		for( int i = 0; i < groups.length; i++ ) {
			ranges[i * 2] = m.start( groups[i] );
			ranges[i * 2 + 1] = m.end( groups[i] );
		}
		// capture groups cannot overlap, so sorting the start and end indices in one
		// array is fine
		Arrays.sort( ranges );

		StringBuilder unmatched = new StringBuilder();
		// everything before the first group
		unmatched.append( line.substring( 0, ranges[0] ) );
		// the stuff between each group
		for( int i = 1; i < ranges.length - 1; i += 2 ) {
			unmatched.append( line.substring( ranges[i], ranges[i + 1] ) );
		}
		// everything after the last group
		unmatched.append( line.substring( ranges[ranges.length - 1] ) );

		return unmatched.toString().trim();
	}
}
