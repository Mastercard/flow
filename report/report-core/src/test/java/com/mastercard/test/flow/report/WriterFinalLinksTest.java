package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.report.Writer.Indexing;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;

/**
 * Final membership links must use the last serialized evidence, not live data.
 */
@SuppressWarnings("static-method")
class WriterFinalLinksTest {

	/**
	 * A rename cannot overwrite another submitted flow, then delete its evidence by
	 * renaming away again. The diagnostic must precede any destructive IO.
	 *
	 * @param mode Index publication policy
	 * @param dir  Isolated report destination
	 * @throws IOException If retained evidence cannot be read
	 */
	@ParameterizedTest
	@EnumSource(Indexing.class)
	void conflictingRenamePreservesBothDetails( Indexing mode, @TempDir Path dir )
			throws IOException {
		Flow first = new ObservedFlow( "collision", null );
		Flow second = new ObservedFlow( "collision", null );
		Writer writer = new Writer( "model", "test", dir, mode );
		AtomicReference<String> firstName = new AtomicReference<>();
		AtomicReference<String> secondName = new AtomicReference<>();
		writer.with( first, detail -> {
			detail.tags.add( "first" );
			detail.motivation = "first evidence";
			firstName.set( Writer.detailFilename( detail ) );
		} );
		writer.with( second, detail -> {
			detail.tags.add( "second" );
			detail.motivation = "second evidence";
			secondName.set( Writer.detailFilename( detail ) );
		} );
		Path firstPath = dir.resolve( "detail/" + firstName.get() + ".html" );
		Path secondPath = dir.resolve( "detail/" + secondName.get() + ".html" );
		byte[] firstEvidence = Files.readAllBytes( firstPath );
		byte[] secondEvidence = Files.readAllBytes( secondPath );
		IllegalStateException failure = assertThrows( IllegalStateException.class,
				() -> writer.with( first, detail -> {
					detail.tags.remove( "first" );
					detail.tags.add( "second" );
				} ) );
		assertTrue( failure.getMessage().contains( "already owned by another flow" ) );
		assertSame( failure, assertThrows( IllegalStateException.class,
				() -> writer.with( first, detail -> {
					detail.tags.remove( "second" );
					detail.tags.add( "elsewhere" );
				} ) ).getCause() );
		assertSame( failure, assertThrows( IllegalStateException.class, writer::close ).getCause() );
		assertArrayEquals( firstEvidence, Files.readAllBytes( firstPath ) );
		assertArrayEquals( secondEvidence, Files.readAllBytes( secondPath ) );
		if( mode == Indexing.FINAL_ONLY ) {
			assertNull( new Reader( dir ).read() );
		}
	}

	/**
	 * A first decorator may move an otherwise colliding initial identity to a free
	 * path; it must not delete the detail already owned under that initial name.
	 *
	 * @param mode Index publication policy
	 * @param dir  Isolated report destination
	 */
	@ParameterizedTest
	@EnumSource(Indexing.class)
	void initialDecorationDoesNotDeleteAnotherFlow( Indexing mode, @TempDir Path dir ) {
		Flow first = new ObservedFlow( "initial identity", null );
		Flow second = new ObservedFlow( "initial identity", null );
		try( Writer writer = new Writer( "model", "test", dir, mode ) ) {
			writer.with( first, detail -> detail.motivation = "first evidence" );
			writer.with( second, detail -> {
				detail.tags.add( "distinct" );
				detail.motivation = "second evidence";
			} );
		}
		Reader reader = new Reader( dir );
		assertEquals( 2, reader.read().entries.size() );
		assertEquals( java.util.Set.of( "first evidence", "second evidence" ),
				reader.read().entries.stream().map( reader::detail )
						.map( detail -> detail.motivation ).collect( Collectors.toSet() ) );
	}

	/**
	 * Shared ancestry outside membership is captured once, not on every insertion
	 * or by rereading mutable model state at finalization.
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void sharedExternalAncestry( @TempDir Path dir ) {
		List<ObservedFlow> ancestry = new ArrayList<>();
		for( int i = 0; i < 100; i++ ) {
			ancestry.add( new ObservedFlow( "ancestor " + i, i == 0 ? null : ancestry.get( i - 1 ) ) );
		}
		try( Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY ) ) {
			for( int i = 0; i < 20; i++ ) {
				writer.with( new ObservedFlow( "leaf " + i, ancestry.get( 99 ) ) );
			}
			writer.with( ancestry.get( 0 ) );
			assertEquals( 1, ancestry.get( 50 ).basisReads );
			// Changing the external model later must not change captured ancestry.
			ancestry.get( 50 ).basis = null;
		}
		Reader reader = new Reader( dir );
		Map<String, Entry> entries = entries( reader );
		String root = entries.get( "ancestor 0" ).detail;
		for( int i = 0; i < 20; i++ ) {
			assertEquals( root, reader.detail( entries.get( "leaf " + i ) ).basis );
		}
		assertNull( reader.detail( entries.get( "ancestor 0" ) ).basis );
		assertEquals( 1, ancestry.get( 50 ).basisReads );
	}

	/**
	 * Cyclic ancestry is diagnosed before callback execution rather than looping.
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void externalBasisCycle( @TempDir Path dir ) {
		ObservedFlow cycle = new ObservedFlow( "cycle", null );
		cycle.basis = cycle;
		Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY );
		IllegalStateException failure = assertThrows( IllegalStateException.class,
				() -> writer.with( new ObservedFlow( "leaf", cycle ), detail -> {
					throw new AssertionError( "No callback on cyclic ancestry" );
				} ) );
		assertTrue( failure.getMessage().contains( "Cyclic report basis ancestry" ) );
		assertThrows( IllegalStateException.class, writer::close );
		assertNull( new Reader( dir ).read() );
	}

	/**
	 * A producer's repeated rename is reflected in its consumers' final links.
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void renamedDependencies( @TempDir Path dir ) {
		AtomicInteger callbacks = new AtomicInteger();
		try( Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY ) ) {
			writer.with( Mdl.DEPENDENT, detail -> callbacks.incrementAndGet() );
			writer.with( Mdl.DEPENDENCY, detail -> detail.tags.add( "first" ) );
			writer.with( Mdl.DEPENDENCY, detail -> {
				detail.tags.remove( "first" );
				detail.tags.add( "last" );
			} );
		}
		Reader reader = new Reader( dir );
		Map<String, Entry> entries = entries( reader );
		FlowData dependent = reader.detail( entries.get( "dependent" ) );
		assertEquals( java.util.Set.of( entries.get( "dependency" ).detail ),
				dependent.dependencies.keySet() );
		assertEquals( "dependency", dependent.dependencies.values().iterator().next().description );
		assertEquals( 1, callbacks.get() );
	}

	/**
	 * A late basis and its renamed path are corrected once from frozen evidence.
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void frozenCorrection( @TempDir Path dir ) {
		AtomicReference<FlowData> retained = new AtomicReference<>();
		AtomicInteger callbacks = new AtomicInteger();
		Map<String, String> mutable = new HashMap<>( Map.of( "value", "captured" ) );
		String payload = "large captured payload ".repeat( 50000 );
		try( Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY ) ) {
			writer.with( Mdl.CHILD, detail -> {
				callbacks.incrementAndGet();
				detail.motivation = "captured motivation";
				detail.context.put( "mutable", mutable );
				detail.context.put( "large", payload );
				retained.set( detail );
			} );
			mutable.put( "value", "late mutation" );
			retained.get().motivation = "late motivation";
			retained.get().tags.add( "LATE" );
			writer.with( Mdl.BASIS, detail -> detail.tags.add( "renamed" ) );
		}
		Reader reader = new Reader( dir );
		Map<String, Entry> entries = entries( reader );
		FlowData child = reader.detail( entries.get( "child" ) );
		assertEquals( entries.get( "basis" ).detail, child.basis );
		assertEquals( "captured motivation", child.motivation );
		assertEquals( Map.of( "value", "captured" ), child.context.get( "mutable" ) );
		assertEquals( payload, child.context.get( "large" ) );
		assertFalse( child.tags.contains( "LATE" ) );
		assertEquals( 1, callbacks.get() );
		entries.values().forEach( entry -> assertNotNull( reader.detail( entry ) ) );
	}

	private static Map<String, Entry> entries( Reader reader ) {
		return reader.read().entries.stream().collect( Collectors.toMap( e -> e.description, e -> e ) );
	}

	/**
	 * A model implementation whose ancestry access can be measured independently.
	 */
	private static class ObservedFlow implements Flow {
		private final Flow contents;
		private Flow basis;
		private int basisReads;

		ObservedFlow( String name, Flow basis ) {
			contents = Deriver.build( Mdl.BASIS, flow -> flow.meta( meta -> meta.description( name ) ) );
			this.basis = basis;
		}

		@Override
		public Metadata meta() {
			return contents.meta();
		}

		@Override
		public Flow basis() {
			basisReads++;
			return basis;
		}

		@Override
		public Interaction root() {
			return contents.root();
		}

		@Override
		public Stream<Actor> implicit() {
			return contents.implicit();
		}

		@Override
		public Stream<Dependency> dependencies() {
			return contents.dependencies();
		}

		@Override
		public Stream<Context> context() {
			return contents.context();
		}

		@Override
		public Stream<Residue> residue() {
			return contents.residue();
		}
	}
}
