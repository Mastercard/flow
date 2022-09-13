/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.graph;

import static java.util.stream.Collectors.toCollection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

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
	 * Recurses over the DAG structure
	 *
	 * @param visitor called once for every node
	 */
	public void traverse( Consumer<DAG<S>> visitor ) {
		visitor.accept( this );
		children.stream().forEach( c -> c.traverse( visitor ) );
	}

	@Override
	public String toString() {
		return toString( "" ).trim();
	}

	private String toString( String indent ) {
		StringBuilder sb = new StringBuilder();
		sb.append( indent ).append( value ).append( "\n" );
		Deque<DAG<S>> cq = children.stream()
				.sorted( ( a, b ) -> String.valueOf( a.value ).compareTo( String.valueOf( b.value ) ) )
				.collect( toCollection( ArrayDeque::new ) );

		while( !cq.isEmpty() ) {
			DAG<S> c = cq.removeFirst();
			String corner = cq.isEmpty() ? "└" : "├";
			sb.append( c.toString( indent
					.replace( '└', ' ' )
					.replace( '├', '│' ) + corner ) );
		}
		return sb.toString();
	}
}
