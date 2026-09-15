package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Destination ownership through the direct writer and filesystem seam.
 */
@SuppressWarnings("static-method")
class WriterOwnershipTest {

	/**
	 * Even an immediate report remains owned until its caller closes it.
	 *
	 * @param dir Isolated report container
	 */
	@Test
	void activeDestinationAndReuse( @TempDir Path dir ) {
		Path destination = dir.resolve( "configured" );
		try( Writer owner = new Writer( "model", "same title", destination ) ) {
			owner.with( Mdl.BASIS );
			assertThrows( IllegalStateException.class, () -> {
				try( Writer competitor = new Writer( "model", "same title",
						dir.resolve( "unused/../configured" ) ) ) {
					competitor.with( Mdl.CHILD );
				}
			} );
			assertEquals( "basis", new Reader( destination ).read().entries.get( 0 ).description );
			try( Writer sibling = new Writer( "model", "same title", dir.resolve( "sibling" ) ) ) {
				sibling.with( Mdl.CHILD );
			}
		}
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
			assertEquals( "child", new Reader( destination ).read().entries.get( 0 ).description );
		}
	}

	/**
	 * Local rejection must not disturb the OS claim held against other JVMs.
	 *
	 * @param dir Isolated container
	 * @throws Exception On process or filesystem failure
	 */
	@Test
	void separateProcessAndStaleClaim( @TempDir Path dir ) throws Exception {
		Path destination = dir.resolve( "report" );
		try( Peer peer = new Peer( dir.resolve( "unused/../report" ) ) ) {
			assertEquals( "owned", peer.command( "open" ) );
			assertThrows( IllegalStateException.class,
					() -> new Writer( "model", "test", destination ) );
			assertEquals( "basis", new Reader( destination ).read().entries.get( 0 ).description );
			assertEquals( "closed", peer.command( "close" ) );
			try( Writer owner = new Writer( "model", "test", destination ) ) {
				owner.with( Mdl.CHILD );
				assertThrows( IllegalStateException.class,
						() -> new Writer( "model", "test", destination ) );
				assertEquals( "active", peer.command( "open" ) );
				assertEquals( "child", new Reader( destination ).read().entries.get( 0 ).description );
			}
			assertEquals( "owned", peer.command( "open" ) );
			assertEquals( "exiting", peer.command( "exit" ) );
			assertTrue( peer.process.waitFor( 10, TimeUnit.SECONDS ) );
			try( Writer replacement = new Writer( "model", "after crash", destination ) ) {
				replacement.with( Mdl.CHILD );
			}
			assertEquals( "after crash", new Reader( destination ).read().meta.testTitle );
		}
	}

	/**
	 * A directory alias names the same output rather than a second claim.
	 *
	 * @param dir Isolated container
	 * @throws Exception On filesystem failure
	 */
	@Test
	void symlinkAlias( @TempDir Path dir ) throws Exception {
		Path real = Files.createDirectory( dir.resolve( "real" ) );
		Path alias = symlink( dir.resolve( "alias" ), real );
		Path destination = real.resolve( "report" );
		try( Writer owner = new Writer( "model", "test", destination ) ) {
			owner.with( Mdl.BASIS );
			assertThrows( IllegalStateException.class,
					() -> new Writer( "model", "test", alias.resolve( "report" ) ) );
			assertEquals( "basis", new Reader( destination ).read().entries.get( 0 ).description );
		}
		try( Writer replacement = new Writer( "model", "alias", alias.resolve( "report" ) ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertEquals( "alias", new Reader( destination ).read().meta.testTitle );
	}

	private static Path symlink( Path link, Path target ) throws IOException {
		try {
			return Files.createSymbolicLink( link, target );
		}
		catch( UnsupportedOperationException | java.nio.file.FileSystemException e ) {
			Assumptions.abort( "Symbolic links unavailable: " + e );
			throw e;
		}
	}

	/**
	 * Neither direction of nested replacement may pass an initializing owner.
	 *
	 * @param childOwns Whether the descendant initializes first
	 * @param dir       Isolated container
	 * @throws Exception On worker failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void nestedInitialization( boolean childOwns, @TempDir Path dir ) throws Exception {
		Path parent = dir.resolve( "tree" );
		Path child = parent.resolve( "sub/report" );
		Path owned = childOwns ? child : parent;
		Path competing = childOwns ? parent : child;
		CountDownLatch clearing = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		ReportFiles files = new ReportFiles() {
			@Override
			void clear( Path root ) {
				// No destination yet: a late child must still detect the parent's claim.
				clearing.countDown();
				await( release );
				super.clear( root );
			}
		};
		var worker = Executors.newSingleThreadExecutor();
		try {
			var initialized = worker.submit( () -> new Writer( "model", "owner", owned,
					Writer.Indexing.IMMEDIATE, files ) );
			assertTrue( clearing.await( 10, TimeUnit.SECONDS ) );
			try {
				if( childOwns ) {
					assertThrows( java.nio.file.NoSuchFileException.class,
							() -> Files.readAllBytes( dir.resolve( ".tree.flow-writer.lock" ) ) );
				}
				assertThrows( IllegalStateException.class, () -> {
					try( Writer competitor = new Writer( "model", "competitor", competing ) ) {
						competitor.with( Mdl.CHILD );
					}
				} );
			}
			finally {
				release.countDown();
				try( Writer owner = initialized.get( 10, TimeUnit.SECONDS ) ) {
					owner.with( Mdl.BASIS );
					assertEquals( "basis", new Reader( owned ).read().entries.get( 0 ).description );
				}
			}
			try( Writer replacement = new Writer( "model", "replacement", competing ) ) {
				replacement.with( Mdl.CHILD );
				assertEquals( "child", new Reader( competing ).read().entries.get( 0 ).description );
			}
		}
		finally {
			release.countDown();
			worker.shutdownNow();
			assertTrue( worker.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 10, TimeUnit.SECONDS ) );
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( e );
		}
	}

	/**
	 * Native Windows must keep the claimed name linked while its handle is open.
	 *
	 * @param publication Exercise the short publication claim
	 * @param dir         Isolated container
	 * @throws Exception On process or filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	@EnabledOnOs(OS.WINDOWS)
	void nativeClaimCannotBeUnlinked( boolean publication, @TempDir Path dir ) throws Exception {
		Path destination = dir.resolve( "report" );
		Path latest = dir.resolve( "latest" );
		Path sidecar = dir
				.resolve( publication ? ".latest.flow-publication.lock" : ".report.flow-writer.lock" );
		try( Peer peer = new Peer( destination, latest );
				Peer competitor = new Peer( publication ? dir.resolve( "other" ) : destination, latest ) ) {
			if( publication ) {
				assertEquals( "owned", peer.command( "open" ) );
				assertEquals( "owned", competitor.command( "open" ) );
			}
			assertEquals( "opened", peer.command( publication ? "delay-publish" : "delay-open" ) );
			assertThrows( java.nio.file.FileSystemException.class, () -> Files.delete( sidecar ) );
			assertThrows( java.nio.file.FileSystemException.class,
					() -> Files.move( sidecar, dir.resolve( "moved-claim" ) ) );
			assertEquals( "active", competitor.command( publication ? "publish" : "open" ) );
			assertEquals( publication ? "published" : "owned", peer.command( "resume" ) );
			assertEquals( "basis", new Reader( destination ).read().entries.get( 0 ).description );
			if( publication ) {
				assertEquals( "published", Files.readString( latest ) );
			}
			else {
				assertEquals( "closed", peer.command( "close" ) );
			}
			Files.delete( sidecar );
		}
	}

	/**
	 * A child opened after its parent's scan cannot own an unlinked claim.
	 *
	 * @param publication Exercise the short publication claim
	 * @param dir         Isolated container
	 * @throws Exception On process or filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void unlinkedClaimAfterParentScan( boolean publication, @TempDir Path dir ) throws Exception {
		Path parent = Files.createDirectory( dir.resolve( "tree" ) );
		Path destination = publication ? dir.resolve( "report" ) : parent.resolve( "child" );
		Path latest = parent.resolve( "latest" );
		Path sidecar = parent
				.resolve( publication ? ".latest.flow-publication.lock" : ".child.flow-writer.lock" );
		CountDownLatch clearing = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		ReportFiles files = new ReportFiles() {
			@Override
			void clear( Path root ) {
				clearing.countDown();
				await( release );
				// Isolate the unlink from other clear failures (notably Windows deny-delete).
				QuietFiles.wrap( () -> {
					Files.delete( sidecar );
					return null;
				} );
				super.clear( root );
			}
		};
		var worker = Executors.newSingleThreadExecutor();
		try( Peer peer = new Peer( destination, latest ) ) {
			if( publication ) {
				assertEquals( "owned", peer.command( "open" ) );
			}
			var replaced = worker.submit( () -> {
				try( Writer ancestor = new Writer( "model", "parent", parent,
						Writer.Indexing.IMMEDIATE, files ) ) {
					ancestor.with( Mdl.BASIS );
				}
			} );
			assertTrue( clearing.await( 10, TimeUnit.SECONDS ) );
			assertEquals( "opened", peer.command( publication ? "delay-publish" : "delay-open" ) );
			release.countDown();
			try {
				replaced.get( 10, TimeUnit.SECONDS );
			}
			catch( java.util.concurrent.ExecutionException e ) {
				if( e.getCause() instanceof java.io.UncheckedIOException
						&& e.getCause().getCause()instanceof java.nio.file.FileSystemException denied
						&& sidecar.toString().equals( denied.getFile() ) ) {
					assertEquals( publication ? "published" : "owned", peer.command( "resume" ) );
					if( !publication ) {
						assertEquals( "closed", peer.command( "close" ) );
					}
					Assumptions.abort( "Open claim unlink is denied by this filesystem: " + denied );
				}
				throw e;
			}
			assertThrows( java.nio.file.NoSuchFileException.class,
					() -> Files.readAttributes( sidecar,
							java.nio.file.attribute.BasicFileAttributes.class ) );
			Files.createFile( sidecar );
			Files.writeString( latest, "retain" );
			assertEquals( "changed", peer.command( "resume" ) );
			assertEquals( "retain", Files.readString( latest ) );
			assertEquals( "parent", new Reader( parent ).read().meta.testTitle );
			try( Peer fresh = new Peer( destination ) ) {
				assertEquals( "owned", fresh.command( "open" ) );
				assertEquals( "closed", fresh.command( "close" ) );
			}
		}
		finally {
			release.countDown();
			worker.shutdownNow();
			assertTrue( worker.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/**
	 * Nested probes must preserve both local and separate-process ownership.
	 *
	 * @param childOwns Whether the descendant owns the namespace
	 * @param dir       Isolated container
	 * @throws Exception On process failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void nestedProcesses( boolean childOwns, @TempDir Path dir ) throws Exception {
		Path parent = dir.resolve( "tree" );
		Path child = parent.resolve( "sub/report" );
		Path owned = childOwns ? child : parent;
		Path competing = childOwns ? parent : child;
		try( Writer owner = new Writer( "model", "owner", owned ); Peer peer = new Peer( competing ) ) {
			owner.with( Mdl.BASIS );
			assertThrows( IllegalStateException.class, () -> {
				try( Writer competitor = new Writer( "model", "competitor", competing ) ) {
					competitor.with( Mdl.CHILD );
				}
			} );
			assertEquals( "active", peer.command( "open" ) );
			assertEquals( "active", peer.command( "open" ) );
			assertEquals( "basis", new Reader( owned ).read().entries.get( 0 ).description );
		}
		try( Peer peer = new Peer( owned ) ) {
			assertEquals( "owned", peer.command( "open" ) );
			assertThrows( IllegalStateException.class, () -> {
				try( Writer competitor = new Writer( "model", "competitor", competing ) ) {
					competitor.with( Mdl.CHILD );
				}
			} );
			assertEquals( "process", new Reader( owned ).read().meta.testTitle );
			assertEquals( "closed", peer.command( "close" ) );
		}
		try( Writer replacement = new Writer( "model", "replacement", competing ) ) {
			replacement.with( Mdl.CHILD );
		}
	}

	/**
	 * A configured advertisement is checked before clearing, even outside the
	 * report.
	 *
	 * @param dir Isolated container
	 * @throws IOException On fixture failure
	 */
	@Test
	void configuredLatestFailureBeforeClear( @TempDir Path dir ) throws IOException {
		Path destination = dir.resolve( "sub/path/report" );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		Path notDirectory = Files.writeString( dir.resolve( "advertisements" ), "retain" );
		assertThrows( java.io.UncheckedIOException.class, () -> {
			try( Writer replacement = new Writer( "model", "replacement", destination,
					Writer.Indexing.IMMEDIATE, notDirectory.resolve( "latest" ) ) ) {
				replacement.with( Mdl.CHILD );
			}
		} );
		assertEquals( "old", new Reader( destination ).read().meta.testTitle );
		assertEquals( "retain", Files.readString( notDirectory ) );
	}

	/**
	 * Different report parents still share the configured advertisement claim.
	 *
	 * @param dir Isolated container
	 * @throws Exception On worker failure
	 */
	@Test
	void configuredPublication( @TempDir Path dir ) throws Exception {
		Path latest = Files.createDirectory( dir.resolve( "advertisements" ) ).resolve( "latest" );
		Path destination = dir.resolve( "sub/path/report" );
		CountDownLatch publishing = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		Writer writer = new Writer( "model", "owner", destination, Writer.Indexing.FINAL_ONLY,
				dir.resolve( "unused/../advertisements/latest" ) ).with( Mdl.BASIS ).onClose( path -> {
					assertEquals( QuietFiles.wrap( destination::toRealPath ), path );
					assertEquals( "basis", new Reader( path ).read().entries.get( 0 ).description );
					publishing.countDown();
					await( release );
				} );
		var worker = Executors.newSingleThreadExecutor();
		try {
			var closed = worker.submit( writer::close );
			assertTrue( publishing.await( 10, TimeUnit.SECONDS ) );
			try {
				Writer competitor = new Writer( "model", "competitor", dir.resolve( "other/report" ),
						Writer.Indexing.FINAL_ONLY, latest ).onClose( path -> {
							throw new AssertionError( "Configured publication actions must not overlap" );
						} );
				assertThrows( IllegalStateException.class, competitor::close );
				assertThrows( IllegalStateException.class, () -> {
					try( Writer replacingParent = new Writer( "model", "parent", latest.getParent() ) ) {
						replacingParent.with( Mdl.CHILD );
					}
				} );
				try( Peer peer = new Peer( latest.getParent() ) ) {
					assertEquals( "active", peer.command( "open" ) );
				}
			}
			finally {
				release.countDown();
				closed.get( 10, TimeUnit.SECONDS );
			}
		}
		finally {
			release.countDown();
			worker.shutdownNow();
			assertTrue( worker.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
		try( Writer replacement = new Writer( "model", "replacement", dir.resolve( "other/report" ),
				Writer.Indexing.FINAL_ONLY, latest ).onClose( path -> {
					assertEquals( "replacement", new Reader( path ).read().meta.testTitle );
				} ) ) {
			replacement.with( Mdl.CHILD );
		}
	}

	/**
	 * Publication cannot start beneath an already initializing replacement.
	 *
	 * @param dir Isolated container
	 * @throws Exception On worker failure
	 */
	@Test
	void publicationDuringParentInitialization( @TempDir Path dir ) throws Exception {
		Path parent = dir.resolve( "advertisements" );
		Writer publisher = new Writer( "model", "publisher", dir.resolve( "reports/run" ),
				Writer.Indexing.FINAL_ONLY, parent.resolve( "latest" ) ).with( Mdl.BASIS )
						.onClose( path -> {
							throw new AssertionError( "Publication parent is being replaced" );
						} );
		CountDownLatch clearing = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		ReportFiles files = new ReportFiles() {
			@Override
			void clear( Path root ) {
				clearing.countDown();
				await( release );
				super.clear( root );
			}
		};
		var worker = Executors.newSingleThreadExecutor();
		try {
			var initialized = worker.submit( () -> new Writer( "model", "parent", parent,
					Writer.Indexing.IMMEDIATE, files ) );
			assertTrue( clearing.await( 10, TimeUnit.SECONDS ) );
			try {
				assertThrows( IllegalStateException.class, publisher::close );
				assertEquals( "publisher", new Reader( publisher.path() ).read().meta.testTitle );
			}
			finally {
				release.countDown();
				try( Writer owner = initialized.get( 10, TimeUnit.SECONDS ) ) {
					owner.with( Mdl.CHILD );
				}
			}
		}
		finally {
			release.countDown();
			worker.shutdownNow();
			assertTrue( worker.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/**
	 * The non-sibling link is withdrawn before the first destructive operation.
	 *
	 * @param relative Use a relative target
	 * @param dir      Isolated container
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void withdrawConfiguredLatest( boolean relative, @TempDir Path dir ) throws Exception {
		Path destination = dir.resolve( "sub/path/report" );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		Path latest = symlink( dir.resolve( "latest" ),
				relative ? Path.of( "sub/path/report" ) : destination );
		ReportFiles files = new ReportFiles() {
			@Override
			void clear( Path root ) {
				assertThrows( java.nio.file.NoSuchFileException.class,
						() -> Files.readSymbolicLink( latest ) );
				super.clear( root );
			}
		};
		try( Writer replacement = new Writer( "model", "replacement", destination,
				Writer.Indexing.FINAL_ONLY, files, latest ).onClose( path -> {
					QuietFiles.wrap( () -> Files.createSymbolicLink( latest, path ) );
				} ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertEquals( "replacement", new Reader( latest ).read().meta.testTitle );
	}

	/**
	 * An explicit output named latest stays readable; it is not an advertisement.
	 *
	 * @param kind       Existing output entry
	 * @param configured Supply the advertisement explicitly
	 * @param dir        Isolated container
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@CsvSource({ "file,false", "directory,true", "relative,false", "absolute,true" })
	void explicitLatestOutput( String kind, boolean configured, @TempDir Path dir ) throws Exception {
		Path latest = dir.resolve( "latest" );
		if( "file".equals( kind ) ) {
			Files.writeString( latest, "old" );
		}
		else if( "directory".equals( kind ) ) {
			Files.createDirectory( latest );
		}
		else {
			Path previous = dir.resolve( "old-report" );
			try( Writer old = new Writer( "model", "old", previous ) ) {
				old.with( Mdl.BASIS );
			}
			symlink( latest, "relative".equals( kind ) ? Path.of( "old-report" ) : previous );
		}
		try( Writer replacement = configured
				? new Writer( "model", "replacement", latest, Writer.Indexing.IMMEDIATE, latest )
				: new Writer( "model", "replacement", latest ) ) {
			replacement.with( Mdl.CHILD );
			assertEquals( latest, replacement.path() );
			assertEquals( "child", new Reader( replacement.path() ).read().entries.get( 0 ).description );
		}
		assertEquals( "replacement", new Reader( latest ).read().meta.testTitle );
		if( "relative".equals( kind ) || "absolute".equals( kind ) ) {
			assertEquals( "replacement",
					new Reader( dir.resolve( "old-report" ) ).read().meta.testTitle );
			assertEquals(
					"relative".equals( kind ) ? Path.of( "old-report" ) : dir.resolve( "old-report" ),
					Files.readSymbolicLink( latest ) );
		}
	}

	/**
	 * Parent traversal is resolved physically before recognizing an output alias.
	 *
	 * @param configured Supply the advertisement explicitly
	 * @param dir        Isolated container
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void explicitLatestThroughParentAlias( boolean configured, @TempDir Path dir ) throws Exception {
		Path real = Files.createDirectories( dir.resolve( "real/sub" ) ).getParent();
		Path alias = symlink( dir.resolve( "alias" ), real.resolve( "sub" ) );
		Assumptions.assumeTrue( Files.isSameFile( alias.resolve( ".." ), real ),
				"This provider resolves parent traversal lexically" );
		Path previous = real.resolve( "old-report" );
		try( Writer old = new Writer( "model", "old", previous ) ) {
			old.with( Mdl.BASIS );
		}
		Path latest = symlink( real.resolve( "latest" ), Path.of( "old-report" ) );
		Path requested = alias.resolve( "../latest" );
		try( Writer replacement = new Writer( "model", "replacement", requested,
				Writer.Indexing.IMMEDIATE, configured ? latest : null ) ) {
			replacement.with( Mdl.CHILD );
			assertEquals( requested, replacement.path() );
			assertEquals( "child", new Reader( requested ).read().entries.get( 0 ).description );
		}
		assertEquals( "replacement", new Reader( requested ).read().meta.testTitle );
		assertEquals( Path.of( "old-report" ), Files.readSymbolicLink( latest ) );
	}

	/**
	 * Scanning report contents does not follow links into other active reports.
	 *
	 * @param dir Isolated container
	 * @throws Exception On filesystem failure
	 */
	@Test
	void descendantScanDoesNotFollowLinks( @TempDir Path dir ) throws Exception {
		Path destination = Files.createDirectory( dir.resolve( "report" ) );
		Path foreign = Files.createDirectory( dir.resolve( "foreign" ) );
		symlink( destination.resolve( "link" ), foreign );
		try( Writer owner = new Writer( "model", "foreign", foreign.resolve( "report" ) );
				Writer replacement = new Writer( "model", "replacement", destination ) ) {
			owner.with( Mdl.BASIS );
			replacement.with( Mdl.CHILD );
			assertEquals( "foreign", new Reader( owner.path() ).read().meta.testTitle );
		}
	}

	/**
	 * A malformed reserved claim must fail before deleting existing evidence.
	 *
	 * @param dir Isolated container
	 * @throws Exception On fixture failure
	 */
	@Test
	void failedDescendantProbePreservesOutput( @TempDir Path dir ) throws Exception {
		Path destination = dir.resolve( "report" );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		Path unusable = Files.createDirectory( destination.resolve( ".child.flow-writer.lock" ) );
		assertThrows( java.io.UncheckedIOException.class, () -> {
			try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
				replacement.with( Mdl.CHILD );
			}
		} );
		assertEquals( "old", new Reader( destination ).read().meta.testTitle );
		Files.delete( unusable );
		Files.createFile( unusable );
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertThrows( java.nio.file.NoSuchFileException.class, () -> Files.readAllBytes( unusable ) );
	}

	/**
	 * The configured latest name is preserved rather than followed to foreign
	 * output.
	 *
	 * @param link Use a foreign symlink rather than an ordinary file
	 * @param dir  Isolated container
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void leaveUnrelatedConfiguredLatest( boolean link, @TempDir Path dir ) throws Exception {
		Path latest = dir.resolve( "latest" );
		Path foreign = Files.createDirectory( dir.resolve( "foreign" ) );
		if( link ) {
			symlink( latest, foreign );
		}
		else {
			Files.writeString( latest, "retain" );
		}
		try( Writer writer = new Writer( "model", "test", dir.resolve( "sub/path/report" ),
				Writer.Indexing.IMMEDIATE, latest ).onClose( path -> {
					assertThrows( IllegalStateException.class, () -> {
						try( Writer parent = new Writer( "model", "parent", dir ) ) {
							parent.with( Mdl.CHILD );
						}
					} );
				} ) ) {
			writer.with( Mdl.BASIS );
		}
		if( link ) {
			assertEquals( foreign, Files.readSymbolicLink( latest ) );
		}
		else {
			assertEquals( "retain", Files.readString( latest ) );
		}
		// No claim was redirected into the foreign link target.
		try( var children = Files.list( foreign ) ) {
			assertEquals( 0, children.count() );
		}
	}

	/**
	 * Disposing failed updates releases ownership but never becomes success.
	 *
	 * @param dir Isolated container
	 */
	@Test
	void failedUpdateRelease( @TempDir Path dir ) {
		Path destination = dir.resolve( "report" );
		Writer writer = new Writer( "model", "test", destination );
		writer.with( Mdl.BASIS );
		AssertionError original = new AssertionError( "callback" );
		assertSame( original, assertThrows( AssertionError.class,
				() -> writer.with( Mdl.CHILD, detail -> {
					throw original;
				} ) ) );
		assertThrows( IllegalStateException.class,
				() -> new Writer( "model", "test", destination ) );
		assertEquals( "basis", new Reader( destination ).read().entries.get( 0 ).description );
		assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
			assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
			assertThrows( IllegalStateException.class,
					() -> new Writer( "model", "test", destination ) );
		}
	}

	/**
	 * Failed construction has no caller-owned writer available for disposal.
	 *
	 * @param dir Isolated container
	 * @throws IOException On fixture failure
	 */
	@Test
	void failedInitializationRelease( @TempDir Path dir ) throws IOException {
		Path destination = dir.resolve( "report" );
		ReportFiles files = new ReportFiles() {
			@Override
			void clear( Path root ) {
				super.clear( root );
				QuietFiles.createDirectories( root );
				QuietFiles.write( root.resolve( "res" ), new byte[] { 42 } );
			}
		};
		assertThrows( RuntimeException.class,
				() -> new Writer( "model", "test", destination, Writer.Indexing.FINAL_ONLY, files ) );
		assertEquals( 42, Files.readAllBytes( destination.resolve( "res" ) )[0] );
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertEquals( "replacement", new Reader( destination ).read().meta.testTitle );
	}

	/**
	 * The successful artifact stays owned until publication has actually returned.
	 *
	 * @param dir Isolated container
	 * @throws Exception On worker failure
	 */
	@Test
	void ownershipDuringPublication( @TempDir Path dir ) throws Exception {
		Path destination = dir.resolve( "report" );
		CountDownLatch publishing = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		AtomicInteger calls = new AtomicInteger();
		Writer writer = new Writer( "model", "test", destination, Writer.Indexing.FINAL_ONLY )
				.onClose( path -> {
					assertEquals( "basis", new Reader( path ).read().entries.get( 0 ).description );
					calls.incrementAndGet();
					publishing.countDown();
					await( release );
				} ).with( Mdl.BASIS );
		var worker = Executors.newSingleThreadExecutor();
		try( Peer peer = new Peer( destination ) ) {
			var close = worker.submit( writer::close );
			assertTrue( publishing.await( 10, TimeUnit.SECONDS ), "close must invoke publication" );
			assertThrows( IllegalStateException.class,
					() -> new Writer( "model", "competitor", destination ) );
			assertEquals( "active", peer.command( "open" ) );
			Writer sibling = new Writer( "model", "sibling", dir.resolve( "sibling" ),
					Writer.Indexing.FINAL_ONLY ).onClose( path -> {
						throw new AssertionError( "Competing latest actions must not overlap" );
					} );
			assertThrows( IllegalStateException.class, sibling::close );
			release.countDown();
			close.get( 10, TimeUnit.SECONDS );
			writer.close();
			assertEquals( 1, calls.get() );
			assertEquals( "owned", peer.command( "open" ) );
			assertEquals( "closed", peer.command( "close" ) );
		}
		finally {
			release.countDown();
			worker.shutdownNow();
			assertTrue( worker.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/**
	 * Reusing an advertised target withdraws its link before any destructive IO.
	 *
	 * @param relative Use a relative link target
	 * @param dir      Isolated container
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void withdrawOwnedLatest( boolean relative, @TempDir Path dir ) throws Exception {
		Path destination = dir.resolve( "report" );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		Path latest = symlink( dir.resolve( "latest" ), relative ? Path.of( "report" ) : destination );
		ReportFiles files = new ReportFiles() {
			@Override
			void clear( Path root ) {
				assertFalse( Files.exists( latest, java.nio.file.LinkOption.NOFOLLOW_LINKS ),
						"withdraw before clearing the advertised report" );
				super.clear( root );
			}
		};
		try( Writer replacement = new Writer( "model", "replacement", destination,
				Writer.Indexing.FINAL_ONLY, files ).onClose( path -> {
					assertEquals( "replacement", new Reader( path ).read().meta.testTitle );
					QuietFiles.wrap( () -> Files.createSymbolicLink( latest, path ) );
				} ) ) {
			replacement.with( Mdl.CHILD );
			assertFalse( Files.exists( latest, java.nio.file.LinkOption.NOFOLLOW_LINKS ) );
		}
		assertEquals( "replacement", new Reader( latest ).read().meta.testTitle );
	}

	/**
	 * Another report's advertisement and an ordinary latest file are not ours.
	 *
	 * @param link Use an unrelated symlink instead of an ordinary file
	 * @param dir  Isolated container
	 * @throws Exception On filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void leaveUnrelatedLatest( boolean link, @TempDir Path dir ) throws Exception {
		Path latest = dir.resolve( "latest" );
		if( link ) {
			symlink( latest, dir.resolve( "unrelated" ) );
		}
		else {
			Files.writeString( latest, "not an advertisement" );
		}
		try( Writer writer = new Writer( "model", "test", dir.resolve( "report" ) ) ) {
			writer.with( Mdl.BASIS );
		}
		if( link ) {
			assertEquals( dir.resolve( "unrelated" ), Files.readSymbolicLink( latest ) );
		}
		else {
			assertEquals( "not an advertisement", Files.readString( latest ) );
		}
	}

	/**
	 * An advertisement failure never retries its action, but does release output.
	 *
	 * @param dir Isolated container
	 */
	@Test
	void failedPublicationRelease( @TempDir Path dir ) {
		Path destination = dir.resolve( "report" );
		AtomicInteger calls = new AtomicInteger();
		IllegalStateException original = new IllegalStateException( "publication" );
		Writer writer = new Writer( "model", "test", destination, Writer.Indexing.FINAL_ONLY )
				.onClose( path -> {
					calls.incrementAndGet();
					assertEquals( "test", new Reader( path ).read().meta.testTitle );
					throw original;
				} ).with( Mdl.BASIS );
		assertSame( original, assertThrows( IllegalStateException.class, writer::close ) );
		assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
		assertEquals( 1, calls.get() );
		assertEquals( "basis", new Reader( destination ).read().entries.get( 0 ).description );
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
		}
	}

	/**
	 * An unusable claim fails before replacement; there is no unlocked fallback.
	 *
	 * @param dir Isolated container
	 * @throws Exception On filesystem failure
	 */
	@Test
	void failedClaimPreservesOutput( @TempDir Path dir ) throws Exception {
		Path destination = dir.resolve( "report" );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		Path sidecar = dir.resolve( ".report.flow-writer.lock" );
		Files.delete( sidecar );
		Files.createDirectory( sidecar );
		assertThrows( java.io.UncheckedIOException.class,
				() -> new Writer( "model", "replacement", destination ) );
		assertEquals( "old", new Reader( destination ).read().meta.testTitle );
		Files.delete( sidecar );
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertEquals( "replacement", new Reader( destination ).read().meta.testTitle );
	}

	private static class Peer implements AutoCloseable {
		private final Process process;
		private final DataInputStream input;
		private final DataOutputStream output;
		private final java.util.concurrent.ExecutorService reader = Executors.newSingleThreadExecutor();

		Peer( Path destination ) throws IOException {
			this( destination, null );
		}

		Peer( Path destination, Path latest ) throws IOException {
			process = new ProcessBuilder(
					Path.of( System.getProperty( "java.home" ), "bin", "java" ).toString(),
					"-cp",
					System.getProperty( "surefire.test.class.path", System.getProperty( "java.class.path" ) ),
					ProcessOwner.class.getName(), destination.toString(),
					latest == null ? "" : latest.toString() )
							.redirectError( ProcessBuilder.Redirect.INHERIT ).start();
			input = new DataInputStream( process.getInputStream() );
			output = new DataOutputStream( process.getOutputStream() );
		}

		String command( String command ) throws Exception {
			output.writeUTF( command );
			output.flush();
			return reader.submit( () -> input.readUTF() ).get( 10, TimeUnit.SECONDS );
		}

		@Override
		public void close() throws Exception {
			process.destroyForcibly();
			assertTrue( process.waitFor( 10, TimeUnit.SECONDS ) );
			input.close();
			output.close();
			reader.shutdownNow();
			assertTrue( reader.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/** A separate JVM driven by pipes, not polling or fixed sleeps. */
	public static class ProcessOwner {
		/**
		 * @param args Destination and optional advertisement
		 * @throws IOException On pipe failure
		 */
		public static void main( String[] args ) throws IOException {
			Path destination = Path.of( args[0] );
			Path latest = args[1].isEmpty() ? null : Path.of( args[1] );
			try( DataInputStream input = new DataInputStream( new FileInputStream( FileDescriptor.in ) );
					DataOutputStream output = new DataOutputStream(
							new FileOutputStream( FileDescriptor.out ) ) ) {
				Writer writer = null;
				while( true ) {
					String command = input.readUTF();
					if( command.startsWith( "delay-" ) ) {
						Path expected = "delay-open".equals( command )
								? destination
										.resolveSibling( "." + destination.getFileName() + ".flow-writer.lock" )
								: latest.resolveSibling( ".latest.flow-publication.lock" );
						ReportClaim.afterOpen = path -> {
							if( path.equals( expected ) ) {
								QuietFiles.wrap( () -> {
									output.writeUTF( "opened" );
									output.flush();
									assertEquals( "resume", input.readUTF() );
									return null;
								} );
							}
						};
						command = command.substring( "delay-".length() );
					}
					if( "open".equals( command ) || "publish".equals( command ) ) {
						try {
							if( "open".equals( command ) ) {
								writer = new Writer( "model", "process", destination, Writer.Indexing.IMMEDIATE,
										latest )
												.with( Mdl.BASIS );
								output.writeUTF( "owned" );
							}
							else {
								writer
										.onClose(
												path -> QuietFiles.wrap( () -> Files.writeString( latest, "published" ) ) )
										.close();
								writer = null;
								output.writeUTF( "published" );
							}
						}
						catch( IllegalStateException e ) {
							if( !e.getMessage().startsWith( "Report destination is active:" ) ) {
								throw e;
							}
							output.writeUTF( "active" );
						}
						catch( java.io.UncheckedIOException e ) {
							if( !e.getCause().getMessage().startsWith( "Report claim changed:" ) ) {
								throw e;
							}
							output.writeUTF( "changed" );
						}
						finally {
							ReportClaim.afterOpen = path -> {
							};
						}
					}
					else if( "close".equals( command ) ) {
						writer.close();
						writer = null;
						output.writeUTF( "closed" );
					}
					else if( "exit".equals( command ) ) {
						output.writeUTF( "exiting" );
						output.flush();
						return;
					}
					else {
						throw new IllegalArgumentException( command );
					}
					output.flush();
				}
			}
		}
	}
}
