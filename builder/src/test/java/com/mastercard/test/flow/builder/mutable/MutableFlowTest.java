package com.mastercard.test.flow.builder.mutable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.builder.CreatorTest;
import com.mastercard.test.flow.builder.mock.Actrs;

/**
 * Exercising {@link MutableFlow}
 */
@SuppressWarnings("static-method")
class MutableFlowTest {

	/**
	 * Exercising fluent interface
	 */
	@Test
	void fluency() {
		MutableFlow flow = new MutableFlow();
		Assertions.assertSame( flow, flow.meta( d -> {
			// no-op
		} ) );
		Assertions.assertSame( flow, flow.root( null ) );
		Assertions.assertSame( flow, flow.implicit( d -> {
			// no-op
		} ) );

		Context ctx = new Context() {

			@Override
			public String name() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Set<Actor> domain() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Context child() {
				throw new UnsupportedOperationException();
			}
		};
		Assertions.assertSame( flow, flow.context( ctx ) );
		Assertions.assertSame( flow, flow.context( Context.class, null ) );
		Assertions.assertSame( flow, flow.context( ctx.getClass(), c -> {
			// no-op
		} ) );

		Residue rsd = new Residue() {

			@Override
			public String name() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Residue child() {
				throw new UnsupportedOperationException();
			}
		};
		Assertions.assertSame( flow, flow.residue( rsd ) );
		Assertions.assertSame( flow, flow.residue( Residue.class, null ) );
		Assertions.assertSame( flow, flow.residue( rsd.getClass(), r -> {
			// no-op
		} ) );
	}

	/**
	 * Exercises error case of updating a non-existent context
	 */
	@Test
	void missingContext() {

		MutableFlow flow = new MutableFlow();
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> flow.context( Context.class, d -> {
					// no-op
				} ) );
		assertEquals(
				"No such context for type interface com.mastercard.test.flow.Context",
				ise.getMessage() );
	}

	/**
	 * Exercises error case of updating a non-existent residue
	 */
	@Test
	void missingResidue() {

		MutableFlow flow = new MutableFlow();
		IllegalStateException ise = assertThrows( IllegalStateException.class,
				() -> flow.residue( Residue.class, d -> {
					// no-op
				} ) );
		assertEquals(
				"No such residue for type interface com.mastercard.test.flow.Residue",
				ise.getMessage() );
	}

	/**
	 * Exercising API
	 */
	@Test
	void fields() {
		MutableFlow flow = new MutableFlow();
		MutableRootInteraction ntr = new MutableRootInteraction();
		ntr.requester( Actrs.AVA );

		MutableFlow ret = flow
				.dependency( new MutableDependency()
						.sink( s -> s.field( "sink field" ) ) )
				.meta( data -> data.description( "description" ) )
				.root( ntr );

		assertSame( flow, ret );

		Flow built = flow.build();
		assertEquals( "sink field", built.dependencies().findFirst().get().sink().field() );
		assertEquals( "description", built.meta().description() );
		assertEquals( Actrs.AVA, built.root().requester() );
	}

	/**
	 * Exercising data inheritance
	 */
	@Test
	void inheritance() {
		{
			Flow basis = CreatorTest.metadataFlow();
			MutableFlow mf = new MutableFlow( basis );
			assertSame( basis, mf.basis() );

			Flow child = mf.build();
			assertSame( basis, child.basis() );
			assertEquals( "metadata only [bar, foo]", child.meta().id() );
		}
		{
			Flow basis = CreatorTest.basicFlow();

			MutableFlow mf = new MutableFlow( basis );
			assertEquals( Actrs.AVA, mf.root().requester() );
			assertEquals(
					"Msg[Child^2 of 'Hi! My name is Ava. I have a voracious appetite for brie.']",
					mf.root().request().assertable() );
			assertEquals( Actrs.BEN, mf.root().responder() );
			assertEquals(
					"Msg[Child^2 of 'Nice to meet you Ava, my name is Ben. I can provide brie.']",
					mf.root().response().assertable() );

			Flow child = mf.build();
			assertSame( basis, child.basis() );
			assertEquals( Actrs.AVA, child.root().requester() );
			assertEquals(
					"Msg[Child^2 of 'Hi! My name is Ava. I have a voracious appetite for brie.']",
					child.root().request().assertable() );
			assertEquals( Actrs.BEN, child.root().responder() );
			assertEquals(
					"Msg[Child^2 of 'Nice to meet you Ava, my name is Ben. I can provide brie.']",
					child.root().response().assertable() );
		}
	}
}
