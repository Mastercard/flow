/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.graph;

import static java.util.stream.Collectors.toCollection;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Represents a graph of items that can be compared against each other and the
 * differences quantified
 *
 * @param <S> The node value type
 */
public class DiffGraph<S> {

	private final Set<S> nodes = new HashSet<>();
	private final BiFunction<S, S, Integer> diff;

	private BiConsumer<S, S> mstListener = ( parent, child ) -> {
		// default no-op
	};

	private BiConsumer<S, S> proximityListener = ( parent, child ) -> {
		// default no-op
	};

	/**
	 * @param diff The function that calculates the distance between nodes. Returns:
	 *             <ul>
	 *             <li>0 if the items are identical</li>
	 *             <li>negative values if the items cannot be compared at all</li>
	 *             <li>larger values if there is a greater degree of difference
	 *             between the items</li>
	 *             </ul>
	 */
	public DiffGraph( BiFunction<S, S, Integer> diff ) {
		this.diff = diff;
	}

	/**
	 * @param node The value to add to the graph
	 * @return <code>this</code>
	 */
	public DiffGraph<S> add( S node ) {
		nodes.add( node );
		return this;
	}

	/**
	 * Finds the minimum spanning tree (MST) of the items
	 *
	 * @param root The value at which to start building the tree
	 * @return The MST root node
	 */
	public DAG<S> minimumSpanningTree( S root ) {

		DAG<S> mst = new DAG<>( root );

		// The set of nodes that are not yet in the MST
		Set<PrimNode<S>> available = nodes.stream()
				.filter( v -> !v.equals( root ) )
				.map( v -> new PrimNode<>( mst, v, diff ) )
				.collect( toCollection( HashSet::new ) );

		available.forEach( a -> proximityListener.accept( mst.value(), a.value() ) );

		// Repeatedly find the node that is not yet in the MST but that is closest to it
		PrimNode<S> closest = null;
		while( null != (closest = available.stream()
				.min( PrimNode.CLOSEST )
				.filter( PrimNode::connected )
				.orElse( null )) ) {
			// add it to the MST
			available.remove( closest );
			DAG<S> added = closest.joinTree();
			mstListener.accept( added.parent().value(), added.value() );

			// Recalculate everyone's distance to the MST
			available.forEach( a -> proximityListener.accept( a.update( added ).value(), a.value() ) );
			// we could avoid iterating over every node by only considering those that have
			// edges to the added one, but our graph is likely to be extremely
			// well-connected, so:
			// * we're not bothering to store that data
			// * it wouldn't save us any time
		}

		return mst;
	}

	/**
	 * @param l A callback that will be invoked when a node is added to the Minimum
	 *          Spanning Tree. The arguments will be <code>( parent, child )</code>
	 * @return <code>this</code>
	 */
	public DiffGraph<S> withMSTListener( BiConsumer<S, S> l ) {
		mstListener = l;
		return this;
	}

	/**
	 * @param l A callback that will be invoked when the distance between a node and
	 *          the Minimum Spanning Tree is updated. The arguments will be
	 *          <code>( parent, child )</code>
	 * @return <code>this</code>
	 */
	public DiffGraph<S> withProximityListener( BiConsumer<S, S> l ) {
		proximityListener = l;
		return this;
	}

	/**
	 * @param from a node
	 * @param to   a node
	 * @return The distance between the supplied nodes
	 */
	public int distance( S from, S to ) {
		return diff.apply( from, to );
	}

	/**
	 * @return The number of nodes
	 */
	public int size() {
		return nodes.size();
	}
}
