package com.mastercard.test.flow.report.duct;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.report.Reader;

/**
 * Finds reports on the filesystem
 */
class Search {

	private static final Path SRC_MAIN_JAVA = Paths.get( "src", "main", "java" );
	private static final Path SRC_TEST_JAVA = Paths.get( "src", "test", "java" );

	private Search() {
		// no instances
	}

	/**
	 * Walks a directory structure to find the reports it contains
	 *
	 * @param root The directory to search
	 * @return A stream of the report paths that lie under the directory
	 */
	static Stream<Path> find( Path root ) {
		ReportForager rf = new ReportForager();
		try {
			Files.walkFileTree( root, rf );
		}
		catch( IOException e ) {
			throw new UncheckedIOException( "Search of " + root + "failed", e );
		}
		return rf.reports();
	}

	private static class ReportForager extends SimpleFileVisitor<Path> {

		private static final Logger LOG = LoggerFactory.getLogger( ReportForager.class );

		private List<Path> found = new ArrayList<>();

		ReportForager() {
		}

		@Override
		public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs )
				throws IOException {

			if( Reader.isReportDir( dir ) ) {
				found.add( dir );
				// we're not expecting nested reports, so don't look deeper
				return FileVisitResult.SKIP_SUBTREE;
			}

			// these directory structures tend to be very large and devoid of execution
			// reports. Let's skip them.
			if( isJavaSourceRoot( dir ) ) {
				LOG.info( "Skipping java source tree {}", dir );
				return FileVisitResult.SKIP_SUBTREE;
			}
			if( isNodeModules( dir ) ) {
				LOG.info( "Skipping node modules tree {}", dir );
				return FileVisitResult.SKIP_SUBTREE;
			}

			return super.preVisitDirectory( dir, attrs );
		}

		public Stream<Path> reports() {
			return found.stream();
		}
	}

	/**
	 * @param path a path
	 * @return <code>true</code> if that path looks like it might be the root of a
	 *         java source tree
	 */
	static boolean isJavaSourceRoot( Path path ) {
		return path.endsWith( SRC_MAIN_JAVA )
				|| path.endsWith( SRC_TEST_JAVA );
	}

	/**
	 * @param path a path
	 * @return <code>true</code> if that path looks like it might contain NPM
	 *         dependencies
	 */
	static boolean isNodeModules( Path path ) {
		return path.endsWith( "node_modules" );
	}

}
