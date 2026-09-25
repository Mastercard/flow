package com.mastercard.test.flow.report;

import static com.mastercard.test.flow.report.Latches.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mastercard.test.flow.report.Writer.Indexing;
import com.mastercard.test.flow.report.data.Entry;

/**
 * Direct writer final publication and failure contracts.
 */
@SuppressWarnings("static-method")
class WriterLifecycleTest {

	/**
	 * Close waits for the entire caller-owned update, including callback
	 * completion.
	 *
	 * @param dir Isolated report destination
	 * @throws Exception On unexpected worker failure
	 */
	@Test
	void closeDuringUpdate( @TempDir Path dir ) throws Exception {
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY );
		var workers = Executors.newFixedThreadPool( 2 );
		CountDownLatch entered = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		CountDownLatch closing = new CountDownLatch( 1 );
		try {
			var update = workers.submit( () -> writer.with( Mdl.BASIS, detail -> {
				entered.countDown();
				await( release );
				detail.motivation = "completed callback";
			} ) );
			await( entered );
			var close = workers.submit( () -> {
				closing.countDown();
				writer.close();
			} );
			await( closing );
			assertThrows( TimeoutException.class, () -> close.get( 200, TimeUnit.MILLISECONDS ) );
			release.countDown();
			update.get( 10, TimeUnit.SECONDS );
			close.get( 10, TimeUnit.SECONDS );
			Reader reader = new Reader( dir );
			assertEquals( "completed callback",
					reader.detail( reader.read().entries.get( 0 ) ).motivation );
		}
		finally {
			release.countDown();
			workers.shutdownNow();
			assertTrue( workers.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/**
	 * Resource cleanup must not replace the original error with self-suppression.
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void tryWithResourcesPreservesPrimary( @TempDir Path dir ) {
		AssertionError original = new AssertionError( "primary" );
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY );
		assertSame( original, assertThrows( AssertionError.class, () -> {
			try( writer ) {
				writer.with( Mdl.BASIS, detail -> {
					throw original;
				} );
			}
		} ) );
		assertEquals( 1, original.getSuppressed().length );
		assertSame( original, original.getSuppressed()[0].getCause() );
	}

	/**
	 * Empty finalized output is still discoverable by existing report consumers.
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void emptyReport( @TempDir Path dir ) {
		try( Writer writer = new Writer( "model", "empty", dir, Indexing.FINAL_ONLY ) ) {
			assertEquals( dir, writer.path() );
		}
		Reader reader = new Reader( dir );
		assertEquals( List.of(), reader.read().entries );
		assertEquals( "empty", reader.read().meta.testTitle );
		assertTrue( Reader.isReportDir( dir ) );
	}

	/**
	 * A callback failure remains observable without replaying it or publishing.
	 *
	 * @param indexing Index policy
	 * @param dir      Isolated report destination
	 */
	@ParameterizedTest
	@EnumSource(Indexing.class)
	void callbackFailureIsLatched( Indexing indexing, @TempDir Path dir ) {
		Writer writer = new Writer( "model", "test", dir, indexing );
		AssertionError failure = new AssertionError( "original failure" );
		assertSame( failure, assertThrows( AssertionError.class,
				() -> writer.with( Mdl.BASIS, detail -> {
					throw failure;
				} ) ) );
		assertSame( failure, assertThrows( IllegalStateException.class, writer::close ).getCause() );
		assertSame( failure, assertThrows( IllegalStateException.class,
				() -> writer.with( Mdl.CHILD, detail -> {
					throw new AssertionError( "Must not replay callbacks" );
				} ) ).getCause() );
		assertNull( new Reader( dir ).read() );
	}

	/**
	 * Details are synchronous, but the canonical index is published only on close.
	 *
	 * @param dir Isolated report destination
	 * @throws Exception On IO failure
	 */
	@Test
	void finalOnly( @TempDir Path dir ) throws Exception {
		Reader reader = new Reader( dir );
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY );
		writer.with( Mdl.BASIS ).with( Mdl.DEPENDENCY );
		assertNull( reader.read() );
		assertNotNull( reader.detail( new Entry( "basis", Mdl.BASIS.meta().tags(),
				Writer.detailFilename( Mdl.BASIS ) ) ) );
		writer.close();
		assertEquals( List.of( "dependency", "basis" ), reader.read().entries.stream()
				.map( e -> e.description ).toList() );
		reader.read().entries.forEach( e -> assertNotNull( reader.detail( e ) ) );
		byte[] index = Files.readAllBytes( dir.resolve( Writer.INDEX_FILE_NAME ) );
		writer.close();
		assertArrayEquals( index, Files.readAllBytes( dir.resolve( Writer.INDEX_FILE_NAME ) ) );
		assertThrows( IllegalStateException.class, () -> writer.with( Mdl.CHILD ) );
		try( var files = Files.list( dir ) ) {
			assertFalse( files.anyMatch( p -> p.getFileName().toString().endsWith( ".tmp" ) ) );
		}
	}
}
