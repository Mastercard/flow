package com.mastercard.test.flow.validation;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.difflib.DiffUtils;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.graph.CachingDiffDistance;
import com.mastercard.test.flow.validation.graph.DAG;
import com.mastercard.test.flow.validation.graph.DiffGraph;

/**
 * <p>
 * Provides a mechanism by which the inheritance structure of the {@link Flow}s
 * in a system {@link Model} can be monitored.
 * </p>
 * <p>
 * The data in a system model is compressed via inheritance - most {@link Flow}
 * instances are a reference to another (the basis) and a list of changes that
 * distinguish the new flow from the basis. This allows us to avoid having to
 * maintain multiple copies of the data that is common to both flows. It's
 * obviously a good idea to minimise the size of that list of updates, which
 * means it's important to choose the inheritance basis carefully. Using an
 * inappropriate choice of basis leads to unnecessary data updates in the flow
 * definition, which are technical debt.
 * </p>
 * <p>
 * If we consider flows to be vertices of a complete graph and the edge weight
 * to be proportional to the differences between two flows, then we can compute
 * the optimal inheritance structure by finding the
 * <a href="https://en.wikipedia.org/wiki/Minimum_spanning_tree">minimum
 * spanning tree</a> of the graph.
 * </p>
 * <p>
 * This class provides a mechanism by which the cost of the actual inheritance
 * structure can be compared to the cost of the optimal inheritance structure.
 * If the charts produced are committed to version control and checked by a test
 * then the quality of the basis choices for newly-added {@link Flow}s can be
 * monitored.
 * </p>
 */
public class InheritanceHealth {

	private final CachingDiffDistance<Flow> diffDistance = new CachingDiffDistance<>(
			InheritanceHealth::flatten,
			InheritanceHealth::diffDistance );

	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	private final int min;
	private final int max;
	private final int heightLimit;
	private final BiConsumer<String, String> assertion;

	/**
	 * The phases of inheritance health checking
	 */
	public enum Phase {
		/**
		 * Wherein the flows are constructed
		 */
		BUILD,
		/**
		 * Wherein the actual inheritance cost is calculated
		 */
		ACTUAL_COST,
		/**
		 * Wherein the optimal inheritance structure is found
		 */
		OPTIMISE,
		/**
		 * Wherein the optimal inheritance cost is calculated
		 */
		OPTIMAL_COST
	}

	private final Map<Phase, BiConsumer<Flow, Float>> progress = new EnumMap<>( Phase.class );

	/**
	 * @param min         {@link Histograph} plot minimum
	 * @param max         {@link Histograph} plot maximum
	 * @param heightLimit Maximum {@link Histograph} plot height
	 * @param assertion   How to compare expected and actual metrics
	 */
	public InheritanceHealth( int min, int max, int heightLimit,
			BiConsumer<String, String> assertion ) {
		this.min = min;
		this.max = max;
		this.heightLimit = heightLimit;
		this.assertion = assertion;
	}

	/**
	 * Adds a progress listener
	 *
	 * @param phase    The phase of interest
	 * @param listener Will be appraised of progress. Arguments are the current flow
	 *                 under consideration and the fraction (range 0-1, or -1 if
	 *                 undetermined) of the complete model that has been processed
	 * @return <code>this</code>
	 */
	public InheritanceHealth progress( Phase phase, BiConsumer<Flow, Float> listener ) {
		progress.put( phase, listener );
		return this;
	}

	/**
	 * Computes inheritance health and compares against expected value
	 *
	 * @param model    The model to measure
	 * @param expected The expected health metrics
	 */
	public void expect( Model model, String... expected ) {
		int total = (int) model.flows()
				// we don't know how many flows there are, so pass -1 for the progress fraction
				.map( f -> progress( Phase.BUILD, f, -1, 1 ) )
				.count();

		StructureCost actual = actualCost( model, total );
		StructureCost optimal = optimalCost( model, total );

		Histograph hstg = new Histograph( min, max, Math.min( max - min, heightLimit ) );
		Deque<String> actualLines = Stream.of( actual.toString( "Actual", hstg ).split( "\n" ) )
				.collect( toCollection( ArrayDeque::new ) );
		Deque<String> optimalLines = Stream.of( optimal.toString( "Optimal", hstg ).split( "\n" ) )
				.collect( toCollection( ArrayDeque::new ) );

		List<String> stitched = new ArrayList<>();
		while( !actualLines.isEmpty() && !optimalLines.isEmpty() ) {
			stitched.add( actualLines.poll() + " | " + optimalLines.poll() );
		}

		assertion.accept(
				MessageHash.copypasta( Stream.of( expected ) ),
				MessageHash.copypasta( stitched.stream() ) );
	}

	private StructureCost actualCost( Model model, int total ) {
		AtomicInteger count = new AtomicInteger( 0 );
		AtomicInteger rootWeight = new AtomicInteger( 0 );
		TreeMap<Integer, Integer> edgeCosts = new TreeMap<>();

		model.flows()
				.forEach( flow -> {
					if( flow.basis() == null ) {
						rootWeight.addAndGet( flatten( flow ).split( "\n" ).length );
					}
					else {
						edgeCosts.compute(
								diffDistance.apply( flow.basis(), flow ),
								( k, v ) -> v == null ? 1 : v + 1 );
					}
					progress( Phase.ACTUAL_COST, flow, count.incrementAndGet(), total );
				} );

		return new StructureCost( rootWeight.get(), edgeCosts );
	}

	private StructureCost optimalCost( Model model, int total ) {
		DiffGraph<Flow> dg = new DiffGraph<>( diffDistance );
		model.flows().forEach( dg::add );

		// The root of the MST only affects the final root weight - edge cost will be
		// the same regardless of the root flow. Let's assume we've made a reasonable
		// choice in the actual hierarchy
		Flow root = model.flows()
				.filter( f -> f.basis() == null )
				.findFirst()
				.orElseThrow( () -> new IllegalArgumentException( "No flows?" ) );

		AtomicInteger count = new AtomicInteger( 0 );
		DAG<Flow> optimal = dg.withMSTListener(
				( parent, child ) -> progress( Phase.OPTIMISE, child, count.incrementAndGet(), total ) )
				.minimumSpanningTree( root );

		count.set( 0 );
		AtomicInteger rootWeight = new AtomicInteger( 0 );
		TreeMap<Integer, Integer> edgeCosts = new TreeMap<>();
		optimal.traverse( dag -> {
			if( dag.parent() == null ) {
				rootWeight.addAndGet( flatten( dag.value() ).split( "\n" ).length );
			}
			else {
				edgeCosts.compute(
						diffDistance.apply( dag.parent().value(), dag.value() ),
						( k, v ) -> v == null ? 1 : v + 1 );
			}
			progress( Phase.OPTIMAL_COST, dag.value(), count.incrementAndGet(), total );
		} );

		return new StructureCost( rootWeight.get(), edgeCosts );
	}

	private Flow progress( Phase phase, Flow flow, int current, int total ) {
		Optional.ofNullable( progress.get( phase ) )
				.ifPresent( l -> l.accept( flow, (float) current / total ) );
		return flow;
	}

	private static class StructureCost {
		final int rootWeight;
		final TreeMap<Integer, Integer> edgeCosts;

		protected StructureCost( int rootWeight, TreeMap<Integer, Integer> edgeCosts ) {
			this.rootWeight = rootWeight;
			this.edgeCosts = edgeCosts;
		}

		String toString( String name, Histograph hstg ) {
			if( edgeCosts.firstKey() < hstg.getMinimum() ) {
				throw new IllegalArgumentException(
						String.format( ""
								+ "Minimum edge weight %s lower than plot range minimum %s\n."
								+ "Decrease the plot range minimum to at most %s and try again.",
								edgeCosts.firstKey(), hstg.getMinimum(), edgeCosts.firstKey() ) );
			}
			if( edgeCosts.lastKey() > hstg.getMaximum() ) {
				throw new IllegalArgumentException(
						String.format( ""
								+ "Maximum edge weight %s higher than plot range maximum %s\n."
								+ "Increase the plot range maximum to at least %s and try again.",
								edgeCosts.lastKey(), hstg.getMaximum(), edgeCosts.lastKey() ) );
			}

			int edgeTotal = edgeCosts.entrySet().stream()
					.mapToInt( e -> e.getKey() * e.getValue() )
					.sum();
			return String.format( ""
					+ "%-17s\n"
					+ "roots %11s\n"
					+ "edges %11s\n"
					+ "total %11s\n"
					+ "%s\n",
					name,
					rootWeight,
					edgeTotal,
					edgeTotal + rootWeight,
					hstg.graph( edgeCosts ) );
		}
	}

	/**
	 * Dumps a flow's data to a string such that it can be usefully compared
	 *
	 * @param flow A flow
	 * @return A string representation of the flow
	 */
	public static String flatten( Flow flow ) {
		List<String> lines = new ArrayList<>();

		lines.add( "Identity:" );
		lines.add( "  " + flow.meta().description() );
		flow.meta().tags().forEach( t -> lines.add( "  " + t ) );

		lines.add( "Motivation:" );
		lines.add( "  " + flow.meta().motivation() );

		lines.add( "Context:" );
		flow.context().forEach( ctx -> {
			lines.add( "  " + ctx.name() + ":" );
			try {
				Stream.of( JSON.writeValueAsString( ctx ).replace( "\r", "" ).split( "\n" ) )
						.map( l -> "  " + l )
						.forEach( lines::add );
			}
			catch( IOException ioe ) {
				throw new UncheckedIOException( "Failed to serialise " + ctx, ioe );
			}
		} );
		lines.add( "Residue:" );
		flow.residue().forEach( rsd -> {
			lines.add( "  " + rsd.name() + ":" );
			try {
				Stream.of( JSON.writeValueAsString( rsd ).replace( "\r", "" ).split( "\n" ) )
						.map( l -> "  " + l )
						.forEach( lines::add );
			}
			catch( IOException ioe ) {
				throw new UncheckedIOException( "Failed to serialise " + rsd, ioe );
			}
		} );

		lines.add( "Interactions:" );
		flatten( flow.root(), lines, "  " );

		return lines.stream().collect( joining( "\n" ) );
	}

	private static void flatten( Interaction ntr, List<String> lines, String indent ) {
		lines.add( String.format( "%s┌REQUEST %s 🠖 %s %s", indent, ntr.requester(), ntr.responder(),
				ntr.tags() ) );
		Stream.of( ntr.request().assertable().split( "\n" ) )
				.map( l -> indent + "│" + l )
				.forEach( lines::add );

		List<Interaction> children = ntr.children().collect( toList() );
		if( children.isEmpty() ) {
			lines.add( indent + "└" );
		}
		else {
			lines.add( indent + "╘ Provokes:" );
			children.forEach( c -> flatten( c, lines, indent + "  " ) );
		}

		lines.add( String.format( "%s┌RESPONSE %s 🠔 %s %s", indent, ntr.requester(), ntr.responder(),
				ntr.tags() ) );
		Stream.of( ntr.response().assertable().split( "\n" ) )
				.map( l -> indent + "│" + l )
				.forEach( lines::add );
		lines.add( indent + "└" );
	}

	/**
	 * @param from A string
	 * @param to   Another string
	 * @return The number of lines of difference between the two
	 */
	public static int diffDistance( String from, String to ) {

		return DiffUtils.diff(
				Arrays.asList( from.split( "\n" ) ),
				Arrays.asList( to.split( "\n" ) ), false ).getDeltas()
				.stream()
				.mapToInt( delta -> {
					switch( delta.getType() ) {
						case DELETE:
							return delta.getSource().getLines().size();
						case INSERT:
							return delta.getTarget().getLines().size();
						case CHANGE:
							return Math.max(
									delta.getSource().getLines().size(),
									delta.getTarget().getLines().size() );
						default:
							return 0;
					}
				} )
				.sum();
	}

}
