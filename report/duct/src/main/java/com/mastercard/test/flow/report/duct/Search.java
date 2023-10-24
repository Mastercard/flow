package com.mastercard.test.flow.report.duct;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.mastercard.test.flow.report.Reader;

/**
 * Finds reports on the filesystem
 */
class Search {

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
			throw new UncheckedIOException( "Failed to scan " + root, e );
		}
		return rf.reports();
	}

	private static class ReportForager extends SimpleFileVisitor<Path> {
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

			// TODO: avoid scanning into java project source and node_modules trees

			return super.preVisitDirectory( dir, attrs );
		}

		public Stream<Path> reports() {
			return found.stream();
		}
	}
}
