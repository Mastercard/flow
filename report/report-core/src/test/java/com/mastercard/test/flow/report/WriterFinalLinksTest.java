package com.mastercard.test.flow.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * Final-only reports resolve basis and dependency links on close, from what was
 * written rather than from live model data.
 */
@SuppressWarnings("static-method")
class WriterFinalLinksTest {

	/**
	 * A descendant links to its nearest ancestor present in the report, through
	 * ancestors that are absent. Ancestry is read once, at the descendant's first
	 * update.
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void absentAncestors( @TempDir Path dir ) {
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
	 * A dependency's renamed detail path is reflected in its dependents' links
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void renamedDependencies( @TempDir Path dir ) {
		try( Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY ) ) {
			writer.with( Mdl.DEPENDENT );
			writer.with( Mdl.DEPENDENCY, detail -> detail.tags.add( "first" ) );
			writer.with( Mdl.DEPENDENCY, detail -> {
				detail.tags.remove( "first" );
				detail.tags.add( "last" );
			} );
		}
		Reader reader = new Reader( dir );
		Map<String, Entry> entries = entries( reader );
		FlowData dependent = reader.detail( entries.get( "dependent" ) );
		assertEquals( Set.of( entries.get( "dependency" ).detail ),
				dependent.dependencies.keySet() );
		assertEquals( "dependency", dependent.dependencies.values().iterator().next().description );
	}

	/**
	 * A basis that arrives after its descendant, under a renamed path, is linked on
	 * close from the detail as written; later changes to the callback's data do not
	 * leak in
	 *
	 * @param dir Isolated report destination
	 */
	@Test
	void presentAncestor( @TempDir Path dir ) {
		AtomicReference<FlowData> retained = new AtomicReference<>();
		Map<String, String> mutable = new HashMap<>( Map.of( "value", "captured" ) );
		try( Writer writer = new Writer( "model", "test", dir, Indexing.FINAL_ONLY ) ) {
			writer.with( Mdl.CHILD, detail -> {
				detail.motivation = "captured motivation";
				detail.context.put( "mutable", mutable );
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
		assertFalse( child.tags.contains( "LATE" ) );
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
