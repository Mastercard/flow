package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mastercard.test.flow.report.Writer.Indexing;

/**
 * Injects faults only at the filesystem boundary, exercising the real writer,
 * serialization and report assets.
 */
@SuppressWarnings("static-method")
class WriterPublicationTest {

	/**
	 * Index work is counted independently of detail payloads and repeated updates.
	 *
	 * @param indexing Index policy
	 * @param dir      Isolated report destination
	 */
	@ParameterizedTest
	@EnumSource(Indexing.class)
	void indexWork( Indexing indexing, @TempDir Path dir ) {
		CountingReportFiles files = new CountingReportFiles();
		try( Writer writer = new Writer( "model", "test", dir, indexing, files ) ) {
			writer.with( Mdl.BASIS ).with( Mdl.CHILD ).with( Mdl.DEPENDENCY ).with( Mdl.DEPENDENT );
			assertEquals( indexing == Indexing.IMMEDIATE ? 10 : 0, files.indexEntries );
			writer.with( Mdl.BASIS, detail -> detail.tags.add( Writer.PASS_TAG ) );
			assertEquals( indexing == Indexing.IMMEDIATE ? 14 : 0, files.indexEntries );
		}
		assertEquals( indexing == Indexing.IMMEDIATE ? 5 : 1, files.indexWrites );
		assertEquals( indexing == Indexing.IMMEDIATE ? 14 : 4, files.indexEntries );
		assertEquals( 5, files.detailWrites );
		assertEquals( indexing == Indexing.FINAL_ONLY ? 1 : 0, files.diagnosticWrites );
	}

	/**
	 * A real filesystem replacement failure never falls back to non-atomic IO.
	 *
	 * @param dir Isolated report destination
	 * @throws IOException On fixture failure
	 */
	@Test
	void occupiedIndex( @TempDir Path dir ) throws IOException {
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY ).with( Mdl.BASIS );
		Path sentinel = dir.resolve( "index.html/retain.txt" );
		Files.createDirectory( sentinel.getParent() );
		Files.writeString( sentinel, "retain" );
		RuntimeException original = assertThrows( RuntimeException.class, writer::close );
		assertEquals( "retain", Files.readString( sentinel ) );
		assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
	}

	enum Fault {
		DETAIL, DIAGNOSTIC, TEMPORARY, WRITE, CLOSE, MOVE
	}

	/**
	 * A failed write, close or atomic move cannot be retried into apparent success.
	 *
	 * @param fault The failing filesystem operation
	 * @param dir   Isolated report destination
	 * @throws Exception On unexpected filesystem failure
	 */
	@ParameterizedTest
	@EnumSource(Fault.class)
	void failuresRemainObservable( Fault fault, @TempDir Path dir ) throws Exception {
		ReportFiles files = new ReportFiles() {
			@Override
			OutputStream open( Path path ) throws IOException {
				boolean temporary = path.getFileName().toString().endsWith( ".tmp" );
				if( fault == Fault.DIAGNOSTIC
						&& path.getFileName().toString().equals( Writer.DIAGNOSTICS_FILE_NAME ) ) {
					throw new IOException( "diagnostic failure" );
				}
				if( fault == Fault.DETAIL && !temporary ) {
					throw new IOException( "detail failure" );
				}
				return new FilterOutputStream( super.open( path ) ) {
					@Override
					public void write( byte[] bytes, int offset, int length ) throws IOException {
						if( temporary && fault == Fault.WRITE ) {
							throw new IOException( "temporary write failure" );
						}
						out.write( bytes, offset, length );
					}

					@Override
					public void close() throws IOException {
						super.close();
						if( temporary && fault == Fault.CLOSE ) {
							throw new IOException( "temporary close failure" );
						}
					}
				};
			}

			@Override
			Path temporary( Path root ) throws IOException {
				if( fault == Fault.TEMPORARY ) {
					throw new IOException( "temporary creation failure" );
				}
				return super.temporary( root );
			}

			@Override
			void publish( Path source, Path destination ) throws IOException {
				if( fault == Fault.MOVE ) {
					throw new AtomicMoveNotSupportedException( source.toString(), destination.toString(),
							"No atomic publication on this filesystem" );
				}
				super.publish( source, destination );
			}
		};
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY, files )
				.onClose( path -> {
					throw new AssertionError( "Failed output must not be advertised" );
				} );
		RuntimeException original;
		if( fault == Fault.DETAIL ) {
			original = assertThrows( RuntimeException.class, () -> writer.with( Mdl.BASIS ) );
		}
		else {
			writer.with( Mdl.BASIS );
			original = assertThrows( RuntimeException.class, writer::close );
			assertTrue(
					Files.exists( dir.resolve( "detail/" + Writer.detailFilename( Mdl.BASIS ) + ".html" ) ) );
		}
		assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
		assertSame( original, assertThrows( IllegalStateException.class,
				() -> writer.with( Mdl.CHILD ) ).getCause() );
		assertFalse( Files.exists( dir.resolve( Writer.INDEX_FILE_NAME ) ) );
		try( var paths = Files.list( dir ) ) {
			assertFalse( paths.anyMatch( p -> p.getFileName().toString().endsWith( ".tmp" ) ) );
		}
		try( Writer replacement = new Writer( "model", "replacement", dir ) ) {
			replacement.with( Mdl.CHILD );
			assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
			assertEquals( "replacement", new Reader( dir ).read().meta.testTitle );
		}
	}
}
