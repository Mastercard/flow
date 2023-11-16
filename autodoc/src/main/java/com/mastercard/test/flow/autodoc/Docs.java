package com.mastercard.test.flow.autodoc;

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

/**
 * Utilities for inspecting files in the project and working with markdown
 * documents
 */
public class Docs {

	private static final Map<String, List<Path>> FILES_CACHE = new HashMap<>();

	private final Path root;
	private final Host host;
	private final Assert assrt;

	/**
	 * Allows control over how line number link fragments are formatted.
	 */
	public enum Host {
		/**
		 * Creates line links that work on github
		 */
		GITHUB("#L%s", "#L%s-L%s"),
		/**
		 * Creates line links that work on bitbucket stash
		 */
		BITBUCKET("#%s", "#%s-%s");

		private final String lineFormat;
		private final String rangeFormat;

		Host( String lineFormat, String rangeFormat ) {
			this.lineFormat = lineFormat;
			this.rangeFormat = rangeFormat;
		}

		/**
		 * Creates a link fragment for a single line
		 *
		 * @param line The line number
		 * @return a link fragment that should result in that line being highlighted
		 */
		public String lineFragment( int line ) {
			return String.format( lineFormat, line );
		}

		/**
		 * Creats a link fragment for a line range
		 *
		 * @param start The line number of the start of the range (inclusive)
		 * @param end   The line number of the end of the range (inclusive)
		 * @return a link fragment that should result in that line range being
		 *         highlighted
		 */
		public String rangeFragment( int start, int end ) {
			return String.format( rangeFormat, start, end );
		}
	}

	/**
	 * @param root  The project root path
	 * @param host  Where the documents will be hosted
	 * @param assrt How to assert object equality
	 */
	public Docs( String root, Host host, Assert assrt ) {
		this.root = Paths.get( root );
		this.host = host;
		this.assrt = assrt;
	}

	/**
	 * @param line The line number
	 * @return A link fragment to highlight that line number
	 */
	public String lineFragment( int line ) {
		return host.lineFragment( line );
	}

	/**
	 * @param from The lower line number
	 * @param to   The higher line number
	 * @return A link fragment to highlight that line range
	 */
	public String lineFragment( int from, int to ) {
		return host.rangeFragment( from, to );
	}

	/**
	 * Checks that two objects are equal
	 *
	 * @param expected    What we expect to find
	 * @param actual      What we actually find
	 * @param description A human-readable description for the assertion
	 */
	public void assertEquals( Object expected, Object actual, String description ) {
		assrt.assertEquals( expected, actual, description );
	}

	/**
	 * @return A stream of all markdown files in the project
	 */
	public Stream<Path> markdownFiles() {
		return files( "markdown", ".md",
				".git", "node_modules", "src/main/java", "src/test/java" );
	}

	/**
	 * @return A stream of all java source files in the project
	 */
	public Stream<Path> javaFiles() {
		return files( "java", ".java",
				".git", "node_modules" );
	}

	/**
	 * @return A stream of all XML files in the project
	 */
	public Stream<Path> xmlFiles() {
		return files( "xml", ".xml",
				".git", "node_modules" );
	}

	/**
	 * @return A stream of all component HTML templates in the project
	 */
	public Stream<Path> componentTemplateFiles() {
		return files( "component_template", ".component.html",
				".git", "node_modules" );
	}

	/**
	 * @return A stream of all typescript files in the project
	 */
	public Stream<Path> typescriptFiles() {
		return files( "typescript", ".ts",
				".git", "node_modules" );
	}

	/**
	 * @param cacheKey A key under which to cache the results
	 * @param suffix   The file suffix to search for
	 * @param ignore   A set of path elements that we're not interested in
	 * @return A stream of all files in the project with the given suffix that do
	 *         not have an ignored pattern in their paths
	 */
	public Stream<Path> files( String cacheKey, String suffix, String... ignore ) {
		if( !FILES_CACHE.containsKey( cacheKey ) ) {
			try {
				SuffixForager sh = new SuffixForager( suffix, ignore );
				Files.walkFileTree( root, sh );
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
			if( dir.endsWith( "target" )
					&& Files.list( dir.getParent() ).anyMatch( p -> p.endsWith( "pom.xml" ) ) ) {
				// we never want to look in the target dir
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
	public void insert( Path path, String start, UnaryOperator<String> content, String end )
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

		assrt.assertEquals( existing, newContent,
				path + " has been updated and needs to be committed to git" );
	}

	/**
	 * @param fragment A fragment that unambiguously identifies a java class or XML
	 *                 file path
	 * @return The full path to that source file
	 */
	public Path sourceFileFor( String fragment ) {
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
	public Path xmlSourceFileFor( String pathFragment ) {
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
	public Path javaSourceFileFor( String classNameFragment ) {
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
