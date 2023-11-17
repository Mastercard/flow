package com.mastercard.test.flow.autodoc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copies sections of source files into readmes to keep code snippets fresh.
 * <p>
 * Use it by delimiting the snippet you want in the source file with:
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
 * <p>
 * If there are multiple classes with the same name then you may have to add
 * enough of the fully-qualified class name to resolve the ambiguity.
 */
public class Snippets {

	private static final String MD_START = "<!-- snippet start -->";
	private static final String MD_END = "<!-- snippet end -->";
	private static final Pattern MD_SNIPPET_DESCRIPTOR = Pattern.compile( "<!-- (.*):(.*) -->" );

	private static final Map<String, Delimiter> SOURCE_DELIMITERS;

	static {
		Map<String, Delimiter> m = new HashMap<>();
		m.put( ".java", new Delimiter( "// snippet-start:(.*)", "// snippet-end:(.*)" ) );
		m.put( ".xml", new Delimiter( "<!-- snippet-start:(.*) -->", "<!-- snippet-end:(.*) -->" ) );
		SOURCE_DELIMITERS = Collections.unmodifiableMap( m );
	}

	private static Delimiter delimiter( Path file ) {
		String fn = file.getFileName().toString();
		return SOURCE_DELIMITERS.entrySet().stream()
				.filter( e -> fn.endsWith( e.getKey() ) )
				.findFirst()
				.map( Entry::getValue )
				.orElseThrow(
						() -> new IllegalArgumentException( "No delimiters found for file '" + fn + "'" ) );
	}

	private final Map<String, Snippet> excerpts = new HashMap<>();
	private final Docs docs;

	/**
	 * @param docs How to work with markdown in this project
	 */
	public Snippets( Docs docs ) {
		this.docs = docs;
	}

	/**
	 * Checks that the snippets in a markdown file are accurate
	 *
	 * @param markdown The markdown file to check
	 */
	public void check( Path markdown ) {
		docs.insert(
				markdown,
				MD_START,
				existing -> refresh( markdown.getParent(), existing ),
				MD_END );
	}

	private String refresh( Path dir, String existing ) {
		Matcher m = MD_SNIPPET_DESCRIPTOR.matcher( existing );
		if( m.find() ) {
			Path src = docs.sourceFileFor( m.group( 1 ) );
			Snippet e = extract( src, m.group( 2 ) );
			return String.format(
					"" + "%s\n" + "\n" + "```%s\n" + "%s\n" + "```\n" + "%s",
					m.group(), e.type(), e.content(), e.link( "Snippet context", dir ) );
		}
		return "No excerpt descriptor found!";
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
					String line = lines.next();

					Delimiter sd = delimiter( file );
					Matcher start = sd.start.matcher( line.trim() );
					Matcher end = sd.end.matcher( line.trim() );
					if( start.matches() ) {
						extant.put( start.group( 1 ), new Snippet( docs, file, lineNumber + 1 ) );
					}
					else if( end.matches() ) {
						excerpts.put( file + ":" + end.group( 1 ), extant.remove( end.group( 1 ) ) );
					}
					else {
						extant.values().forEach( e -> e.append( line ) );
					}
				}

				extant.forEach( ( n, e ) -> excerpts.put( file + ":" + n, e ) );
			}

			ex = excerpts.get( key );
			if( ex == null ) {
				return new Snippet( docs, file, 0 ).append( "No such excerpt!" );
			}
			return ex;
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	private static class Snippet {
		private final Docs docs;
		private final Path file;

		private final int startLine;
		private int endLine;
		private final List<String> lines = new ArrayList<>();
		private String sharedWhitespacePrefix = null;

		/**
		 * Constructs a new and empty snippet
		 *
		 * @param file      The source file
		 * @param startLine The first line of the snippet
		 */
		Snippet( Docs docs, Path file, int startLine ) {
			this.docs = docs;
			this.file = file;

			this.startLine = startLine;
			endLine = startLine;
		}

		/**
		 * Adds aline to the snippet
		 *
		 * @param line The line
		 * @return <code>this</code>
		 */
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
				while( i < sharedWhitespacePrefix.length()
						&& i < line.length()
						&& sharedWhitespacePrefix.charAt( i ) == line.charAt( i ) ) {
					i++;
				}
				sharedWhitespacePrefix = sharedWhitespacePrefix.substring( 0, i );
			}

			lines.add( line );
			endLine++;
			return this;
		}

		/** @return The filename suffix */
		String type() {
			String fn = file.getFileName().toString();
			int idx = fn.lastIndexOf( '.' );
			return idx >= 0 ? fn.substring( idx + 1 ) : "";
		}

		/** @return The content of the snippet */
		String content() {
			return lines.stream()
					.map(
							l -> l.length() >= sharedWhitespacePrefix.length()
									? l.substring( sharedWhitespacePrefix.length() )
									: l )
					.collect( joining( "\n" ) );
		}

		/**
		 * @param text link text
		 * @param from link source location
		 * @return a relative link to the snippet location
		 */
		String link( String text, Path from ) {
			return String.format(
					"[%s](%s%s)",
					text,
					from.relativize( file ).toString().replace( '\\', '/' ),
					docs.lineFragment( startLine, endLine - 1 ) );
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
