package com.mastercard.test.flow.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.Index;

/** Exercises {@link Writer.Indexing} publication policy. */
@SuppressWarnings("static-method")
class WriterIndexingTest {

	/**
	 * Immediate compatibility and deferred publication checkpoints remain distinct.
	 */
	@Test
	void policyMatrix( @TempDir Path parent ) {
		Path immediateRoot = parent.resolve( "immediate" );
		Writer immediate = new Writer( "model", "immediate", immediateRoot );
		immediate.with( Mdl.CHILD );
		assertEquals( List.of( "child" ), descriptions( immediateRoot ) );
		immediate.with( Mdl.BASIS );
		assertEquals( List.of( "child", "basis" ), descriptions( immediateRoot ) );

		Path deferredRoot = parent.resolve( "deferred" );
		TrackingApp app = new TrackingApp( deferredRoot );
		Writer deferred = new Writer( "model", "deferred", deferredRoot,
				Writer.Indexing.FIRST_THEN_EXPLICIT, app );

		deferred.publishIndex();
		assertEquals( 0, app.indexEntryCounts.size(), "no empty publication" );

		deferred.with( Mdl.CHILD );
		assertEquals( List.of( "child" ), descriptions( deferredRoot ), "first snapshot" );
		deferred.with( Mdl.BASIS );
		assertEquals( List.of( "child" ), descriptions( deferredRoot ), "stale snapshot" );

		deferred.publishIndex();
		assertEquals( List.of( "child", "basis" ), descriptions( deferredRoot ), "explicit snapshot" );
		deferred.publishIndex();
		assertEquals( List.of( 1, 2 ), app.indexEntryCounts, "one plus final serialization" );

		Entry child = new Reader( deferredRoot ).read().entries.get( 0 );
		assertEquals( Writer.detailFilename( Mdl.BASIS ),
				new Reader( deferredRoot ).detail( child ).basis, "corrected basis detail" );
	}

	/**
	 * A stale snapshot keeps resolving after a repeated flow changes detail
	 * identity.
	 */
	@Test
	void supersededDeferredDetailRemainsReadable( @TempDir Path root ) {
		Writer writer = new Writer( "model", "test", root, Writer.Indexing.FIRST_THEN_EXPLICIT );
		Reader reader = new Reader( root );

		writer.with( Mdl.BASIS, detail -> detail.tags.add( "old identity" ) );
		Entry oldEntry = reader.read().entries.get( 0 );
		assertEquals( "basis", reader.detail( oldEntry ).description );

		writer.with( Mdl.BASIS, detail -> detail.tags.add( "new identity" ) );
		assertEquals( oldEntry.detail, reader.read().entries.get( 0 ).detail, "visible stale detail" );
		assertEquals( "basis", reader.detail( oldEntry ).description, "stale detail remains readable" );

		writer.publishIndex();
		Entry currentEntry = reader.read().entries.get( 0 );
		assertNotEquals( oldEntry.detail, currentEntry.detail );
		assertEquals( "basis", reader.detail( currentEntry ).description );
		assertTrue( Files.isRegularFile( root.resolve( "detail/" + oldEntry.detail + ".html" ) ) );
	}

	/**
	 * Deferred update failure is original and permanently prevents complete
	 * publication.
	 */
	@Test
	void failedUpdateInvalidatesPublication( @TempDir Path root ) {
		Writer writer = new Writer( "model", "test", root, Writer.Indexing.FIRST_THEN_EXPLICIT );
		writer.with( Mdl.BASIS );
		byte[] firstSnapshot = QuietFiles.readAllBytes( root.resolve( Writer.INDEX_FILE_NAME ) );
		RuntimeException failure = new RuntimeException( "callback failure" );

		assertSame( failure, assertThrows( RuntimeException.class,
				() -> writer.with( Mdl.CHILD, detail -> {
					throw failure;
				} ) ) );
		writer.with( Mdl.DEPENDENCY );

		IllegalStateException invalid = assertThrows( IllegalStateException.class,
				writer::publishIndex );
		assertSame( failure, invalid.getCause() );
		assertArrayEquals( firstSnapshot,
				QuietFiles.readAllBytes( root.resolve( Writer.INDEX_FILE_NAME ) ) );
	}

	/**
	 * Failed deferred publication preserves the prior snapshot and is never
	 * retried.
	 */
	@Test
	void failedPublicationPreservesSnapshotAndCleansTemporaryFile( @TempDir Path root )
			throws Exception {
		RuntimeException failure = new RuntimeException( "publication failure" );
		TrackingApp app = new TrackingApp( root );
		app.failIndexWrite = 2;
		app.failure = failure;
		Writer writer = new Writer( "model", "test", root,
				Writer.Indexing.FIRST_THEN_EXPLICIT, app );

		writer.with( Mdl.BASIS );
		byte[] firstSnapshot = QuietFiles.readAllBytes( root.resolve( Writer.INDEX_FILE_NAME ) );
		writer.with( Mdl.CHILD );

		assertSame( failure, assertThrows( RuntimeException.class, writer::publishIndex ) );
		assertArrayEquals( firstSnapshot,
				QuietFiles.readAllBytes( root.resolve( Writer.INDEX_FILE_NAME ) ) );
		try( var files = Files.list( root ) ) {
			assertEquals( List.of( Writer.INDEX_FILE_NAME ), files
					.filter( Files::isRegularFile )
					.map( path -> path.getFileName().toString() )
					.toList(), "temporary index cleanup" );
		}

		IllegalStateException invalid = assertThrows( IllegalStateException.class,
				writer::publishIndex );
		assertSame( failure, invalid.getCause() );
		assertEquals( List.of( 1, 2 ), app.indexEntryCounts, "failed publication is not retried" );
	}

	private static List<String> descriptions( Path root ) {
		return new Reader( root ).read().entries.stream().map( entry -> entry.description ).toList();
	}

	private static final class TrackingApp extends JsApp {
		private final List<Integer> indexEntryCounts = new ArrayList<>();
		private int failIndexWrite = -1;
		private RuntimeException failure;

		private TrackingApp( Path root ) {
			super( "/com/mastercard/test/flow/report", root.resolve( "res" ) );
		}

		@Override
		public void write( Object payload, Path destination ) {
			if( payload instanceof Index index ) {
				indexEntryCounts.add( index.entries.size() );
				if( indexEntryCounts.size() == failIndexWrite ) {
					throw failure;
				}
			}
			super.write( payload, destination );
		}
	}
}
