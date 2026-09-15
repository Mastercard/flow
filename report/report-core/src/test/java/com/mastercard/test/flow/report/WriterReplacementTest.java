package com.mastercard.test.flow.report;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Sequential replacement, advertisement and completion through the direct
 * writer.
 */
@SuppressWarnings("static-method")
class WriterReplacementTest {

	/**
	 * @param dir Isolated report container
	 */
	@Test
	void sequentialReuse( @TempDir Path dir ) {
		Path destination = dir.resolve( "configured" );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		try( Writer replacement = new Writer( "model", "replacement",
				dir.resolve( "unused/../configured" ) ) ) {
			replacement.with( Mdl.CHILD );
			assertEquals( "child", new Reader( destination ).read().entries.get( 0 ).description );
			assertEquals( 1, new Reader( destination ).read().entries.size() );
			assertFalse( Files.exists( destination.resolve( "detail/"
					+ Writer.detailFilename( Mdl.BASIS ) + ".html" ) ) );
		}
	}

	/**
	 * @param dir Isolated container
	 * @throws IOException On filesystem failure
	 */
	@Test
	void symlinkAlias( @TempDir Path dir ) throws IOException {
		Path real = Files.createDirectory( dir.resolve( "real" ) );
		Path alias = symlink( dir.resolve( "alias" ), real );
		Path destination = real.resolve( "report" );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		try( Writer replacement = new Writer( "model", "alias", alias.resolve( "report" ) ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertEquals( "alias", new Reader( destination ).read().meta.testTitle );
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
		assertThrows( UncheckedIOException.class, () -> new Writer( "model", "replacement",
				destination, Writer.Indexing.IMMEDIATE, notDirectory.resolve( "latest" ) ) );
		assertEquals( "old", new Reader( destination ).read().meta.testTitle );
		assertEquals( "retain", Files.readString( notDirectory ) );
	}

	/**
	 * Withdrawal precedes destructive IO; publication follows finalization.
	 *
	 * @param nested   Use a non-sibling configured destination
	 * @param relative Use a relative target
	 * @param dir      Isolated container
	 * @throws IOException On filesystem failure
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void withdrawLatest( boolean nested, boolean relative, @TempDir Path dir ) throws IOException {
		Path name = Path.of( nested ? "sub/path/report" : "report" );
		Path destination = dir.resolve( name );
		try( Writer previous = new Writer( "model", "old", destination ) ) {
			previous.with( Mdl.BASIS );
		}
		Path latest = symlink( dir.resolve( "latest" ), relative ? name : destination );
		ReportFiles files = new ReportFiles() {
			@Override
			void clear( Path root ) {
				assertFalse( Files.exists( latest, LinkOption.NOFOLLOW_LINKS ),
						"withdraw before clearing the advertised report" );
				super.clear( root );
			}
		};
		try( Writer replacement = new Writer( "model", "replacement", destination,
				Writer.Indexing.FINAL_ONLY, files, nested ? latest : null ).onClose( path -> {
					assertEquals( "replacement", new Reader( path ).read().meta.testTitle );
					QuietFiles.wrap( () -> Files.createSymbolicLink( latest, path ) );
				} ) ) {
			replacement.with( Mdl.CHILD );
			assertFalse( Files.exists( latest, LinkOption.NOFOLLOW_LINKS ) );
		}
		assertEquals( "replacement", new Reader( latest ).read().meta.testTitle );
	}

	/**
	 * An explicit output named latest stays readable; it is not an advertisement.
	 *
	 * @param kind       Existing output entry
	 * @param configured Supply the advertisement explicitly
	 * @param dir        Isolated container
	 * @throws IOException On filesystem failure
	 */
	@ParameterizedTest
	@CsvSource({ "file,false", "directory,true", "relative,false", "absolute,true" })
	void explicitLatestOutput( String kind, boolean configured, @TempDir Path dir )
			throws IOException {
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
		try( Writer replacement = new Writer( "model", "replacement", latest,
				Writer.Indexing.IMMEDIATE, configured ? latest : null ) ) {
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
	 * @throws IOException On filesystem failure
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void explicitLatestThroughParentAlias( boolean configured, @TempDir Path dir )
			throws IOException {
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
	 * Replacing report contents does not follow links into unrelated output.
	 *
	 * @param dir Isolated container
	 * @throws IOException On filesystem failure
	 */
	@Test
	void leaveLinkedContents( @TempDir Path dir ) throws IOException {
		Path destination = Files.createDirectory( dir.resolve( "report" ) );
		Path foreign = Files.createDirectory( dir.resolve( "foreign" ) );
		Files.writeString( foreign.resolve( "retain" ), "user content" );
		symlink( destination.resolve( "link" ), foreign );
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertEquals( "user content", Files.readString( foreign.resolve( "retain" ) ) );
	}

	/**
	 * @param nested Use an explicitly configured advertisement
	 * @param link   Use a foreign symlink rather than an ordinary file
	 * @param dir    Isolated container
	 * @throws IOException On filesystem failure
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void leaveUnrelatedLatest( boolean nested, boolean link, @TempDir Path dir ) throws IOException {
		Path latest = dir.resolve( "latest" );
		Path foreign = dir.resolve( "foreign" );
		if( link ) {
			symlink( latest, foreign );
		}
		else {
			Files.writeString( latest, "retain" );
		}
		try( Writer writer = new Writer( "model", "test",
				dir.resolve( nested ? "sub/path/report" : "report" ),
				Writer.Indexing.IMMEDIATE, nested ? latest : null ) ) {
			writer.with( Mdl.BASIS );
		}
		if( link ) {
			assertEquals( foreign, Files.readSymbolicLink( latest ) );
		}
		else {
			assertEquals( "retain", Files.readString( latest ) );
		}
		assertFalse( Files.exists( foreign ) );
	}

	/**
	 * Failed construction has no caller-owned writer available for disposal.
	 *
	 * @param dir Isolated container
	 * @throws IOException On fixture failure
	 */
	@Test
	void failedInitialization( @TempDir Path dir ) throws IOException {
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
	 * Completion runs synchronously once, with a readable canonical artifact.
	 *
	 * @param fail         Throw from the completion action
	 * @param setupFailure Block creation of the advertisement parent after
	 *                     construction
	 * @param dir          Isolated container
	 * @throws IOException On fixture failure
	 */
	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "false,true" })
	void publication( boolean fail, boolean setupFailure, @TempDir Path dir ) throws IOException {
		Path destination = dir.resolve( "sub/path/report" );
		Path advertisements = dir.resolve( "advertisements" );
		Path latest = advertisements.resolve( "nested/latest" );
		AtomicInteger calls = new AtomicInteger();
		Thread caller = Thread.currentThread();
		IllegalStateException original = new IllegalStateException( "publication" );
		Writer writer = new Writer( "model", "test", destination, Writer.Indexing.FINAL_ONLY,
				dir.resolve( "unused/../advertisements/nested/latest" ) ).onClose( path -> {
					assertSame( caller, Thread.currentThread() );
					assertEquals( QuietFiles.wrap( destination::toRealPath ), path );
					assertEquals( "basis", new Reader( path ).read().entries.get( 0 ).description );
					calls.incrementAndGet();
					QuietFiles.wrap( () -> Files.writeString( latest, path.toString(),
							CREATE_NEW ) );
					if( fail ) {
						throw original;
					}
				} ).with( Mdl.BASIS );
		assertEquals( 0, calls.get() );
		assertFalse( Files.exists( advertisements ), "create the parent only at publication" );
		if( setupFailure ) {
			Files.writeString( advertisements, "retain" );
			UncheckedIOException failure = assertThrows( UncheckedIOException.class, writer::close );
			assertEquals( 0, calls.get(), "setup failure must prevent the callback" );
			assertEquals( "retain", Files.readString( advertisements ) );
			Files.delete( advertisements );
			assertSame( failure, assertThrows( IllegalStateException.class, writer::close ).getCause() );
			assertSame( failure, assertThrows( IllegalStateException.class,
					() -> writer.with( Mdl.CHILD ) ).getCause() );
			assertFalse( Files.exists( advertisements ), "failed publication is not retried" );
		}
		else if( fail ) {
			assertSame( original, assertThrows( IllegalStateException.class, writer::close ) );
			assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
		}
		else {
			writer.close();
			writer.close();
		}
		assertEquals( setupFailure ? 0 : 1, calls.get() );
		if( !setupFailure ) {
			assertEquals( destination.toRealPath().toString(), Files.readString( latest ) );
		}
		try( Writer replacement = new Writer( "model", "replacement", destination ) ) {
			replacement.with( Mdl.CHILD );
		}
		assertEquals( "replacement", new Reader( destination ).read().meta.testTitle );
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
}
