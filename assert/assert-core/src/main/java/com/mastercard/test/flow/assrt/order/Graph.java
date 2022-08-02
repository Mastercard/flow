package com.mastercard.test.flow.assrt.order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A directed weighted graph of nodes
 *
 * @param <T> The node value type
 */
public class Graph<T> {

	private final Comparator<T> preference;
	private final Map<T, Node<T>> nodes = new HashMap<>();

	/**
	 * @param preference The lowest-priority constrain on node order
	 */
	public Graph( Comparator<T> preference ) {
		this.preference = preference;
	}

	/**
	 * Adds a {@link Node} in the graph
	 *
	 * @param value The {@link Node} value
	 * @return <code>this</code>
	 */
	public Graph<T> with( T value ) {
		nodes.put( value, new Node<>( value ) );
		return this;
	}

	/**
	 * Adds an edge to the graph if both values exist in the graph
	 *
	 * @param weight The weight of the edge
	 * @param from   The source node
	 * @param to     The destination node
	 * @return <code>this</code>
	 */
	public Graph<T> edge( int weight, T from, T to ) {
		Node<T> src = nodes.get( from );
		Node<T> dst = nodes.get( to );
		if( src != null && dst != null ) {
			src.edgeTo( dst, weight );
		}
		return this;
	}

	/**
	 * Removes cycles from the graph by removing the lowest-weight edge in each
	 * cycle
	 */
	private void removeCycles() {
		nodes.values().forEach( Node::removeCycles );

	}

	/**
	 * Produces a value ordering where linked-to nodes come before linked-from
	 * nodes. Cycles in the graph will be broken by deleting an arbitrary
	 * lowest-weight edge.
	 *
	 * @return The graph node values, in an order such that linked-<i>to</i> values
	 *         come before linked-<i>from</i> values.
	 */
	public List<T> order() {
		removeCycles();
		List<T> order = new ArrayList<>();
		Set<T> values = new TreeSet<>( preference );
		values.addAll( nodes.keySet() );
		while( !values.isEmpty() ) {
			T t = values.iterator().next();
			nodes.get( t ).add( order, values, preference );
		}
		return order;
	}

	/**
	 * Node value accessor
	 *
	 * @return the graph node values
	 */
	public Stream<T> values() {
		return nodes.keySet().stream();
	}

	@Override
	public String toString() {
		return nodes.values().stream()
				.map( Node::toString )
				.sorted()
				.collect( Collectors.joining( "\n" ) );
	}
}
