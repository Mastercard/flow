package com.mastercard.test.flow.doc;

import static com.mastercard.test.flow.doc.Util.lineFragment;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * <p>
 * Copies sections of source files into readmes to keep code snippets fresh.
 * </p>
 * <p>
 * Use it by delimiting the snippet you want in the source file with:
 * </p>
 *
 * <pre>
 * public class MyClass {
 *   // snippet-start:my_snippet_name
 *   blah blah blah
 *   // snippet-end:my_snippet_name
 * }
 * </pre>
 * <p>
 * Then including it in a markdown file with:
 *
 * <pre>
 * &lt;!-- snippet start --&gt;
 * &lt;!-- MyClass:my_snippet_name --&gt;
 * blah blah blah
 * &lt;!-- snippet end --&gt;
 * </pre>
 * <p>
 * <p>
 * This test will ensure that the snippet content in the markdown file matches
 * the source file, and add a link to the snippet context from the markdown.
 * </p>
 * <p>
 * If there are multiple classes with the same name then you may have to add
 * enough of the fully-qualified class name to resolve the ambiguity.
 * </p>
 */
// This test will fail in the middle of release preparation as all the
// ${project.version} usages get transformed into the actual version
@Tag("avoid_on_release")
class SnippetTest {

	private static final String MD_START = "<!-- snippet start -->";
	private static final String MD_END = "<!-- snippet end -->";
	private static final Pattern MD_SNIPPET_DESCRIPTOR = Pattern.compile( "<!-- (.*):(.*) -->" );

	private static final Map<Path, UnaryOperator<String>> MUTATIONS;
	static {
		Map<Path, UnaryOperator<String>> m = new HashMap<>();
		// Ideally we'd have a
		// <properties> <flow.version>${project.version}</flow.version> </properties>
		// in the pom so that the dependencies are consistent with how they'd be
		// declared in an actual client project.
		// https://issues.apache.org/jira/browse/MRELEASE-799 scuppers that, so we're
		// reduced to mutating the snippets as they are extracted. The markdown will
		// look ok, but it'll be inconsistent with the actual pom file.
		// I'm unhappy about this.
		m.put( Paths.get( "pom.xml" ).getFileName(),
				s -> s.replace( "${project.version}", "${flow.version}" ) );
		MUTATIONS = Collections.unmodifiableMap( m );
	}

	private static final Map<String, Delimiter> SOURCE_DELIMITERS;
	static {
		Map<String, Delimiter> m = new HashMap<>();
		m.put( ".java", new Delimiter(
				"// snippet-start:(.*)", "// snippet-end:(.*)" ) );
		m.put( ".xml", new Delimiter(
				"<!-- snippet-start:(.*) -->", "<!-- snippet-end:(.*) -->" ) );
		SOURCE_DELIMITERS = Collections.unmodifiableMap( m );
	}

	private final Map<String, Snippet> excerpts = new HashMap<>();

	/**
	 * Checks that code snippets in readmes are up-to-date
	 *
	 * @return tests
	 * @throws Exception If the file walk fails
	 */
	@TestFactory
	Stream<DynamicTest> markdown() throws Exception {
		return Util.markdownFiles()
				.map( mdFile -> dynamicTest( mdFile.toString(),
						() -> Util.insert( mdFile,
								MD_START,
								existing -> refresh( mdFile.getParent(), existing ),
								MD_END ) ) );
	}

	private String refresh( Path dir, String existing ) {
		Matcher m = MD_SNIPPET_DESCRIPTOR.matcher( existing );
		if( m.find() ) {
			Path src = Util.sourceFileFor( m.group( 1 ) );
			Snippet e = extract( src, m.group( 2 ) );
			return String.format( ""
					+ "%s\n"
					+ "\n"
					+ "```%s\n"
					+ "%s\n"
					+ "```\n"
					+ "%s",
					m.group(),
					e.type(),
					e.content(),
					e.link( "Snippet context", dir ) );
		}
		return "No excerpt descriptor found!";
	}

	private static Delimiter delimiter( Path file ) {
		String fn = file.getFileName().toString();
		return SOURCE_DELIMITERS.entrySet().stream()
				.filter( e -> fn.endsWith( e.getKey() ) )
				.findFirst()
				.map( Entry::getValue )
				.orElseThrow( () -> new IllegalArgumentException(
						"No delimiters found for file '" + fn + "'" ) );
	}

	private Snippet extract( Path file, String name ) {
		try {
			String key = file + ":" + name;
			Snippet ex = excerpts.get( key );
			if( ex == null ) {

				String content = new String( Files.readAllBytes( file ), UTF_8 );
				Iterator<String> lines = Arrays.asList( content.split( "\n", -1 ) ).iterator();
				Map<String, Snippet> extant = new HashMap<>();
				int lineNumber = 0;

				while( lines.hasNext() ) {
					lineNumber++;
					String line = MUTATIONS.getOrDefault( file.getFileName(), s -> s )
							.apply( lines.next() );

					Delimiter sd = delimiter( file );
					Matcher start = sd.start.matcher( line.trim() );
					Matcher end = sd.end.matcher( line.trim() );
					if( start.matches() ) {

						extant.put( start.group( 1 ), new Snippet( file, lineNumber + 1 ) );
					}
					else if( end.matches() ) {

						excerpts.put( file + ":" + end.group( 1 ),
								extant.remove( end.group( 1 ) ) );
					}
					else {
						extant.values().forEach( e -> e.append( line ) );
					}
				}

				extant.forEach( ( n, e ) -> excerpts.put( file + ":" + n, e ) );
			}

			ex = excerpts.get( key );
			if( ex == null ) {
				return new Snippet( file, 0 ).append( "No such excerpt!" );
			}
			return ex;
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	private static class Snippet {
		private final Path file;

		private final int startLine;
		private int endLine;
		private final List<String> lines = new ArrayList<>();
		private String sharedWhitespacePrefix = null;

		Snippet( Path file, int startLine ) {
			this.file = file;

			this.startLine = startLine;
			endLine = startLine;
		}

		Snippet append( String line ) {
			if( sharedWhitespacePrefix == null ) {
				int i = 0;
				while( Character.isWhitespace( line.charAt( i ) ) ) {
					i++;
				}
				sharedWhitespacePrefix = line.substring( 0, i );
			}
			else if( !line.isEmpty() ) {
				int i = 0;
				while( i < sharedWhitespacePrefix.length() && i < line.length()
						&& sharedWhitespacePrefix.charAt( i ) == line.charAt( i ) ) {
					i++;
				}
				sharedWhitespacePrefix = sharedWhitespacePrefix.substring( 0, i );
			}

			lines.add( line );
			endLine++;
			return this;
		}

		String type() {
			String fn = file.getFileName().toString();
			int idx = fn.lastIndexOf( '.' );
			return idx >= 0 ? fn.substring( idx + 1 ) : "";
		}

		String content() {
			return lines.stream()
					.map( l -> l.length() >= sharedWhitespacePrefix.length()
							? l.substring( sharedWhitespacePrefix.length() )
							: l )
					.collect( joining( "\n" ) );
		}

		String link( String text, Path from ) {
			return String.format( "[%s](%s%s)",
					text,
					from.relativize( file ).toString().replace( '\\', '/' ),
					lineFragment( startLine, endLine - 1 ) );
		}
	}

	private static class Delimiter {
		public final Pattern start;
		public final Pattern end;

		public Delimiter( String start, String end ) {
			this.start = Pattern.compile( start );
			this.end = Pattern.compile( end );
		}
	}
}
