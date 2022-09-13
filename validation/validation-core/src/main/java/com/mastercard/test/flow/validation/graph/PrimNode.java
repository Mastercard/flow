/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.graph;

import static java.util.Comparator.comparing;

import java.util.Comparator;
import java.util.function.BiFunction;

/**
 * A node in graph over which we're running Prim's algorithm to find the Minimum
 * Spanning Tree (MST) of a graph. The minimum spanning tree is the subset of
 * the graph that covers all nodes while minimising edge cost.
 *
 * @param <S> item type
 */
class PrimNode<S> {

	/**
	 * The value of this node
	 */
	private final S value;

	/**
	 * How to calculate the distance between values
	 */
	private final BiFunction<S, S, Integer> diff;

	/**
	 * The closest node in the MST
	 */
	private DAG<S> closest;

	/**
	 * The distance to the closest MST node
	 */
	private int distance;

	/**
	 * A comparator that will sort into ascending order of distance from the Minimum
	 * Spanning Tree
	 */
	public static final Comparator<PrimNode<?>> CLOSEST = comparing( PrimNode::distance );

	/**
	 * @param closest The closest node minimum spanning tree
	 * @param value   Node value
	 * @param diff    How to compute distances between node values
	 */
	public PrimNode( DAG<S> closest, S value, BiFunction<S, S, Integer> diff ) {
		this.value = value;
		this.closest = closest;
		this.diff = diff;
		distance = diff.apply( closest.value(), value );
	}

	/**
	 * Adds this node to the MST
	 *
	 * @return The newly-added MST node
	 */
	public DAG<S> joinTree() {
		return closest.withChild( value );
	}

	/**
	 * Updates this node's distance to the MST
	 *
	 * @param dag The newest node in the MST
	 */
	public void update( DAG<S> dag ) {
		int d = diff.apply( dag.value(), value );
		if( d < distance ) {
			distance = d;
			closest = dag;
		}
	}

	/**
	 * @return The distance from this node to the MST
	 */
	public int distance() {
		return distance;
	}

}
