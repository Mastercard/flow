package com.mastercard.test.flow.assrt.order;

/**
 * A weighted, directed edge between two {@link Node}s
 *
 * @param <T> The {@link Node} type
 */
class Edge<T> {

	private final Node<T> from;
	private final Node<T> to;
	private final int weight;

	/**
	 * @param from   Source {@link Node}
	 * @param to     Destination {@link Node}
	 * @param weight {@link Edge} weight
	 */
	Edge( Node<T> from, Node<T> to, int weight ) {
		this.from = from;
		this.to = to;
		this.weight = weight;
	}

	/**
	 * @return The destination {@link Node}
	 */
	public Node<T> to() {
		return to;
	}

	/**
	 * @return The edge weight
	 */
	public int weight() {
		return weight;
	}

	/**
	 * Removes this edge from the source {@link Node}
	 */
	void delete() {
		from.remove( this );
	}

	@Override
	public String toString() {
		return from.value() + "-" + weight + "->" + to.value();
	}
}
