package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
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
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> new JsApp( "/does/not/exist",
						Paths.get( "target/noManifest/res" ) ) );
		assertEquals( "Failed to extract /does/not/exist/manifest.txt",
				ise.getMessage() );
	}

	/**
	 * What happens when the manifest is empty
	 */
	@Test
	void emptyManifest() {
		UncheckedIOException uioe = assertThrows( UncheckedIOException.class,
				() -> new JsApp( "/JsAppTest/emptyManifest",
						Paths.get( "target/emptyManifest/res" ) ) );
		assertEquals( "Failed to extract index.html", uioe.getMessage() );
	}

	/**
	 * What happens when the manifest lists a file that doesn't exist
	 */
	@Test
	void missingManifestEntry() {
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> new JsApp( "/JsAppTest/missingManifestEntry",
						Paths.get( "target/missingManifestEntry/res" ) ) );
		assertEquals( "Failed to copy resource /JsAppTest/missingManifestEntry/nosuchfile.txt",
				ise.getMessage() );
	}

	/**
	 * What happens when there isn't an index.html file
	 */
	@Test
	void missingIndex() {
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> new JsApp( "/JsAppTest/missingIndex",
						Paths.get( "target/missingIndex/res" ) ) );
		assertEquals( "Failed to find index.html in target/missingIndex/res",
				ise.getMessage().replace( '\\', '/' ) );
	}

	/**
	 * What happens when the index.html is bad
	 */
	@Test
	void malformedIndex() {
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> new JsApp( "/JsAppTest/malformedIndex",
						Paths.get( "target/malformedIndex/res" ) ) );
		assertEquals( ""
				+ "Start or end line not found in target/malformedIndex/res/index.html\n"
				+ "This is not a valid index file",
				iae.getMessage().replace( '\\', '/' ) );
	}
}
