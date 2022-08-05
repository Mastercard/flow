package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Exercises failure paths of {@link JsApp}
 */
@SuppressWarnings("static-method")
class JsAppTest {

	/**
	 * What happens when the manifest file is not found
	 */
	@Test
	void noManifest() {
		Path p = Paths.get( "target/noManifest/res" );
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> new JsApp( "/does/not/exist", p ) );
		assertEquals( "Failed to extract /does/not/exist/manifest.txt",
				ise.getMessage() );
	}

	/**
	 * What happens when the manifest is empty
	 */
	@Test
	void emptyManifest() {
		Path p = Paths.get( "target/emptyManifest/res" );
		UncheckedIOException uioe = assertThrows( UncheckedIOException.class,
				() -> new JsApp( "/JsAppTest/emptyManifest", p ) );
		assertEquals( "Failed to extract index.html", uioe.getMessage() );
	}

	/**
	 * What happens when the manifest lists a file that doesn't exist
	 */
	@Test
	void missingManifestEntry() {
		Path p = Paths.get( "target/missingManifestEntry/res" );
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> new JsApp( "/JsAppTest/missingManifestEntry", p ) );
		assertEquals( "Failed to copy resource /JsAppTest/missingManifestEntry/nosuchfile.txt",
				ise.getMessage() );
	}

	/**
	 * What happens when there isn't an index.html file
	 */
	@Test
	void missingIndex() {
		Path p = Paths.get( "target/missingIndex/res" );
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> new JsApp( "/JsAppTest/missingIndex", p ) );
		assertEquals( "Failed to find index.html in target/missingIndex/res",
				ise.getMessage().replace( '\\', '/' ) );
	}

	/**
	 * What happens when the index.html is bad
	 */
	@Test
	void malformedIndex() {
		Path p = Paths.get( "target/malformedIndex/res" );
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> new JsApp( "/JsAppTest/malformedIndex", p ) );
		assertEquals( ""
				+ "Start or end line not found in target/malformedIndex/res/index.html\n"
				+ "This is not a valid index file",
				iae.getMessage().replace( '\\', '/' ) );
	}
}
