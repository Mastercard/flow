package com.mastercard.test.flow.doc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;

/**
 * Utilities for working with markdown documents
 */
class Util {

	private static final Map<String, List<Path>> FILES_CACHE = new HashMap<>();

	/**
	 * @param line The line number
	 * @return A link fragment to highlight that line number
	 */
	static String lineFragment( int line ) {
		// Bitbucket links will be broken pending
		// https://jira.atlassian.com/browse/BSERV-13422
		return String.format( "#L%s,%s", line, line );
	}

	/**
	 * @param from The lower line number
	 * @param to   The higher line number
	 * @return A link fragment to highlight that line range
	 */
	static String lineFragment( int from, int to ) {
		// Bitbucket links will be broken pending
		// https://jira.atlassian.com/browse/BSERV-13422
		return String.format( "#L%s-L%s,%s-%s", from, to, from, to );
	}

	/**
	 * @return A stream of all markdown files in the project
	 */
	static Stream<Path> markdownFiles() {
		return files( "markdown", ".md",
				".git", "node_modules", "src/main/java", "src/test/java" );
	}

	/**
	 * @return A stream of all markdown files in the project
	 */
	static Stream<Path> javaFiles() {
		return files( "java", ".java",
				".git", "node_modules" );
	}

	/**
	 * @return A stream of all XML files in the project
	 */
	static Stream<Path> xmlFiles() {
		return files( "xml", ".xml",
				".git", "node_modules" );
	}

	/**
	 * @param cacheKey A key under which to cache the results
	 * @param suffix   The file suffix to search for
	 * @param ignore   A set of path elements that we're not interested in
	 * @return A stream of all files in the project with the given suffix that do
	 *         not have an ignored pattern in their paths
	 */
	private static Stream<Path> files( String cacheKey, String suffix, String... ignore ) {
		if( !FILES_CACHE.containsKey( cacheKey ) ) {
			try {
				SuffixForager sh = new SuffixForager( suffix, ignore );
				Files.walkFileTree( Paths.get( ".." ), sh );
				FILES_CACHE.put( cacheKey, sh.files() );
			}
			catch( IOException ioe ) {
				throw new UncheckedIOException( ioe );
			}
		}
		return FILES_CACHE.get( cacheKey ).stream();
	}

	/**
	 * Walks the source tree to look for files with a particular suffix
	 */
	static class SuffixForager extends SimpleFileVisitor<Path> {
		private final String suffix;
		private final Set<String> ignore;
		private List<Path> found = new ArrayList<>();

		/**
		 * @param suffix The file suffix to look for
		 * @param ignore File path substrings to ignore
		 */
		SuffixForager( String suffix, String... ignore ) {
			this.suffix = suffix;
			this.ignore = new TreeSet<>( Arrays.asList( ignore ) );
		}

		@Override
		public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs )
				throws IOException {
			// there are a few large trees where java content is unlikely
			String path = dir.toString();
			if( ignore.stream().anyMatch( i -> path.contains( i ) ) ) {
				// so let's not plumb their depths
				return FileVisitResult.SKIP_SUBTREE;
			}
			return super.preVisitDirectory( dir, attrs );
		}

		@Override
		public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
			if( file.getFileName().toString().endsWith( suffix ) ) {
				found.add( file );
			}
			return super.visitFile( file, attrs );
		}

		/**
		 * @return The files we found on our walk
		 */
		List<Path> files() {
			return found;
		}
	}

	/**
	 * Regenerates a section of a file and throws a failed comparison test if the
	 * file was altered by that act.
	 *
	 * @param path    The file to operate on
	 * @param start   The line at which to insert the content
	 * @param content How to mutate the existing content of the section
	 * @param end     The line at which the content ends
	 * @throws Exception IO failure
	 */
	static void insert( Path path, String start, UnaryOperator<String> content, String end )
			throws Exception {

		String existing = "";
		if( Files.exists( path ) ) {
			existing = new String( Files.readAllBytes( path ), UTF_8 );
		}
		List<String> regenerated = new ArrayList<>();

		Iterator<String> exi = Arrays.asList( existing.split( "\n", -1 ) ).iterator();
		while( exi.hasNext() ) {
			boolean found = false;
			while( exi.hasNext() && !found ) {
				String line = exi.next();
				if( line.trim().equals( start ) ) {
					found = true;
					break;
				}
				regenerated.add( line );
			}
			if( found ) {
				StringBuilder existingContent = new StringBuilder();
				boolean endFound = false;
				while( exi.hasNext() && !endFound ) {
					String line = exi.next();
					if( line.trim().equals( end ) ) {
						endFound = true;
					}
					else {
						existingContent.append( line ).append( "\n" );
					}
				}

				// add the new content
				regenerated.add( start );
				regenerated.add( "" );
				regenerated.add( content.apply( existingContent.toString().trim() ) );
				regenerated.add( "" );
				regenerated.add( end );
			}
		}

		// write the file
		String newContent = regenerated.stream().collect( Collectors.joining( "\n" ) );
		Files.write( path, newContent.getBytes( UTF_8 ) );

		Assertions.assertEquals( existing, newContent,
				path + " has been updated and needs to be committed to git" );
	}

	/**
	 * @param fragment A fragment that unambiguously identifies a java class or XML
	 *                 file path
	 * @return The full path to that source file
	 */
	static Path sourceFileFor( String fragment ) {
		if( fragment.endsWith( ".xml" ) ) {
			return xmlSourceFileFor( fragment );
		}
		return javaSourceFileFor( fragment );
	}

	/**
	 * @param pathFragment A fragment of a path to an XML file, sufficient to
	 *                     unambiguously find the referenced file
	 * @return The full path to the XML file
	 */
	static Path xmlSourceFileFor( String pathFragment ) {
		List<Path> matches = xmlFiles()
				.filter( p -> p.toString().replace( '\\', '/' ).contains( pathFragment ) )
				.collect( toList() );

		if( matches.size() != 1 ) {
			throw new IllegalArgumentException(
					"Failed to find unambiguous source file for '" + pathFragment
							+ "'. Found " + matches );
		}

		return matches.get( 0 );
	}

	/**
	 * @param classNameFragment a portion of a class name, sufficient to
	 *                          unambiguously find the class source file
	 * @return The source file
	 */
	static Path javaSourceFileFor( String classNameFragment ) {
		boolean exactMatch = false;
		String cnf = classNameFragment;
		if( cnf.endsWith( "!" ) ) {
			exactMatch = true;
			cnf = cnf.substring( 0, cnf.length() - 1 );
		}
		String path = cnf.replace( '.', '/' );
		List<Path> matches = javaFiles()
				.filter( p -> p.toString().replace( '\\', '/' ).contains( path ) )
				.collect( toList() );

		// avoid matches against tests, which usually have superset names
		if( matches.size() > 1 && !cnf.endsWith( "Test" ) ) {
			matches = matches.stream()
					.filter( p -> !p.getFileName().toString().endsWith( "Test.java" ) )
					.collect( toList() );
		}

		if( exactMatch ) {
			String fn = cnf + ".java";
			matches = matches.stream()
					.filter( p -> p.getFileName().toString().equals( fn ) )
					.collect( toList() );
		}

		if( matches.size() != 1 ) {
			throw new IllegalArgumentException(
					"Failed to find unambiguous source file for '" + classNameFragment
							+ "'. Found " + matches );
		}

		return matches.get( 0 );
	}

}
