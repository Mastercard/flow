package com.mastercard.test.flow.autodoc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javassist.ClassPool;
import javassist.NotFoundException;

/**
 * Checks that links to code are accurate.
 * <p>
 * Use it by adding a <a href=
 * "https://www.markdownguide.org/basic-syntax/#reference-style-links">reference-style
 * link</a> to your markdown document, where the link reference is the name of
 * the class or the class and method. The second part of the link should exist
 * within a comment-delimited block.
 * <p>
 * For example:
 *
 * <pre>
 * [This][MyClassName] is a link to the class.
 * [This][MyClassName.myMethod()] is a link to the method.
 *
 * &lt;!-- code_link_start --&gt;
 *
 * [MyClassName]: relative/path/to/source/file/MyClassName.java
 * [MyClassName.myMethod()]: relative/path/to/source/file/MyClassName.java#56
 *
 * &lt;!-- code_link_end --&gt;
 * </pre>
 * <p>
 * The check will update the link paths and line numbers and report failure if
 * any markdown files are changed as a result.
 * <p>
 * If you're trying to link to a class but other classes that start with the
 * same name get in the way, end the classname with <code>!</code> to signal
 * that you're looking for an exact match. e.g.: if you're trying to link to
 * <code>Foo</code> but the search also turns up <code>FooBar</code> then use
 * link reference <code>Foo!</code>
 */
public class CodeLink {

	private static final String START = "<!-- code_link_start -->";
	private static final String END = "<!-- code_link_end -->";

	private static final Pattern REF_LINK = Pattern.compile( "\\[([^]]*)\\]: " );

	private final Docs docs;

	/**
	 * @param docs How to work with markdown documents
	 */
	public CodeLink( Docs docs ) {
		this.docs = docs;
	}

	/**
	 * Checks the code link sections in a markdown file
	 *
	 * @param file The markdown file
	 */
	public void check( Path file ) {
		docs.insert(
				file,
				START,
				content -> Stream.of( content.split( "\n" ) )
						.map(
								line -> {
									Matcher m = REF_LINK.matcher( line );
									if( m.find() ) {
										String ref = m.group( 1 );
										return String.format( "[%s]: %s", ref,
												linkfor( ref, file.getParent() ) );
									}
									return line;
								} )
						.collect( Collectors.joining( "\n" ) ),
				END );
	}

	private String linkfor( String ref, Path dir ) {
		String classNameFragment = ref;
		String method = "";
		String search = "";
		if( ref.contains( "." ) && ref.endsWith( ")" ) ) {
			int callIdx = ref.lastIndexOf( '.', ref.indexOf( '(' ) );
			classNameFragment = classNameFragment.substring( 0, callIdx );
			method = ref.substring( callIdx + 1 );
		}
		if( ref.contains( "?" ) ) {
			int searchIdx = ref.indexOf( "?" );
			classNameFragment = classNameFragment.substring( 0, searchIdx );
			search = ref.substring( searchIdx + 1 );
		}

		Path src = docs.javaSourceFileFor( classNameFragment );

		String lineFragment = "";

		if( !method.isEmpty() ) {
			lineFragment = methodDeclarationLineRange( ref, method, src );
		}

		if( !search.isEmpty() ) {
			lineFragment = searchLine( ref, search, src );
		}

		return dir.relativize( src ).toString().replace( '\\', '/' ) + lineFragment;
	}

	private String searchLine( String ref, String search, Path src ) {

		try {
			Pattern p = Pattern.compile( search );

			String[] srcLines = new String( Files.readAllBytes( src ), UTF_8 ).split( "\n" );
			Set<Integer> candidates = new TreeSet<>();
			for( int i = 0; i < srcLines.length; i++ ) {
				if( p.matcher( srcLines[i] ).find() ) {
					candidates.add( i + 1 );
				}
			}
			if( candidates.size() != 1 ) {
				throw new IllegalArgumentException(
						"Expected 1 line to match "
								+ ref
								+ ", found:"
								+ candidates.stream()
										.map( i -> "\n  " + i + ": " + srcLines[i - 1] )
										.collect( joining() ) );
			}
			return docs.lineFragment( candidates.iterator().next() );
		}
		catch( IOException e ) {
			throw new IllegalArgumentException( "Failed to find line numbers for '" + ref + "'", e );
		}
	}

	/**
	 * @param ref    The link reference from the markdown
	 * @param method The method part of that ref
	 * @param src    The source file
	 * @return A link fragment that points to the declaration of the method in the
	 *         source file
	 */
	private String methodDeclarationLineRange( String ref, String method, Path src ) {
		// this is where the fun begins
		// extract the classname, method name and method parameter types
		String srcPath = src.toString().replace( '\\', '.' ).replace( '/', '.' );
		String className = srcPath.substring( srcPath.lastIndexOf( ".java." ) + 6 );
		className = className.substring( 0, className.length() - ".java".length() );
		String methodName = method.substring( 0, method.indexOf( '(' ) );
		String[] parameterTypes = Stream
				.of( method.substring( methodName.length() + 1, method.length() - 1 ).split( "," ) )
				.map( String::trim )
				.filter( s -> !s.isEmpty() )
				.toArray( String[]::new );

		try {
			// load the class
			Class<?> clss = CodeLink.class.getClassLoader().loadClass( className );

			// find the method that matches name and parameter types
			Method mthd = Stream.of( clss.getDeclaredMethods() )
					.filter( m -> methodName.equals( m.getName() ) )
					.filter(
							m -> {
								boolean argMatch = m.getParameterCount() == parameterTypes.length;
								for( int i = 0; argMatch && i < parameterTypes.length; i++ ) {
									String actual = m.getParameters()[i]
											.getType()
											.getSimpleName()
											// Markdown doesn't like link references with square brackets
											.replace( "[]", "..." );
									argMatch = argMatch && parameterTypes[i].equals( actual );
								}
								return argMatch;
							} )
					.findFirst()
					.orElseThrow(
							() -> new IllegalArgumentException(
									String.format(
											"Failed to find method from '%s' in %s%s",
											ref,
											clss,
											Stream.of( clss.getDeclaredMethods() )
													.filter( m -> methodName.equals( m.getName() ) )
													.map( m -> "\n    " + m.toString().replace( "[]", "..." ) )
													.collect( joining() ) ) ) );

			// find the line number of the first bytecode
			int firstImplementationLineIndex = ClassPool.getDefault()
					.get( mthd.getDeclaringClass().getCanonicalName() )
					.getDeclaredMethod( mthd.getName() )
					.getMethodInfo()
					.getLineNumber( 0 )
					// -1 to go from line number to index
					- 1;

			// load the source file
			String[] srcLines = new String( Files.readAllBytes( src ), UTF_8 ).split( "\n" );

			// find the end of the method declaration: the first non-empty line before the
			// first bytcode
			int lastDeclarationLineIndex = firstImplementationLineIndex - 1;
			while( lastDeclarationLineIndex > 0 && srcLines[lastDeclarationLineIndex].trim().isEmpty() ) {
				lastDeclarationLineIndex--;
			}

			// scan backwards from the last declaration line to the start of the javadoc or
			// the first empty line
			int firstDeclarationLine = lastDeclarationLineIndex;
			while( firstDeclarationLine > 0
					&& !"/**".equals( srcLines[firstDeclarationLine].trim() )
					&& !srcLines[firstDeclarationLine].trim().isEmpty() ) {
				firstDeclarationLine--;
			}

			// now we can make a nice link fragment to point to the method API information
			return docs.lineFragment(
					// line numbers are 1-based, indices are 0-based
					firstDeclarationLine + 1, lastDeclarationLineIndex + 1 );
		}
		catch( IOException | ClassNotFoundException | NotFoundException e ) {
			throw new IllegalArgumentException( "Failed to find line numbers for '" + ref + "'", e );
		}
	}
}
