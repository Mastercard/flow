package com.mastercard.test.flow.report;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The filesystem boundary for report payloads and atomic index publication.
 */
class ReportFiles {

	/**
	 * @param path File to create or replace
	 * @return The output stream, whose owner must close it before publication
	 * @throws IOException On filesystem failure
	 */
	OutputStream open( Path path ) throws IOException {
		return Files.newOutputStream( path );
	}

	/**
	 * @param root The report directory
	 * @return A new same-directory temporary index path
	 * @throws IOException On filesystem failure
	 */
	Path temporary( Path root ) throws IOException {
		return Files.createTempFile( root, ".index-", ".tmp" );
	}

	/**
	 * @param source      A fully written and closed temporary index
	 * @param destination The final index
	 * @throws IOException If atomic publication fails or is unsupported; no
	 *                     non-atomic replacement is attempted
	 */
	void publish( Path source, Path destination ) throws IOException {
		Files.move( source, destination, ATOMIC_MOVE, REPLACE_EXISTING );
	}
}
