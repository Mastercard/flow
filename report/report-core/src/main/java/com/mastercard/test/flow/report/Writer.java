package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.report.data.DependencyData;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.InteractionData;
import com.mastercard.test.flow.report.data.Meta;
import com.mastercard.test.flow.report.data.ResidueData;
import com.mastercard.test.flow.util.Bytes;

/**
 * For writing a new report
 * <p>
 * Callers must use a single active writer per output namespace and publication
 * location. Competing writers, including overlapping parent/child destinations,
 * are unsupported and may delete, mix or misleadingly publish output. Updates
 * from multiple producers on the same writer are supported.
 * </p>
 */
public class Writer implements AutoCloseable {

	/**
	 * When the report's index becomes available.
	 */
	public enum Indexing {
		/** Preserve insertion order and publish an index on every update. */
		IMMEDIATE,
		/** Publish one index, ordered by stable detail identity, on close. */
		FINAL_ONLY
	}

	/**
	 * The file name under which the report index is saved
	 */
	public static final String INDEX_FILE_NAME = "index.html";
	/**
	 * The directory in which {@link Flow} detail data is stored
	 */
	public static final String DETAIL_DIR_NAME = "detail";
	/**
	 * Added to {@link Flow}s and {@link Interaction}s that pass assertions
	 */
	public static final String PASS_TAG = "PASS";
	/**
	 * Added to {@link Flow}s and {@link Interaction}s that fail assertions
	 */
	public static final String FAIL_TAG = "FAIL";
	/**
	 * Added to {@link Flow}s and {@link Interaction}s that are not exercised in the
	 * test
	 */
	public static final String SKIP_TAG = "SKIP";
	/**
	 * Added to {@link Flow}s that suffer some non-assertion error
	 */
	public static final String ERROR_TAG = "ERROR";

	/**
	 * The set of tag values used to record assertion outcome. We want a
	 * {@link Flow} to have a consistent detail path in all reports regardless of
	 * their outcome in a given test instance, so we have to disregard these result
	 * tags when computing the file name
	 */
	public static final Set<String> RESULT_TAGS = Collections.unmodifiableSet(
			Stream.of( PASS_TAG, FAIL_TAG, ERROR_TAG, SKIP_TAG ).collect( toSet() ) );

	private final String modelTitle;
	private final String testTitle;
	private final Path root;
	private final Path requestedRoot;
	private final Path latest;
	private final Map<Flow, IndexedFlowData> data = new LinkedHashMap<>();
	private final Map<String, IndexedFlowData> detailOwners = new HashMap<>();
	private final JsApp app;
	private final Indexing indexing;
	private final ReportFiles files;
	private State state = State.OPEN;
	private Throwable failure;
	private boolean updating;
	private Consumer<Path> publication;

	private enum State {
		OPEN, FINALIZING, CLOSED, FAILED
	}

	private final Map<Flow, List<Flow>> missingBases = new HashMap<>();
	private final Map<Flow, Flow> bases = new HashMap<>();

	/**
	 * @param modelTitle A human-readable title for the model that supplied the test
	 *                   data
	 * @param testTitle  A human-readable title for the test that exercised the data
	 * @param root       Where to write the report to
	 */
	public Writer( String modelTitle, String testTitle, Path root ) {
		this( modelTitle, testTitle, root, Indexing.IMMEDIATE );
	}

	/**
	 * @param modelTitle A human-readable title for the model
	 * @param testTitle  A human-readable title for the test
	 * @param root       Where to write the report
	 * @param indexing   When to publish the index; final-only callers must close
	 *                   the writer after submitting all updates
	 */
	public Writer( String modelTitle, String testTitle, Path root, Indexing indexing ) {
		this( modelTitle, testTitle, root, indexing, new ReportFiles() );
	}

	/**
	 * @param modelTitle Model title
	 * @param testTitle  Test title
	 * @param root       Report destination
	 * @param indexing   Index publication policy
	 * @param latest     Advertisement location, named latest; null uses a sibling
	 *                   of the canonical destination. Its parent is canonicalized,
	 *                   not the advertisement's target. No link is created
	 *                   automatically.
	 */
	public Writer( String modelTitle, String testTitle, Path root, Indexing indexing, Path latest ) {
		this( modelTitle, testTitle, root, indexing, new ReportFiles(), latest );
	}

	/**
	 * @param modelTitle Model title
	 * @param testTitle  Test title
	 * @param root       Report destination
	 * @param indexing   Index publication policy
	 * @param files      Payload filesystem operations
	 */
	Writer( String modelTitle, String testTitle, Path root, Indexing indexing, ReportFiles files ) {
		this( modelTitle, testTitle, root, indexing, files, null );
	}

	/**
	 * @param modelTitle Model title
	 * @param testTitle  Test title
	 * @param root       Report destination
	 * @param indexing   Index publication policy
	 * @param files      Payload filesystem operations
	 * @param latest     Advertisement location known before replacement; null uses
	 *                   a sibling
	 */
	Writer( String modelTitle, String testTitle, Path root, Indexing indexing, ReportFiles files,
			Path latest ) {
		this.modelTitle = modelTitle;
		this.testTitle = testTitle;
		requestedRoot = root;
		this.indexing = Objects.requireNonNull( indexing, "indexing" );
		this.files = Objects.requireNonNull( files, "files" );
		this.root = QuietFiles.wrap( () -> ReportFiles.canonical( root.toAbsolutePath() ) );
		this.latest = QuietFiles.wrap( () -> ReportFiles.latest( this.root, latest ) );
		files.withdrawLatest( root, this.root, this.latest );
		files.clear( this.root );
		app = new JsApp( "/com/mastercard/test/flow/report", this.root.resolve( "res" ), files );
	}

	/**
	 * Writes the duct index file
	 *
	 * @param dir The directory to write to
	 */
	public static void writeDuctIndex( Path dir ) {
		new JsApp( "/com/mastercard/test/flow/report", dir.resolve( "res" ) )
				.write(
						Collections.singletonMap( "type", "duct" ),
						dir.resolve( INDEX_FILE_NAME ) );
	}

	/**
	 * Adds or updates a {@link Flow} in the report
	 * <p>
	 * Updates, callbacks, detail IO and link/index publication are serialized on
	 * this writer. Callbacks execute synchronously on the calling thread. Callers
	 * remain responsible for the semantic order of updates to the same flow and
	 * must not wait for another thread to update this writer from a callback.
	 * </p>
	 *
	 * @param flow  The {@link Flow}
	 * @param extra Extra data, above and beyond what the flow holds
	 * @return <code>this</code>
	 */
	@SafeVarargs
	public final synchronized Writer with( Flow flow, Consumer<FlowData>... extra ) {
		requireOpen();
		updating = true;
		try {
			return update( flow, extra );
		}
		catch( RuntimeException | Error e ) {
			state = State.FAILED;
			failure = e;
			throw e;
		}
		finally {
			updating = false;
		}
	}

	private void requireOpen() {
		if( state == State.FAILED ) {
			// A fresh wrapper avoids self-suppression when try-with-resources closes
			// a writer whose update already threw the original exception.
			throw new IllegalStateException( "Writer previously failed: " + root, failure );
		}
		if( state != State.OPEN || updating ) {
			throw new IllegalStateException( "Writer is " + (updating ? "updating" : state) );
		}
	}

	private Writer update( Flow flow, Consumer<FlowData>[] extra ) {
		IndexedFlowData idf = data.computeIfAbsent( flow,
				f -> {
					if( indexing == Indexing.FINAL_ONLY ) {
						captureBases( flow );
					}
					return new IndexedFlowData( flow, data.keySet(), missingBases,
							indexing == Indexing.FINAL_ONLY );
				} );
		String oldname = idf.indexEntry().detail;
		idf.update( extra );
		String newname = idf.indexEntry().detail;
		IndexedFlowData owner = detailOwners.get( newname );
		if( owner != null && owner != idf ) {
			throw new IllegalStateException( "Report detail " + newname
					+ " is already owned by another flow" );
		}

		// A first decoration has not written its initial name. It must never
		// remove another flow's evidence merely because that name was shared.
		if( !newname.equals( oldname ) && detailOwners.remove( oldname, idf ) ) {
			QuietFiles.recursiveDelete( root.resolve( "detail/" + oldname + ".html" ) );
		}

		// write the new detail
		idf.writeTo( root, app );
		detailOwners.put( newname, idf );

		// refresh the index
		if( indexing == Indexing.IMMEDIATE ) {
			writeIndex( root.resolve( INDEX_FILE_NAME ) );
		}
		else {
			// Final membership, not arrival order, determines link correction.
			return this;
		}

		// refresh the details of those who were waiting for that flow as a better basis
		// candidate
		missingBases.forEach( ( unhappy, preferred ) -> {
			Iterator<Flow> pi = preferred.iterator();
			boolean found = false;
			while( pi.hasNext() ) {
				Flow betterBase = pi.next();
				if( found ) {
					pi.remove();
				}
				else if( betterBase == flow ) {
					found = true;
					pi.remove();
					IndexedFlowData toUpdate = data.get( unhappy );
					toUpdate.detail = toUpdate.detail.withBasis( detailFilename( flow ) );
					toUpdate.writeTo( root, app );
				}
			}
		} );

		// prune satisfied flows
		missingBases.entrySet().removeAll(
				missingBases.entrySet().stream()
						.filter( e -> e.getValue().isEmpty() )
						.collect( toSet() ) );

		return this;
	}

	private void captureBases( Flow flow ) {
		Set<Flow> visiting = new HashSet<>();
		Flow current = flow;
		while( current != null && !bases.containsKey( current ) ) {
			visiting.add( current );
			Flow basis = current.basis();
			bases.put( current, basis );
			current = basis;
		}
		if( current != null && visiting.contains( current ) ) {
			throw new IllegalStateException( "Cyclic report basis ancestry for " + flow.meta().id() );
		}
	}

	private Flow nearestPresent( Flow basis, Map<Flow, Flow> resolved ) {
		List<Flow> visited = new ArrayList<>();
		Flow current = basis;
		while( current != null && !data.containsKey( current ) && !resolved.containsKey( current ) ) {
			visited.add( current );
			current = bases.get( current );
		}
		Flow nearest = current == null || data.containsKey( current ) ? current
				: resolved.get( current );
		visited.forEach( absent -> resolved.put( absent, nearest ) );
		return nearest;
	}

	private void correctFinalLinks() {
		Map<Flow, Flow> resolved = new HashMap<>();
		data.forEach( ( flow, indexed ) -> {
			Flow nearest = nearestPresent( bases.get( flow ), resolved );
			String basis = nearest == null ? null : data.get( nearest ).indexEntry().detail;
			Map<String, String> renamed = new HashMap<>();
			indexed.dependencySources.forEach( ( path, source ) -> {
				IndexedFlowData present = data.get( source );
				if( present != null && indexed.serializedDependencies.contains( path )
						&& !path.equals( present.indexEntry().detail ) ) {
					renamed.put( path, present.indexEntry().detail );
				}
			} );
			if( !Objects.equals( indexed.serializedBasis, basis ) || !renamed.isEmpty() ) {
				Path path = root.resolve( DETAIL_DIR_NAME )
						.resolve( indexed.indexEntry().detail + ".html" );
				// The file is the last successful serialized snapshot. No second full
				// payload is retained and no live execution callback is read again.
				ObjectNode snapshot = Template.extract(
						new String( QuietFiles.readAllBytes( path ), UTF_8 ),
						ObjectNode.class );
				snapshot.put( "basis", basis );
				ObjectNode dependencies = (ObjectNode) snapshot.get( "dependencies" );
				Map<String, JsonNode> moved = new HashMap<>();
				renamed.forEach(
						( oldPath, newPath ) -> moved.put( newPath, dependencies.remove( oldPath ) ) );
				moved.forEach( dependencies::set );
				app.write( snapshot, path );
			}
		} );
	}

	private void writeIndex( Path destination ) {
		Stream<Entry> entries = data.values().stream().map( IndexedFlowData::indexEntry );
		if( indexing == Indexing.FINAL_ONLY ) {
			entries = entries.sorted( comparing( entry -> entry.detail ) );
		}
		app.write( new Index( new Meta( modelTitle, testTitle, System.currentTimeMillis() ),
				entries.collect( toList() ) ), destination );
	}

	/**
	 * Registers one synchronous publication action for successful finalization. It
	 * runs inside close and must finish its use before returning, not wait for
	 * another thread to use this writer. A thrown failure is latched and is not
	 * retried. Actions advertising latest must use the location supplied at
	 * construction (a sibling by default). They may replace an existing symlink,
	 * but preserve its target and ordinary files/directories at that location.
	 * Actions handle their own partial side effects.
	 *
	 * @param action Receives the canonical report destination
	 * @return This writer
	 */
	public synchronized Writer onClose( Consumer<Path> action ) {
		requireOpen();
		if( publication != null ) {
			throw new IllegalStateException( "Publication action already registered" );
		}
		publication = Objects.requireNonNull( action, "action" );
		return this;
	}

	/**
	 * Completes this report. Final-only indexes are closed in a same-directory
	 * temporary file before an atomic move, with no non-atomic fallback. A
	 * successful repeated close does nothing; further updates are rejected. After
	 * an update or publication fails, subsequent close/update calls throw an
	 * exception whose cause is the original failure, without retrying IO. Failed
	 * finalization does not invoke the publication action.
	 */
	@Override
	public synchronized void close() {
		if( state == State.CLOSED ) {
			return;
		}
		requireOpen();
		state = State.FINALIZING;
		try {
			if( indexing == Indexing.FINAL_ONLY ) {
				correctFinalLinks();
				publishIndex();
			}
			if( publication != null ) {
				QuietFiles.createDirectories( latest.getParent() );
				publication.accept( root );
			}
			state = State.CLOSED;
		}
		catch( RuntimeException | Error e ) {
			state = State.FAILED;
			failure = e;
			throw e;
		}
	}

	private void publishIndex() {
		Path temporary = null;
		try {
			Files.createDirectories( root.resolve( DETAIL_DIR_NAME ) );
			temporary = files.temporary( root );
			writeIndex( temporary );
			files.publish( temporary, root.resolve( INDEX_FILE_NAME ) );
		}
		catch( IOException e ) {
			UncheckedIOException problem = new UncheckedIOException(
					"Failed to publish final report " + root, e );
			removeTemporary( temporary, problem );
			throw problem;
		}
		catch( RuntimeException | Error e ) {
			removeTemporary( temporary, e );
			throw e;
		}
	}

	private static void removeTemporary( Path temporary, Throwable problem ) {
		if( temporary != null ) {
			try {
				Files.deleteIfExists( temporary );
			}
			catch( IOException | RuntimeException e ) {
				problem.addSuppressed( e );
			}
		}
	}

	/**
	 * Gets the directory where the report is being written
	 *
	 * @return The path to the report directory
	 */
	public Path path() {
		return requestedRoot;
	}

	/**
	 * Attempts to open a browser to view the report. Failure is silent.
	 */
	public void browse() {
		LocalBrowse.WITH_AWT.to( root.resolve( INDEX_FILE_NAME ).toUri() );
	}

	/**
	 * @return An immutable, detached snapshot from {@link Flow}s that are missing
	 *         their ideal bases to lists of those bases in preference order. Flow
	 *         references retain their existing identities.
	 */
	public synchronized Map<Flow, List<Flow>> missingBases() {
		Map<Flow, List<Flow>> snapshot = new HashMap<>();
		if( indexing == Indexing.IMMEDIATE ) {
			missingBases.forEach( ( flow, desired ) -> snapshot.put( flow, List.copyOf( desired ) ) );
		}
		else {
			// This optional diagnostic can enumerate missing paths; updates and
			// final publication do not perform this per-member ancestry scan.
			data.keySet().forEach( flow -> {
				List<Flow> desired = new ArrayList<>();
				Set<Flow> seen = new HashSet<>();
				Flow basis = bases.get( flow );
				while( basis != null && !data.containsKey( basis ) && seen.add( basis ) ) {
					desired.add( basis );
					basis = bases.get( basis );
				}
				if( !desired.isEmpty() ) {
					snapshot.put( flow, List.copyOf( desired ) );
				}
			} );
		}
		return Collections.unmodifiableMap( snapshot );
	}

	private static class IndexedFlowData {
		private Entry indexEntry;
		private String serializedBasis;
		private Set<String> serializedDependencies;
		private final Map<String, Flow> dependencySources = new HashMap<>();
		FlowData detail;

		public IndexedFlowData( Flow flow,
				Set<Flow> flowsInReport,
				Map<Flow, List<Flow>> missingBases, boolean finalOnly ) {

			// walk up the basis chain until we find one that exists in the report
			Flow closesBasis = flow.basis();
			List<Flow> desiredBases = new ArrayList<>();
			if( finalOnly && !flowsInReport.contains( closesBasis ) ) {
				closesBasis = null;
			}
			while( !finalOnly && closesBasis != null
					&& !flowsInReport.contains( closesBasis ) ) {
				desiredBases.add( closesBasis );
				closesBasis = closesBasis.basis();
			}

			if( !desiredBases.isEmpty() ) {
				// keep track of the missing ones. If those get added to the report later we'll
				// want to update this flow to point at them
				missingBases.put( flow, desiredBases );
			}

			detail = new FlowData(
					flow.meta().description(),
					new TreeSet<>( flow.meta().tags() ),
					flow.meta().motivation(),
					flow.meta().trace(),
					Optional.ofNullable( closesBasis )
							.map( Writer::detailFilename )
							.orElse( null ),
					flow.dependencies()
							.map( d -> d.source().flow() )
							.filter( d -> d != flow )
							.map( source -> Map.entry( detailFilename( source ), source ) )
							.peek( source -> dependencySources.put( source.getKey(), source.getValue() ) )
							.collect( toMap(
									Map.Entry::getKey,
									v -> new DependencyData(
											v.getValue().meta().description(),
											v.getValue().meta().tags() ),
									( a, b ) -> b ) ),
					new InteractionData( flow.root() ),
					flow.context()
							.collect( toMap( Context::name, v -> v ) ),
					flow.residue()
							.map( r -> new ResidueData( r.name(), r, null, null ) )
							.collect( toList() ),
					new TreeSet<>(),
					new ArrayList<>() );
			update();
		}

		@SafeVarargs
		public final void update( Consumer<FlowData>... extra ) {
			for( Consumer<FlowData> e : extra ) {
				e.accept( detail );
			}
			indexEntry = new Entry( detail.description, detail.tags,
					detailFilename( detail ) );
		}

		Entry indexEntry() {
			return indexEntry;
		}

		/**
		 * Writes the detail data
		 *
		 * @param root The report root directory
		 * @param app  The application
		 */
		void writeTo( Path root, JsApp app ) {
			app.write( detail, root
					.resolve( DETAIL_DIR_NAME )
					.resolve( indexEntry().detail + ".html" ) );
			serializedBasis = detail.basis;
			serializedDependencies = new HashSet<>( detail.dependencies.keySet() );
		}
	}

	/**
	 * Computes the filename for a flow identity
	 *
	 * @param flow The {@link Flow}
	 * @return The file name under which the flow's details should be saved
	 */
	public static String detailFilename( Flow flow ) {
		return detailFilename( flow.meta().description(), flow.meta().tags() );
	}

	/**
	 * Computes the filename for a flow identity
	 *
	 * @param flow The {@link Flow}
	 * @return The file name under which the flow's details should be saved
	 */
	public static String detailFilename( FlowData flow ) {
		return detailFilename( flow.description, flow.tags );
	}

	/**
	 * Computes the filename for a flow identity
	 *
	 * @param description {@link Metadata#description()}
	 * @param tags        {@link Metadata#tags()}
	 * @return The file name under which the flow's details should be saved
	 */
	private static String detailFilename( String description, Set<String> tags ) {
		try {
			Set<String> toHash = new TreeSet<>( tags );
			toHash.removeAll( RESULT_TAGS );

			MessageDigest md5 = MessageDigest.getInstance( "MD5" );
			return Bytes.toHex( md5.digest( (description + toHash).getBytes( UTF_8 ) ) );
		}
		catch( NoSuchAlgorithmException nsae ) {
			throw new IllegalStateException( "MD5 not found", nsae );
		}
	}
}
