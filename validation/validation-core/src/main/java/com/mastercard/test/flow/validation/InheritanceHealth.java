package com.mastercard.test.flow.validation;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.difflib.DiffUtils;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.graph.CachingDiffDistance;

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
 * If we consider flows to be vertices of a graph and the distance between
 * vertices to be proportional to the differences between two flows, then we can
 * compute the optimal inheritance structure by finding the
 * <a href="https://en.wikipedia.org/wiki/Minimum_spanning_tree">minimum
 * spanning tree</a> of the graph.
 * </p>
 * <p>
 * This class provides a mechanism by which the cost of the actual inheritance
 * structure can be compared to the cost of the optimal inheritance structure.
 * If the charts produced are committed to version control and checked by a test
 * then the quality of the basis choice of newly-added {@link Flow}s can be
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
	private final BiConsumer<String, String> assertion;

	public InheritanceHealth( int min, int max, BiConsumer<String, String> assertion ) {
		this.min = min;
		this.max = max;
		this.assertion = assertion;
	}

	public void expect( Model model, String... expected ) {
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
