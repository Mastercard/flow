package com.mastercard.test.flow.builder.mutable;

import static com.mastercard.test.flow.builder.mock.Actrs.AVA;
import static com.mastercard.test.flow.builder.mock.Actrs.BEN;
import static com.mastercard.test.flow.builder.mock.Actrs.CHE;
import static com.mastercard.test.flow.builder.mock.Actrs.DAN;
import static com.mastercard.test.flow.builder.mock.Actrs.EFA;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.builder.concrete.ConcreteInteraction;
import com.mastercard.test.flow.builder.mock.Actrs;
import com.mastercard.test.flow.builder.mock.Msg;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercising {@link MutableInteraction}
 */
@SuppressWarnings("static-method")
class MutableInteractionTest {

	private static MutableRootInteraction root() {
		MutableRootInteraction root = new MutableRootInteraction();
		root.requester( Actrs.AVA ).responder( Actrs.BEN );
		return root;
	}

	/**
	 * Exercises builder API
	 */
	@Test
	void fields() {
		MutableRootInteraction root = root();
		MutableInteraction mi = new MutableInteraction( root );
		MutableInteraction ret = mi
				.requester( Actrs.CHE )
				.responder( Actrs.DAN )
				.request( new Msg( "req" ) )
				.response( new Msg( "res" ) )
				.tags( Tags.add( "tag" ) );

		assertSame( mi, ret );

		assertEquals( Actrs.CHE, mi.requester() );
		assertEquals( "Msg[Child^1 of 'req']", mi.request().assertable() );
		assertEquals( Actrs.DAN, mi.responder() );
		assertEquals( "Msg[Child^1 of 'res']", mi.response().assertable() );
		assertEquals( "[tag]", mi.tags().toString() );

		ConcreteInteraction ci = mi.build( root.build() );
		assertEquals( Actrs.CHE, ci.requester() );
		assertEquals( "Msg[Child^1 of 'req']", ci.request().assertable() );
		assertEquals( Actrs.DAN, ci.responder() );
		assertEquals( "Msg[Child^1 of 'res']", ci.response().assertable() );
		assertEquals( "[tag]", ci.tags().toString() );
	}

	/**
	 * Tests inheritance
	 */
	@Test
	void inheritance() {
		ConcreteInteraction basis = new ConcreteInteraction(
				root().build(), new Msg( "req" ), Actrs.CHE, new Msg( "res" ), Tags.tags( "tag" ) );

		ConcreteInteraction ci = new MutableInteraction( root(), basis )
				.responder( Actrs.DAN )
				.response( new Msg( "overridden" ) )
				.tags( Tags.add( "new" ) )
				.build( root().build() );

		assertEquals( Actrs.BEN, ci.requester() );
		assertEquals( "Msg[Child^1 of 'req']", ci.request().assertable() );
		assertEquals( Actrs.DAN, ci.responder() );
		assertEquals( "Msg[Child^1 of 'overridden']", ci.response().assertable() );
		assertEquals( "[new, tag]", ci.tags().toString() );
	}

	/**
	 * Exercising adding child calls
	 */
	@Test
	void call() {

		MutableRootInteraction root = root();
		MutableInteraction mi = new MutableInteraction( root )
				.responder( CHE );
		MutableInteraction ret = mi
				.call( c -> c.to( CHE ).request( null ).response( null ) )
				.call( 0, c -> c.to( DAN ).request( null ).response( null ) )
				.call( 1, c -> c.to( EFA ).request( null ).response( null ) )
				.call( 10, c -> c.to( AVA ).request( null ).response( null ) )
				.call( -2, c -> c.to( BEN ).request( null ).response( null ) );

		assertSame( mi, ret, "fluent API" );
		assertEquals( ""
				+ "DAN\n"
				+ "EFA\n"
				+ "CHE\n"
				+ "BEN\n"
				+ "AVA",
				mi.build( root.build() )
						.children()
						.map( i -> i.responder().name() )
						.collect( joining( "\n" ) ) );
	}

	/**
	 * The API makes it pretty difficult to go off-piste when defining interactions,
	 * but here's what happens if you manage it
	 */
	@Test
	void badCall() {
		MutableInteraction wrong = new MutableInteraction( root() );
		MutableInteraction mi = new MutableInteraction( root() );

		Exception e = assertThrows( IllegalStateException.class, () -> mi.call( to -> wrong ) );
		assertEquals( "Failed to return to origin", e.getMessage() );
	}
}
