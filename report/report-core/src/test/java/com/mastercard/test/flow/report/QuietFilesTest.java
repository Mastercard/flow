package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link QuietFiles}
 */
@SuppressWarnings("static-method")
class QuietFilesTest {

	/**
	 * Bumps pitest coverage
	 *
	 * @throws Exception reflection failure
	 */
	@Test
	void constructor() throws Exception {
		// pitest complains that we don't exercise the private constructor, so...
		Constructor<QuietFiles> c = QuietFiles.class.getDeclaredConstructor();
		assertTrue( Modifier.isPrivate( c.getModifiers() ) );
		c.setAccessible( true );
		c.newInstance();
		c.setAccessible( false );
	}

	/**
	 * Exercises the operation-wrapping behaviour
	 */
	@Test
	void wrapping() {
		UncheckedIOException uioe = assertThrows( UncheckedIOException.class,
				() -> QuietFiles.wrap( () -> {
					throw new IOException( "root failure" );
				} ) );
		assertEquals( "root failure", uioe.getCause().getMessage() );
	}

	/**
	 * Directories can be listed
	 */
	@Test
	void list() {
		Path src = Paths.get( "src/test/java/"
				+ getClass().getName().replace( '.', '/' ) )
				.getParent();
		Set<String> list = QuietFiles.list( src )
				.map( Path::toString )
				.collect( toSet() );

		assertTrue( list.stream()
				.anyMatch( s -> s.endsWith( getClass().getSimpleName() + ".java" ) ),
				"found\n  " + list.stream().collect( joining( "\n  " ) ) );
	}

	/**
	 * Lines of files can be read
	 */
	@Test
	void lines() {
		Path src = Paths.get( "src/test/java/"
				+ getClass().getName().replace( '.', '/' ) + ".java" );

		List<String> lines = QuietFiles.lines( src )
				.collect( toList() );

		// look for this!
		assertTrue( lines.stream()
				.anyMatch( l -> "// look for this!".equals( l.trim() ) ),
				"found\n  " + lines.stream().collect( joining( "\n  " ) ) );
	}

	/**
	 * Deletion of a non-existent file
	 */
	@Test
	void alreadyDeleted() {
		Path file = Paths.get( "target/alreadyDeleted.txt" );

		assertFalse( Files.exists( file ) );

		QuietFiles.recursiveDelete( file );

		assertFalse( Files.exists( file ) );
	}

	/**
	 * Recursive deletion of a single file
	 *
	 * @throws Exception If we fail to create the test file
	 */
	@Test
	void deleteFile() throws Exception {
		Path file = Paths.get( "target/deleteFile.txt" );
		Files.write( file, "This is a single file!".getBytes( UTF_8 ) );

		assertTrue( Files.exists( file ) );

		QuietFiles.recursiveDelete( file );

		assertFalse( Files.exists( file ) );
	}

	/**
	 * Recursive deletion of a directory structure
	 *
	 * @throws Exception If we fail to create the test file
	 */
	@Test
	void deleteDir() throws Exception {
		Path root = Paths.get( "target/deleteDir" );
		Path file = root.resolve( "deep/structure/file.txt" );
		Files.createDirectories( file.getParent() );
		Files.write( file, "This is a deeply-nested file!".getBytes( UTF_8 ) );

		assertTrue( Files.exists( root ) );
		assertTrue( Files.exists( file ) );

		QuietFiles.recursiveDelete( root );

		assertFalse( Files.exists( root ) );
		assertFalse( Files.exists( file ) );
	}

	/**
	 * File writing and reading
	 *
	 * @throws Exception if pre-emptive cleanup fails
	 */
	@Test
	void writeRead() throws Exception {
		Path f = Paths.get( "target/write" );
		Files.deleteIfExists( f );
		Path r = QuietFiles.write( f, "hello!".getBytes( UTF_8 ) );
		assertEquals( f, r );

		assertEquals( "hello!", new String( QuietFiles.readAllBytes( f ), UTF_8 ) );
	}

}
