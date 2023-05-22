package com.mastercard.test.flow.validation.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Reaches the parts of {@link DAG} that other tests don't
 */
@SuppressWarnings("static-method")
class DAGTest {

	/**
	 * Children are stringified in lexicographical order
	 */
	@Test
	void toStringOrder() {
		DAG<String> root = new DAG<>( "root" );
		Stream.of( "jihbacfged".split( "" ) )
				.forEach( root::withChild );

		assertEquals( ""
				+ "root\n"
				+ "├a\n"
				+ "├b\n"
				+ "├c\n"
				+ "├d\n"
				+ "├e\n"
				+ "├f\n"
				+ "├g\n"
				+ "├h\n"
				+ "├i\n"
				+ "└j", root.toString() );
	}
}
