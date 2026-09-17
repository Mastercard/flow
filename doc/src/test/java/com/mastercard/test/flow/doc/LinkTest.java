package com.mastercard.test.flow.doc;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Checks the validity of relative links in markdown files. A link is valid if
 * either:
 * <ul>
 * <li>The destination is a directory that contains a README.md file</li>
 * <li>The destination is a regular file</li>
 * </ul>
 */
@SuppressWarnings("static-method")
class LinkTest {

	private static final Pattern STD_LINK = Pattern.compile( "\\[.*?\\]\\((.*?)\\)" );
	private static final Pattern REF_LINK = Pattern.compile( "^\\[.*?\\]: (.*)$" );

	/**
	 * @return per-file tests that throw a wobbler if we find an invalid link
	 */
	@TestFactory
	Stream<DynamicTest> markdown() {
		return Util.markdownFiles()
				.map( mdFile -> dynamicTest( mdFile.toString(), () -> checkLinks( mdFile ) ) );
	}

	/**
	 * @param directory Standalone documentation, without a module README
	 * @throws IOException If the fixture cannot be written
	 */
	@Test
	void sameDocumentLinksDoNotRequireModuleReadme( @TempDir Path directory ) throws IOException {
		Path document = directory.resolve( "guide.md" );
		Files.writeString( document,
				"# Section\n[inline](#section)\n[reference]: #section\n[empty]()\n" );
		Assertions.assertDoesNotThrow( () -> checkLinks( document ) );

		Files.writeString( document, "[missing](missing.md#section)\n" );
		AssertionError failure = Assertions.assertThrows( AssertionError.class,
				() -> checkLinks( document ) );
		Assertions.assertTrue( failure.getMessage().contains( "missing.md" ) );
	}

	private static void checkLinks( Path file ) {
		QuietFiles.lines( file )
				.flatMap( line -> Stream.of( STD_LINK.matcher( line ), REF_LINK.matcher( line ) ) )
				.forEach( mtch -> {
					while( mtch.find() ) {
						if( !mtch.group( 1 ).startsWith( "http" ) ) {
							String target = mtch.group( 1 ).replaceAll( "#.*", "" );
							Path destination = target.isEmpty() ? file.toAbsolutePath()
									: file.getParent().toAbsolutePath().resolve( target );

							Assertions.assertTrue( Files.exists( destination ),
									String.format( "Bad link:\n%s\nin\n%s",
											destination, mtch.group() ) );

							if( Files.isDirectory( destination ) ) {
								Assertions.assertTrue( QuietFiles.list( destination )
										.anyMatch( f -> "README.md".equals( f.getFileName().toString() ) ),
										String.format(
												"Bad directory link:\n%s\nlinked from\n%s\nis not a module root",
												destination, mtch.group() ) );
							}
							else {
								Assertions.assertTrue( Files.isRegularFile( destination ),
										String.format( "Bad file link:\n%s\nfrom\n%s",
												destination, mtch.group() ) );
							}
						}
					}
				} );
	}
}
