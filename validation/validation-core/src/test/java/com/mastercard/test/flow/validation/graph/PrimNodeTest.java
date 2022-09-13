package com.mastercard.test.flow.validation.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PrimNode}
 */
class PrimNodeTest {

	/**
	 * Shows that when a {@link PrimNode} is updated with two equidistant routes to
	 * the DAG, it retains the first one offered.
	 */
	@Test
	void update() {
		DAG<Item> low = new DAG<>( new Item( 1 ) );
		DAG<Item> high = new DAG( new Item( 3 ) );
		DAG<Item> far = new DAG<>( new Item( 5 ) );

		PrimNode<Item> pn = new PrimNode<>( far, new Item( 2 ), Item::distance );

		assertEquals( 3, pn.distance() );

		pn.update( low );
		assertEquals( 1, pn.distance() );

		pn.update( high );
		assertEquals( 1, pn.distance() );

		DAG<Item> dag = pn.joinTree();

		assertEquals( "1", dag.parent().value().toString() );
	}
}
