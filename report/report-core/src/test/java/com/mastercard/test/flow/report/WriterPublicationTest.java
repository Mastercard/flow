package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mastercard.test.flow.report.Writer.Indexing;

/**
 * Index publication and failure latching, exercised through the real filesystem
 * and report assets.
 */
@SuppressWarnings("static-method")
class WriterPublicationTest {

	/**
	 * Write-once output has no index until close; immediate output has one from the
	 * first update.
	 *
	 * @param indexing Index policy
	 * @param dir      Isolated report destination
	 * @throws IOException On filesystem failure
	 */
	@ParameterizedTest
	@EnumSource(Indexing.class)
	void indexTiming( Indexing indexing, @TempDir Path dir ) throws IOException {
		Path index = dir.resolve( Writer.INDEX_FILE_NAME );
		try( Writer writer = new Writer( "model", "test", dir, indexing ) ) {
			writer.with( Mdl.BASIS ).with( Mdl.CHILD );
			assertEquals( indexing == Indexing.IMMEDIATE, Files.exists( index ) );
			writer.with( Mdl.BASIS, detail -> detail.tags.add( Writer.PASS_TAG ) );
		}
		assertEquals( 2, new Reader( dir ).read().entries.size() );
		try( var paths = Files.list( dir ) ) {
			assertFalse( paths.anyMatch( p -> p.getFileName().toString().endsWith( ".tmp" ) ) );
		}
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
		DETAIL, MOVE
	}

	/**
	 * A failed detail write or index publication cannot be retried into apparent
	 * success, and leaves no partial index behind.
	 *
	 * @param fault The failing filesystem operation
	 * @param dir   Isolated report destination
	 * @throws Exception On unexpected filesystem failure
	 */
	@ParameterizedTest
	@EnumSource(Fault.class)
	void failuresRemainObservable( Fault fault, @TempDir Path dir ) throws Exception {
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY );
		RuntimeException original;
		if( fault == Fault.DETAIL ) {
			Files.writeString( dir.resolve( Writer.DETAIL_DIR_NAME ), "not a directory" );
			original = assertThrows( RuntimeException.class, () -> writer.with( Mdl.BASIS ) );
		}
		else {
			writer.with( Mdl.BASIS );
			Files.createDirectory( dir.resolve( Writer.INDEX_FILE_NAME ) );
			original = assertThrows( RuntimeException.class, writer::close );
			assertTrue(
					Files.exists( dir.resolve( "detail/" + Writer.detailFilename( Mdl.BASIS ) + ".html" ) ) );
		}
		assertSame( original, assertThrows( IllegalStateException.class, writer::close ).getCause() );
		assertSame( original, assertThrows( IllegalStateException.class,
				() -> writer.with( Mdl.CHILD ) ).getCause() );
		assertFalse( Files.isRegularFile( dir.resolve( Writer.INDEX_FILE_NAME ) ) );
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
