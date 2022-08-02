package com.mastercard.test.flow.report.data;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link InteractionData}
 */
@SuppressWarnings("static-method")
class InteractionDataTest {

	/**
	 * Exercises
	 * {@link InteractionData#update(java.util.function.Predicate, java.util.function.Consumer)}
	 */
	@Test
	void update() {
		InteractionData child = new InteractionData( "BEN", "CHE", new TreeSet<>(),
				null, null, emptyList() );
		InteractionData parent = new InteractionData( "AVA", "BEN", new TreeSet<>(),
				null, null, Arrays.asList( child ) );

		parent.update( i -> "CHE".equals( i.responder ), i -> i.tags.add( "updated" ) );

		assertEquals( "[]", parent.tags.toString() );
		assertEquals( "[updated]", child.tags.toString() );
	}
}
