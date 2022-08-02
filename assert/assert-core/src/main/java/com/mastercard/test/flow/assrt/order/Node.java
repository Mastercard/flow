package com.mastercard.test.flow.assrt.order;

import static java.util.stream.Collectors.joining;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A node in a {@link Graph}
 *
 * @param <T> The node value type
 */
class Node<T> {

	private enum State {
		/**
		 * Node has not been visited yet
		 */
		UNKNOWN,
		/**
		 * Node is being explored
		 */
		EXPLORING,
		/**
		 * Node and all descendants are not part of a cycle
		 */
		ACYCLIC
	}

	private final T value;
	private final Set<Edge<T>> edges = new HashSet<>();
	private Node.State state = State.UNKNOWN;
	private Edge<T> lastExplored;

	/**
	 * @param value the node value
	 */
	Node( T value ) {
		this.value = value;
	}

	/**
	 * @return The node value
	 */
	public T value() {
		return value;
	}

	/**
	 * Adds an edge to another node
	 *
	 * @param target The other node in the edge
	 * @param weight The edge weight
	 */
	void edgeTo( Node<T> target, int weight ) {
		edges.add( new Edge<>( this, target, weight ) );

	}

	/**
	 * Removes an {@link Edge}
	 *
	 * @param e The {@link Edge}
	 */
	void remove( Edge<T> e ) {
		edges.remove( e );
	}

	/**
	 * Explores the graphs starting from this node and removes any cycles found
	 */
	void removeCycles() {
		if( state == State.ACYCLIC ) {
			// this node has already been explored and found to be acyclic
		}
		else if( state == State.UNKNOWN ) {
			// recurse!
			state = State.EXPLORING;
			Set<Edge<T>> iterate = new HashSet<>( edges );
			for( Edge<T> e : iterate ) {
				if( edges.contains( e ) ) {
					lastExplored = e;
					e.to().removeCycles();
				}
			}
			lastExplored = null;
			state = State.ACYCLIC;
		}
		else if( state == State.EXPLORING ) {
			// we've been here before!

			// Trace through the lastExplored edges till we're back here again to find the
			// minimum weight edge
			Edge<T> minimum = lastExplored;
			Node<T> n = lastExplored.to();
			while( n != this ) {
				if( minimum.weight() > n.lastExplored.weight() ) {
					minimum = n.lastExplored;
				}
				n = n.lastExplored.to();
			}

			minimum.delete();
		}
	}

	/**
	 * Adds the node's prerequisites (the nodes that it links to), then itself to
	 * the list
	 *
	 * @param order      The list to build
	 * @param values     The set of nodes not yet in the last
	 * @param preference The preferred order of node values
	 */
	void add( List<T> order, Set<T> values, Comparator<T> preference ) {
		if( values.remove( value ) ) {
			edges.stream()
					.sorted( Comparator.comparing( e -> e.to().value(), preference ) )
					.forEach( e -> e.to().add( order, values, preference ) );
			order.add( value() );
		}
	}

	@Override
	public String toString() {
		return value + edges.stream()
				.map( e -> "\n\t" + e )
				.collect( joining() );
	}
}
