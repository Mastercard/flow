package com.mastercard.test.flow.builder.concrete;

import static com.mastercard.test.flow.builder.mock.Actrs.AVA;
import static com.mastercard.test.flow.builder.mock.Actrs.BEN;
import static com.mastercard.test.flow.builder.mock.Actrs.CHE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.util.Tags;

/**
 * Execising {@link ConcreteInteraction}
 */
@SuppressWarnings("static-method")
class ConcreteInteractionTest {

	/**
	 * Exercises the child-population method
	 */
	@Test
	void children() {
		ConcreteRootInteraction root = new ConcreteRootInteraction( AVA, null, BEN, null,
				Tags.tags() );
		ConcreteInteraction child = new ConcreteInteraction( root, null, CHE, null, Tags.tags() );
		ConcreteInteraction complete = root.with( child )
				.complete();

		assertSame( root, complete );

		assertEquals( AVA, root.requester() );
		assertEquals( BEN, root.responder() );

		assertEquals( BEN, root.children().findFirst().get().requester() );
		assertEquals( CHE, root.children().findFirst().get().responder() );

		assertThrows( UnsupportedOperationException.class, () -> root.with( null ),
				"Children cannot be added after complete() call" );
	}

}
