package com.mastercard.test.flow.assrt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.order.Graph;
import com.mastercard.test.flow.util.Tags;

/**
 * Calculates an order in which {@link Flow}s should be processed in such that:
 * <ul>
 * <li>Dependencies are satisfied</li>
 * <li>Duplicate failures are minimised</li>
 * <li>State-change expense is minimised</li>
 * <li>Chain restrictions are honoured</li>
 * </ul>
 */
public class Order {

	private final Collection<Flow> flows = new ArrayList<>();
	private final Collection<Applicator<?>> applicators;

	/**
	 * Flows that bear the same tag value that starts with this prefix will be
	 * scheduled as a unit, {@link Flow}s that do not bear the same tag will not be
	 * interleaved into that unit
	 */
	public static final String CHAIN_TAG_PREFIX = "chain:";

	/**
	 * @param flows       The {@link Flow}s that we want to process
	 * @param applicators Applicators that are relevant for the execution
	 */
	public Order( Stream<Flow> flows, Collection<Applicator<?>> applicators ) {
		flows.forEach( this.flows::add );
		this.applicators = applicators;
	}

	/**
	 * Performs scheduling
	 *
	 * @return A processing schedule for the supplied {@link Flow}s
	 */
	public Stream<Flow> order() {
		// A map from chain name to chain members
		Map<String, List<Flow>> chains = new HashMap<>();
		// A map from chain member to chain name
		Map<Flow, String> chainNames = new HashMap<>();

		// This defines the ideal order of flows that minimises expensive context
		// switches
		Comparator<Flow> contextOrder = new ContextOrder( flows, applicators );

		// We're going to divert from that ideal order to:
		// * process chains as a unit
		// * ensure that dependencies are honoured
		// * process basis flows before their children

		// Build chains. flows that are not actually in a chain are implicitly in a
		// chain all on their lonesomes
		flows.forEach( f -> {
			String chain = Tags.suffix( f.meta().tags(), CHAIN_TAG_PREFIX )
					.orElse( f.meta().id() );
			chains.computeIfAbsent( chain, c -> new ArrayList<>() ).add( f );
			chainNames.put( f, chain );
		} );

		// Correct the internal order of each chain
		for( Map.Entry<String, List<Flow>> chain : chains.entrySet() ) {
			if( chain.getValue().size() > 1 ) {
				chains.put( chain.getKey(),
						order( chain.getValue().stream(),
								flw -> flw.dependencies().map( d -> d.source().flow() ),
								flw -> Stream.of( flw.basis() ).filter( Objects::nonNull ),
								contextOrder ) );
			}
		}

		// Order the list of chains
		List<List<Flow>> metaChain = order( chains.values().stream(),
				chain -> chain.stream()
						.flatMap( Flow::dependencies )
						.map( dep -> dep.source().flow() )
						.filter( Objects::nonNull )
						.map( chainNames::get )
						.map( chains::get ),
				chain -> chain.stream()
						.map( Flow::basis )
						.filter( Objects::nonNull )
						.map( chainNames::get )
						.map( chains::get ),
				Comparator.comparing( chain -> chain.get( 0 ), contextOrder ) );

		// Flatten the list of chains into a list of flows
		return metaChain
				.stream()
				.flatMap( List::stream );
	}

	/**
	 * Order the supplied items according to the supplied constraints
	 *
	 * @param <T>           The item type
	 * @param items         The items
	 * @param prerequisites The strongest constraint on order
	 * @param bases         A weaker constraint on order
	 * @param preference    The weakest constraint on order
	 * @return An ordered list of the items
	 */
	private static <T> List<T> order( Stream<T> items,
			Function<T, Stream<T>> prerequisites,
			Function<T, Stream<T>> bases,
			Comparator<T> preference ) {
		Graph<T> graph = new Graph<>( preference );
		items.forEach( graph::with );
		// dependencies have max weight - they must be honoured
		graph.values().forEach(
				snk -> prerequisites.apply( snk )
						.forEach( src -> graph.edge( Integer.MAX_VALUE, snk, src ) ) );
		// basis links have weight 1 - these will be deleted first to resolve cycles
		graph.values().forEach(
				flw -> bases.apply( flw )
						.forEach( bss -> graph.edge( 1, flw, bss ) ) );
		return graph.order();
	}

}
