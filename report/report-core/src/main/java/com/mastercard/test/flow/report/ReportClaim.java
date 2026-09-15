package com.mastercard.test.flow.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.sun.nio.file.ExtendedOpenOption;

/**
 * Cooperating ownership of a report's physical output namespace.
 */
final class ReportClaim implements AutoCloseable {

	/** Scheduling seam for cross-process filesystem interleavings. */
	static Consumer<Path> afterOpen = path -> {
	};

	// Closing another descriptor for a locked file can release process-wide locks
	// on some systems. Reject local competitors before opening that descriptor.
	private static final Set<Path> LOCAL = ConcurrentHashMap.newKeySet();
	private final Path root;
	private final Path sidecar;
	private final boolean denyDelete;
	private Path latest;
	private boolean latestIsOutput;
	private FileChannel channel;
	private Object fileKey;
	private IOException closeFailure;
	private boolean released;

	/**
	 * @param destination   Report destination
	 * @param advertisement Configured latest location, or null for a sibling
	 */
	ReportClaim( Path destination, Path advertisement ) {
		this( QuietFiles.wrap( () -> canonical( destination.toAbsolutePath() ) ) );
		try {
			latest = advertisement == null ? root.resolveSibling( "latest" ) : QuietFiles.wrap( () -> {
				Path requested = advertisement.toAbsolutePath();
				if( !requested.getFileName().toString().equals( "latest" ) ) {
					throw new IllegalArgumentException( "Expected a latest advertisement: " + requested );
				}
				// Resolve the parent, never the advertisement's possibly foreign target.
				Path parent = canonical( requested.getParent() );
				try {
					if( !Files.readAttributes( parent, BasicFileAttributes.class ).isDirectory() ) {
						throw new NotDirectoryException( parent.toString() );
					}
				}
				catch( NoSuchFileException e ) {
					// A missing publication directory can be created when publishing.
				}
				return parent.resolve( "latest" );
			} );
			Path requested = destination.toAbsolutePath();
			latestIsOutput = QuietFiles.wrap( () -> canonical( requested.getParent() )
					.resolve( requested.getFileName() ).equals( latest ) );
		}
		catch( RuntimeException | Error e ) {
			releaseAfterFailure( e );
			throw e;
		}
	}

	private ReportClaim( Path destination ) {
		this( destination, destination.resolveSibling( "." + destination.getFileName()
				+ ".flow-writer.lock" ), true );
		try {
			QuietFiles.wrap( () -> {
				checkAncestors( root.getParent(), sidecar );
				checkDescendants();
				checkLinked();
				return null;
			} );
		}
		catch( RuntimeException | Error e ) {
			releaseAfterFailure( e );
			throw e;
		}
	}

	private ReportClaim( Path destination, Path claimFile, boolean create ) {
		root = destination;
		if( root.getParent() == null
				|| root.getFileName().toString().endsWith( ".flow-writer.lock" )
				|| root.getFileName().toString().equals( ".latest.flow-publication.lock" ) ) {
			throw new IllegalArgumentException( "Unsupported report destination: " + root );
		}
		sidecar = claimFile;
		denyDelete = System.getProperty( "os.name" ).startsWith( "Windows" )
				&& sidecar.getFileSystem() == FileSystems.getDefault();
		if( !LOCAL.add( sidecar ) ) {
			throw new IllegalStateException( "Report destination is active: " + root );
		}
		try {
			QuietFiles.wrap( () -> {
				if( create ) {
					Files.createDirectories( sidecar.getParent() );
					try {
						Files.createFile( sidecar );
					}
					catch( FileAlreadyExistsException e ) {
						// Never truncate or reopen a locally owned claim while ensuring existence.
					}
				}
				// Capture BEFORE open: a key read afterwards might describe a replacement,
				// not the inode held by the channel. Check again after all ownership probes.
				fileKey = Files.readAttributes( sidecar, BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS ).fileKey();
				if( fileKey == null && !denyDelete ) {
					throw new IOException( "Report claim has no file identity: " + sidecar );
				}
				// The native Windows provider has no fileKey; its open handle must deny
				// deletion instead. Unsupported options fail closed, never fall back.
				channel = denyDelete
						? FileChannel.open( sidecar, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS,
								ExtendedOpenOption.NOSHARE_DELETE )
						: FileChannel.open( sidecar, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS );
				if( channel.tryLock() == null ) {
					throw new IllegalStateException( "Report destination is active: " + root );
				}
				return null;
			} );
			afterOpen.accept( sidecar );
		}
		catch( RuntimeException | Error e ) {
			releaseAfterFailure( e );
			throw e;
		}
	}

	private void releaseAfterFailure( Throwable failure ) {
		try {
			close();
		}
		catch( RuntimeException | Error cleanup ) {
			failure.addSuppressed( cleanup );
		}
	}

	private void checkLinked() {
		QuietFiles.wrap( () -> {
			Object linkedKey = Files.readAttributes( sidecar, BasicFileAttributes.class,
					LinkOption.NOFOLLOW_LINKS ).fileKey();
			if( !denyDelete && !fileKey.equals( linkedKey ) ) {
				throw new IOException( "Report claim changed: " + sidecar );
			}
			return null;
		} );
	}

	private static Path canonical( Path path ) throws IOException {
		try {
			Files.readAttributes( path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS );
		}
		catch( NoSuchFileException e ) {
			if( path.getParent() == null ) {
				throw e;
			}
			Path resolved = canonical( path.getParent() ).resolve( path.getFileName() ).normalize();
			// Normalizing a missing component followed by '..' can expose an existing
			// alias. Resolve that alias too, before choosing the LOCAL key or IO path.
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

	private void checkAncestors( Path ancestor, Path ownedSidecar ) {
		for( Path path = ancestor; path != null && path.getParent() != null; path = path.getParent() ) {
			Path ancestorClaim = path.resolveSibling( "." + path.getFileName() + ".flow-writer.lock" );
			if( !ancestorClaim.equals( ownedSidecar ) ) {
				probe( ancestorClaim );
			}
		}
	}

	private void checkDescendants() throws IOException {
		// Hold our lock through both checks. Linked-file validation also rejects a
		// delayed child whose sidecar a finishing ancestor removed after its scan.
		Files.walkFileTree( root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory( Path path, BasicFileAttributes attributes ) {
				return visitFile( path, attributes );
			}

			@Override
			public FileVisitResult visitFile( Path path, BasicFileAttributes attributes ) {
				String name = path.getFileName().toString();
				if( name.equals( ".latest.flow-publication.lock" )
						|| name.startsWith( "." ) && name.endsWith( ".flow-writer.lock" ) ) {
					probe( path );
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed( Path path, IOException failure ) throws IOException {
				if( path.equals( root ) && failure instanceof NoSuchFileException ) {
					return FileVisitResult.CONTINUE;
				}
				throw failure;
			}
		} );
	}

	private void probe( Path path ) {
		if( path.equals( sidecar ) ) {
			return;
		}
		// Reserve LOCAL atomically before opening even a probe descriptor: closing
		// one must never release another local writer's process-wide POSIX lock.
		try( ReportClaim probe = new ReportClaim( root, path, false ) ) {
			// An unlocked stale claim is harmless; never delete or create probe files.
			probe.checkLinked();
		}
		catch( UncheckedIOException e ) {
			if( !(e.getCause() instanceof NoSuchFileException) ) {
				throw e;
			}
		}
	}

	/**
	 * @return The canonical destination used for all destructive IO
	 */
	Path root() {
		return root;
	}

	/**
	 * @return A short-lived claim protecting the configured publication location
	 */
	ReportClaim publication() {
		ReportClaim publication = new ReportClaim( latest,
				latest.resolveSibling( ".latest.flow-publication.lock" ), true );
		try {
			// An earlier replacing ancestor prevents publication; a later one sees
			// this locked sidecar in its scan and cannot remove our publication parent.
			publication.checkAncestors( latest.getParent(), sidecar );
			publication.checkLinked();
			return publication;
		}
		catch( RuntimeException | Error e ) {
			publication.releaseAfterFailure( e );
			throw e;
		}
	}

	/**
	 * Withdraws only this destination's advertisement before replacement.
	 */
	void withdrawLatest() {
		// The configured output can itself be a latest alias: path() must stay usable.
		if( latestIsOutput ) {
			return;
		}
		QuietFiles.wrap( () -> {
			if( pointsHere( latest ) ) {
				try( ReportClaim publication = publication() ) {
					if( pointsHere( latest ) ) {
						Files.delete( latest );
					}
				}
			}
			return null;
		} );
	}

	private boolean pointsHere( Path latest ) throws IOException {
		try {
			if( !Files.readAttributes( latest, BasicFileAttributes.class,
					LinkOption.NOFOLLOW_LINKS ).isSymbolicLink() ) {
				return false;
			}
		}
		catch( NoSuchFileException e ) {
			return false;
		}
		return canonical( latest.getParent().resolve( Files.readSymbolicLink( latest ) ) )
				.equals( root );
	}

	@Override
	public void close() {
		if( released ) {
			return;
		}
		try {
			// A failed close may already mark the channel closed without disposing its
			// handle. A later no-op close cannot establish safe release of LOCAL.
			if( closeFailure != null ) {
				throw closeFailure;
			}
			if( channel != null ) {
				channel.close();
			}
			LOCAL.remove( sidecar );
			released = true;
		}
		catch( IOException e ) {
			closeFailure = e;
			throw new UncheckedIOException( "Failed to release report " + root, e );
		}
	}
}
