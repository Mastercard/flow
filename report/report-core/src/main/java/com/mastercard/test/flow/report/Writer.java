package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
 */
public class Writer {

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
	private final Map<Flow, IndexedFlowData> data = new LinkedHashMap<>();
	private final JsApp app;

	private final Map<Flow, List<Flow>> missingBases = new HashMap<>();

	/**
	 * @param modelTitle A human-readable title for the model that supplied the test
	 *                   data
	 * @param testTitle  A human-readable title for the test that exercised the data
	 * @param root       Where to write the report to
	 */
	public Writer( String modelTitle, String testTitle, Path root ) {
		this.modelTitle = modelTitle;
		this.testTitle = testTitle;
		this.root = root;
		// delete whatever might be there already
		QuietFiles.recursiveDelete( root );
		// write static content
		app = new JsApp( "/com/mastercard/test/flow/report", root.resolve( "res" ) );
	}

	/**
	 * Adds or updates a {@link Flow} in the report
	 *
	 * @param flow  The {@link Flow}
	 * @param extra Extra data, above and beyond what the flow holds
	 * @return <code>this</code>
	 */
	@SafeVarargs
	public final Writer with( Flow flow, Consumer<FlowData>... extra ) {
		IndexedFlowData idf = data.computeIfAbsent( flow,
				f -> new IndexedFlowData( flow, data.keySet(), missingBases ) );
		String oldname = idf.indexEntry().detail;
		idf.update( extra );

		if( !idf.indexEntry().detail.equals( oldname ) ) {
			QuietFiles.recursiveDelete( root.resolve( "detail/" + oldname + ".html" ) );
		}

		// write the new detail
		app.write( idf.detail, root
				.resolve( DETAIL_DIR_NAME )
				.resolve( idf.indexEntry().detail + ".html" ) );

		// refresh the index
		app.write( new Index(
				new Meta( modelTitle, testTitle,
						System.currentTimeMillis() ),
				data.values().stream()
						.map( IndexedFlowData::indexEntry )
						.collect( toList() ) ),
				root.resolve( INDEX_FILE_NAME ) );

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
					app.write( toUpdate.detail, root
							.resolve( DETAIL_DIR_NAME )
							.resolve( toUpdate.indexEntry().detail + ".html" ) );
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

	/**
	 * Gets the directory where the report is being written
	 *
	 * @return The path to the report directory
	 */
	public Path path() {
		return root;
	}

	/**
	 * Attempts to open a browser to view the report. Failure is silent.
	 */
	public void browse() {
		if( Desktop.isDesktopSupported()
				&& Desktop.getDesktop().isSupported( Action.BROWSE ) ) {
			try {
				Desktop.getDesktop().browse( root.resolve( INDEX_FILE_NAME ).toUri() );
			}
			catch( @SuppressWarnings("unused") IOException e ) {
				// oh well
			}
		}
	}

	/**
	 * @return A map from {@link Flow}s that are missing their ideal bases to list
	 *         of those bases in preference order
	 */
	public Map<Flow, List<Flow>> missingBases() {
		return missingBases;
	}

	private static class IndexedFlowData {
		private Entry indexEntry;
		FlowData detail;

		public IndexedFlowData( Flow flow,
				Set<Flow> flowsInReport,
				Map<Flow, List<Flow>> missingBases ) {

			// walk up the basis chain until we find one that exists in the report
			Flow closesBasis = flow.basis();
			List<Flow> desiredBases = new ArrayList<>();
			while( closesBasis != null
					&& !flowsInReport.contains( closesBasis ) ) {
				desiredBases.add( closesBasis );
				closesBasis = closesBasis.basis();
			}

			if( !desiredBases.isEmpty() ) {
				// keep track of the missing ones. If those get added to the rpeort later we'll
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
							.collect( toMap(
									Writer::detailFilename,
									v -> new DependencyData(
											v.meta().description(),
											v.meta().tags() ),
									( a, b ) -> b ) ),
					new InteractionData( flow.root() ),
					flow.context()
							.collect( toMap( Context::name, v -> v ) ),
					flow.residue()
							.map( r -> new ResidueData( r.name(), r, null, null ) )
							.collect( toList() ),
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
