package com.mastercard.test.flow.autodoc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks the validity of relative links in markdown documents.
 */
public class RelativeLink {

	private static final Pattern STD_LINK = Pattern.compile( "\\[.*?\\]\\((.*?)\\)" );
	private static final Pattern REF_LINK = Pattern.compile( "^\\[.*?\\]: (.*)$" );

	private final Docs docs;

	/**
	 * @param docs How to work with the documents in the project
	 */
	public RelativeLink( Docs docs ) {
		this.docs = docs;
	}

	/**
	 * Checks the validity of relative links in a markdown file. A link is valid if
	 * either:
	 * <ul>
	 * <li>The destination is a directory that contains a <code>README.md</code>
	 * file
	 * <li>The destination is a regular file
	 * </ul>
	 *
	 * @param file A markdown file path
	 */
	public void check( Path file ) {
		try( Stream<String> lines = Files.lines( file ) ) {
			lines.flatMap( line -> Stream.of( STD_LINK.matcher( line ), REF_LINK.matcher( line ) ) )
					.forEach(
							mtch -> {
								while( mtch.find() ) {
									if( !mtch.group( 1 ).startsWith( "http" ) ) {
										Path destination = file.getParent()
												.toAbsolutePath()
												.resolve( mtch.group( 1 ).replaceAll( "#.*", "" ) );

										docs.assertEquals(
												true,
												Files.exists( destination ),
												String.format( "Bad link:\n%s\nin\n%s", destination, mtch.group() ) );

										if( Files.isDirectory( destination ) ) {
											try {
												docs.assertEquals(
														true,
														Files.list( destination )
																.anyMatch( f -> "README.md".equals( f.getFileName().toString() ) ),
														String.format(
																"Bad directory link:\n%s\nlinked from\n%s\nis not a module root",
																destination, mtch.group() ) );
											}
											catch( IOException e ) {
												throw new UncheckedIOException( "Failed to list files in " + destination,
														e );
											}
										}
										else {
											docs.assertEquals(
													true,
													Files.isRegularFile( destination ),
													String.format( "Bad file link:\n%s\nfrom\n%s", destination,
															mtch.group() ) );
										}
									}
								}
							} );
		}
		catch( IOException e ) {
			throw new UncheckedIOException( "Failed to iterate lines of " + file, e );
		}
	}
}
