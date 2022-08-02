package com.mastercard.test.flow.report;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;

/**
 * For reading an existing report
 */
public class Reader {

	private final URI uri;

	/**
	 * @param dir The directory to read from
	 */
	public Reader( Path dir ) {
		this( dir.toUri() );
	}

	/**
	 * @param uri The URI of the directory to read from
	 */
	public Reader( URI uri ) {
		this.uri = uri;
	}

	/**
	 * Reads the report index
	 *
	 * @return the report index, or <code>null</code> if there is no data
	 */
	public Index read() {
		return extract( uri.resolve( Writer.INDEX_FILE_NAME ), Index.class );
	}

	/**
	 * Reads {@link Flow} detail data
	 *
	 * @param e An index entry
	 * @return The {@link FlowData} for the supplied {@link Entry}, or
	 *         <code>null</code> if there was no data to read
	 */
	public FlowData detail( Entry e ) {
		return extract(
				uri.resolve( Writer.DETAIL_DIR_NAME + "/" + e.detail + ".html" ),
				FlowData.class );
	}

	/**
	 * Determines if a report {@link Entry} matches a {@link Flow}
	 *
	 * @param entry The report {@link Index} {@link Entry}
	 * @param flow  The {@link Flow} to compare against
	 * @return <code>true</code> if the descriptions and tags are the same
	 *         (discounting the {@link Writer#RESULT_TAGS}
	 */
	public static boolean matches( Entry entry, Flow flow ) {
		if( entry.description.equals( flow.meta().description() ) ) {
			Set<String> et = new HashSet<>( entry.tags );
			Set<String> mt = new HashSet<>( flow.meta().tags() );

			et.removeAll( Writer.RESULT_TAGS );
			mt.removeAll( Writer.RESULT_TAGS );

			return et.equals( mt );
		}
		return false;
	}

	private static <T> T extract( URI uri, Class<T> type ) {
		try( BufferedReader br = new BufferedReader(
				new InputStreamReader( uri.toURL().openStream() ) ) ) {
			String file = br.lines().collect( joining( "\n" ) );
			return Template.extract( file, type );
		}
		catch( @SuppressWarnings("unused") FileNotFoundException fnfe ) {
			return null;
		}
		catch( IOException | UncheckedIOException e ) {
			throw new IllegalStateException( "Failed to read " + uri, e );
		}
	}

	/**
	 * Searches the a directory for the most recent execution report that satisfies
	 * a constraint
	 *
	 * @param dir    The directory to search in
	 * @param filter Additional search constraint
	 * @return The most recent execution report
	 */
	public static final Path mostRecent( String dir, Predicate<Path> filter ) {
		Path path = Paths.get( dir );
		if( !Files.exists( path ) || !Files.isDirectory( path ) ) {
			return null;
		}

		try( Stream<Path> walk = Files.walk( path ) ) {
			return walk
					.filter( Reader::isReportDir )
					.filter( filter )
					.min( Reader.MOST_RECENT )
					.orElse( null );
		}
		catch( IOException ioe ) {
			throw new IllegalStateException(
					"Failed to find execution report in " + dir,
					ioe );
		}
	}

	/**
	 * @param p A path
	 * @return <code>true</code> if the supplied path looks like an execution report
	 */
	public static final boolean isReportDir( Path p ) {
		try {
			if( Files.isDirectory( p ) ) {
				try( Stream<Path> s = Files.list( p ) ) {
					Set<Path> contents = s.collect( toSet() );
					boolean hasIndex = contents.stream()
							.anyMatch( f -> Files.isRegularFile( f )
									&& Writer.INDEX_FILE_NAME.equals( f.getFileName().toString() ) );
					boolean hasDetailDir = contents.stream()
							.anyMatch( f -> Files.isDirectory( p )
									&& Writer.DETAIL_DIR_NAME.equals( f.getFileName().toString() ) );

					return hasIndex && hasDetailDir;
				}
			}
		}
		catch( IOException ioe ) {
			throw new IllegalStateException( "Failed to detect reportiness of " + p, ioe );
		}
		return false;
	}

	/**
	 * Compares paths based on their modified time, sorting most-recent first
	 */
	public static final Comparator<Path> MOST_RECENT = ( a, b ) -> {
		try {
			FileTime at = Files.getLastModifiedTime( a );
			FileTime bt = Files.getLastModifiedTime( b );
			// note we've flip-reversed it to get latest-first
			return bt.compareTo( at );
		}
		catch( @SuppressWarnings("unused") IOException ioe ) {
			return 0;
		}
	};
}
