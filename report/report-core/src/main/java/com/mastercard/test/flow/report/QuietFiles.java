package com.mastercard.test.flow.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.stream.Stream;

/**
 * Wrappers around {@link Files} methods that convert {@link IOException}s to
 * {@link UncheckedIOException}. This is more convenient in functional contexts.
 */
public class QuietFiles {

	/**
	 * An operation that can throw an {@link IOException}
	 *
	 * @param <T> The return type
	 */
	interface FailureProne<T> {
		/**
		 * Do the operation
		 *
		 * @return The return value
		 * @throws IOException If something goes wrong
		 */
		T run() throws IOException;
	}

	/**
	 * Perform a failure-prone operation, wrapping failures as
	 * {@link UncheckedIOException}
	 *
	 * @param <T> The return type
	 * @param op  The operation
	 * @return The return value
	 */
	static <T> T wrap( FailureProne<T> op ) {
		try {
			return op.run();
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	private QuietFiles() {
		// no instances
	}

	/**
	 * Deletes a file or directory
	 *
	 * @param path The path of the file/dir to delete
	 */
	public static void recursiveDelete( Path path ) {
		try {
			if( !Files.exists( path, LinkOption.NOFOLLOW_LINKS ) ) {
				// we're done here
			}
			else if( Files.isDirectory( path, LinkOption.NOFOLLOW_LINKS ) ) {
				try( Stream<Path> children = Files.list( path ) ) {
					children.forEach( QuietFiles::recursiveDelete );
				}
				Files.delete( path );
			}
			else {
				Files.delete( path );
			}
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( "Failed to delete " + path, ioe );
		}
	}

	/**
	 * @see Files#createDirectories(Path, FileAttribute...)
	 * @param dir  the directory to create
	 * @param attr an optional list of file attributes to set atomically when
	 *             creating the directory
	 */
	public static void createDirectories( Path dir, FileAttribute<?>... attr ) {
		wrap( () -> Files.createDirectories( dir, attr ) );
	}

	/**
	 * @see Files#createTempFile(String, String, FileAttribute...)
	 * @param prefix     the prefix string to be used in generating the file's name;
	 *                   may be null
	 * @param suffix     the suffix string to be used in generating the file's name;
	 *                   may be null, in which case ".tmp" is used
	 * @param attributes an optional list of file attributes to set atomically when
	 *                   creating the file
	 * @return the path to the newly created file that did not exist before this
	 *         method was invoked
	 */
	public static Path createTempFile( String prefix, String suffix,
			FileAttribute<?>... attributes ) {
		return wrap( () -> Files.createTempFile( prefix, suffix, attributes ) );
	}

	/**
	 * @see Files#write(Path, byte[], OpenOption...)
	 * @param path    The path to write to
	 * @param bytes   The data to write
	 * @param options options specifying how the file is opened
	 * @return the path
	 */
	public static Path write( Path path, byte[] bytes, OpenOption... options ) {
		return wrap( () -> Files.write( path, bytes, options ) );
	}

	/**
	 * @see Files#lines(Path)
	 * @param path The path to read
	 * @return The lines in that file
	 */
	public static Stream<String> lines( Path path ) {
		return wrap( () -> Files.lines( path ) );
	}

	/**
	 * @see Files#list(Path)
	 * @param dir The directory to list
	 * @return The directory contents
	 */
	public static Stream<Path> list( Path dir ) {
		return wrap( () -> Files.list( dir ) );
	}

	/**
	 * @see Files#move(Path, Path, CopyOption...)
	 * @param source  the path to the file to move
	 * @param target  the path to the target file (may be associated with a
	 *                different provider to the source path)
	 * @param options options specifying how the move should be done
	 * @return the path to the target file
	 */
	public static Path move( Path source, Path target, CopyOption... options ) {
		return wrap( () -> Files.move( source, target, options ) );
	}

	/**
	 * @see Files#readAllBytes(Path)
	 * @param path The path to read
	 * @return The bytes of the file at that location
	 */
	public static byte[] readAllBytes( Path path ) {
		return wrap( () -> Files.readAllBytes( path ) );
	}
}
