/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.graph;

import static java.util.stream.Collectors.joining;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A Directed Acyclic Graph, or tree.
 *
 * @param <S> item type
 */
public class DAG<S> {

	private final DAG<S> parent;
	private final S value;
	private final Set<DAG<S>> children = new HashSet<>();

	/**
	 * @param value root value
	 */
	public DAG( S value ) {
		this( null, value );
	}

	private DAG( DAG<S> parent, S value ) {
		this.parent = parent;
		this.value = value;
	}

	/**
	 * Adds a child to this tree
	 *
	 * @param v The value of the child
	 * @return The child node
	 */
	public DAG<S> withChild( S v ) {
		DAG<S> child = new DAG<>( this, v );
		children.add( child );
		return child;
	}

	/**
	 * @return The parent node
	 */
	public DAG<S> parent() {
		return parent;
	}

	/**
	 * @return The value of this node
	 */
	public S value() {
		return value;
	}

	/**
	 * @return The child nodes
	 */
	public Stream<DAG<S>> children() {
		return children.stream();
	}

	/**
	 * @return A stream of all values in this tree, in depth-first order
	 */
	public Stream<S> values() {
		return Stream.concat(
				Stream.of( value ),
				children().flatMap( DAG::values ) );
	}

	/**
	 * Recurses over the DAG structure
	 *
	 * @param visitor called once for every node
	 */
	public void traverse( Consumer<DAG<S>> visitor ) {
		visitor.accept( this );
		children().forEach( c -> c.traverse( visitor ) );
	}

	@Override
	public String toString() {
		return toString( "" );
	}

	private String toString( String indent ) {
		String childIndent = indent + value + " | ";
		return indent + value
				+ children()
						.map( c -> "\n" + c.toString( childIndent ) )
						.collect( joining() );
	}
}
