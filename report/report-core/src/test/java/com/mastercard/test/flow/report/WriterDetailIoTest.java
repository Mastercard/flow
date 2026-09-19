package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mastercard.test.flow.report.Writer.Indexing;
import com.mastercard.test.flow.report.data.Entry;

/**
 * Detail file writes happen outside the writer monitor, so one flow's disk IO
 * does not hold up other flows. These tests hold a write open through the
 * package-private file-write seam.
 */
@SuppressWarnings("static-method")
class WriterDetailIoTest {

	private final ExecutorService workers = Executors.newFixedThreadPool( 2 );

	/**
	 * Releases the worker threads
	 *
	 * @throws InterruptedException if shutdown is interrupted
	 */
	@AfterEach
	void shutdown() throws InterruptedException {
		workers.shutdownNow();
		assertTrue( workers.awaitTermination( 10, TimeUnit.SECONDS ) );
	}

	/**
	 * A file-write seam that pauses the first write until released
	 */
	private static class HeldWrite implements BiConsumer<Path, byte[]> {
		final CountDownLatch entered = new CountDownLatch( 1 );
		final CountDownLatch release = new CountDownLatch( 1 );

		@Override
		public void accept( Path path, byte[] bytes ) {
			if( entered.getCount() > 0 ) {
				entered.countDown();
				await( release );
			}
			QuietFiles.write( path, bytes );
		}
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 10, TimeUnit.SECONDS ), "latch timed out" );
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( e );
		}
	}

	/**
	 * A second update returns while the first update's detail write is still held
	 * open.
	 *
	 * @param indexing Index policy
	 * @param dir      Isolated report destination
	 * @throws Exception On unexpected worker failure
	 */
	@ParameterizedTest
	@EnumSource(Indexing.class)
	void updatesOverlapDetailWrites( Indexing indexing, @TempDir Path dir ) throws Exception {
		HeldWrite held = new HeldWrite();
		try( Writer writer = new Writer( "model", "test", dir, indexing, held ) ) {
			try {
				Future<?> first = workers.submit( () -> writer.with( Mdl.BASIS ) );
				await( held.entered );
				Future<?> second = workers.submit( () -> writer.with( Mdl.DEPENDENCY ) );
				second.get( 10, TimeUnit.SECONDS );
				assertThrows( TimeoutException.class, () -> first.get( 100, TimeUnit.MILLISECONDS ),
						"The first write is still held" );
			}
			finally {
				held.release.countDown();
			}
		}
		Reader reader = new Reader( dir );
		assertEquals( 2, reader.read().entries.size() );
		reader.read().entries
				.forEach( e -> assertEquals( e.description, reader.detail( e ).description ) );
	}

	/**
	 * Close does not return until every in-flight detail write has landed, and the
	 * published report links each detail correctly.
	 *
	 * @param dir Isolated report destination
	 * @throws Exception On unexpected worker failure
	 */
	@Test
	void closeWaitsForDetailWrites( @TempDir Path dir ) throws Exception {
		HeldWrite held = new HeldWrite();
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY, held );
		Future<?> update = workers.submit( () -> writer.with( Mdl.CHILD ) );
		await( held.entered );
		writer.with( Mdl.BASIS );
		Future<?> close = workers.submit( writer::close );
		assertThrows( TimeoutException.class, () -> close.get( 100, TimeUnit.MILLISECONDS ),
				"Close waits for the held write" );
		assertThrows( IllegalStateException.class, () -> writer.with( Mdl.DEPENDENCY ),
				"Updates are rejected once close has begun" );
		held.release.countDown();
		update.get( 10, TimeUnit.SECONDS );
		close.get( 10, TimeUnit.SECONDS );

		Reader reader = new Reader( dir );
		Map<String, Entry> entries = reader.read().entries.stream()
				.collect( Collectors.toMap( e -> e.description, e -> e ) );
		assertEquals( Set.of( "child", "basis" ), entries.keySet() );
		assertEquals( Writer.detailFilename( Mdl.BASIS ),
				reader.detail( entries.get( "child" ) ).basis );
	}

	/**
	 * A failed detail write propagates to its caller and latches the writer.
	 *
	 * @param indexing Index policy
	 * @param dir      Isolated report destination
	 */
	@ParameterizedTest
	@EnumSource(Indexing.class)
	void writeFailureIsLatched( Indexing indexing, @TempDir Path dir ) {
		UncheckedIOException failure = new UncheckedIOException( new IOException( "disk full" ) );
		Writer writer = new Writer( "model", "test", dir, indexing, ( path, bytes ) -> {
			throw failure;
		} );
		assertSame( failure,
				assertThrows( UncheckedIOException.class, () -> writer.with( Mdl.BASIS ) ) );
		assertSame( failure, assertThrows( IllegalStateException.class,
				() -> writer.with( Mdl.DEPENDENCY ) ).getCause() );
		assertSame( failure, assertThrows( IllegalStateException.class, writer::close ).getCause() );
	}
}
