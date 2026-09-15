package com.mastercard.test.flow.report;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * The filesystem boundary for report payloads and atomic index publication.
 */
class ReportFiles {

	/**
	 * Resolves existing aliases without requiring a destination to exist yet.
	 *
	 * @param path Absolute path
	 * @return Physical destination for report IO
	 * @throws IOException On path resolution failure
	 */
	static Path canonical( Path path ) throws IOException {
		try {
			Files.readAttributes( path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS );
		}
		catch( NoSuchFileException e ) {
			if( path.getParent() == null ) {
				throw e;
			}
			Path resolved = canonical( path.getParent() ).resolve( path.getFileName() ).normalize();
			// Missing components followed by '..' can expose an existing alias.
			try {
				Files.readAttributes( resolved, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS );
			}
			catch( NoSuchFileException missing ) {
				return resolved;
			}
			return resolved.toRealPath();
		}
		return path.toRealPath();
	}

	/**
	 * Resolves only the advertisement parent, never an unrelated target.
	 *
	 * @param root          Physical report destination
	 * @param advertisement Configured latest location, or null for a sibling
	 * @return Advertisement path with a canonical parent
	 * @throws IOException On path resolution failure
	 */
	static Path latest( Path root, Path advertisement ) throws IOException {
		if( advertisement == null ) {
			return root.resolveSibling( "latest" );
		}
		Path absolute = advertisement.toAbsolutePath();
		if( !absolute.getFileName().toString().equals( "latest" ) ) {
			throw new IllegalArgumentException( "Expected a latest advertisement: " + absolute );
		}
		Path parent = canonical( absolute.getParent() );
		try {
			if( !Files.readAttributes( parent, BasicFileAttributes.class ).isDirectory() ) {
				throw new NotDirectoryException( parent.toString() );
			}
		}
		catch( NoSuchFileException e ) {
			// The publication directory may be created on completion.
		}
		return parent.resolve( "latest" );
	}

	/**
	 * Withdraws this report's advertisement before replacement, not an output
	 * alias.
	 *
	 * @param requested Requested output access path
	 * @param root      Physical report destination
	 * @param latest    Advertisement path with a canonical parent
	 */
	void withdrawLatest( Path requested, Path root, Path latest ) {
		QuietFiles.wrap( () -> {
			Path absolute = requested.toAbsolutePath();
			if( canonical( absolute.getParent() ).resolve( absolute.getFileName() ).equals( latest ) ) {
				return null;
			}
			try {
				if( Files.readAttributes( latest, BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS ).isSymbolicLink()
						&& canonical( latest.getParent().resolve( Files.readSymbolicLink( latest ) ) )
								.equals( root ) ) {
					Files.delete( latest );
				}
			}
			catch( NoSuchFileException e ) {
				// Nothing to withdraw.
			}
			return null;
		} );
	}

	/**
	 * @param root Destination to replace; callers exclude competing writers
	 */
	void clear( Path root ) {
		QuietFiles.recursiveDelete( root );
	}

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
