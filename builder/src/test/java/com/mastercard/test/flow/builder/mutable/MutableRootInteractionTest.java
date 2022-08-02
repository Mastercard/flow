package com.mastercard.test.flow.builder.mutable;

import static com.mastercard.test.flow.builder.mock.Actrs.AVA;
import static com.mastercard.test.flow.builder.mock.Actrs.BEN;
import static com.mastercard.test.flow.builder.mock.Actrs.CHE;
import static com.mastercard.test.flow.builder.mock.Actrs.DAN;
import static com.mastercard.test.flow.builder.mock.Actrs.EFA;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.builder.concrete.ConcreteInteraction;
import com.mastercard.test.flow.builder.concrete.ConcreteRootInteraction;
import com.mastercard.test.flow.builder.mock.Msg;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercises the mutable interaction API
 */
@SuppressWarnings("static-method")
class MutableRootInteractionTest {

	/**
	 * Exercises builder API
	 */
	@Test
	void fields() {
		MutableInteraction root = new MutableRootInteraction();
		MutableInteraction ret = root
				.requester( AVA )
				.request( new Msg( "Hi Ben!" ) )
				.responder( BEN )
				.response( new Msg( "Hi Ava!" ) )
				.tags( Tags.add( "greet" ) )
				.children( l -> assertEquals( 0, l.size() ) );

		assertSame( root, ret, "fluent API" );
		assertEquals( AVA, root.requester() );
		assertEquals( "Msg[Child^1 of 'Hi Ben!']", root.request().assertable() );
		assertEquals( BEN, root.responder() );
		assertEquals( "Msg[Child^1 of 'Hi Ava!']", root.response().assertable() );
		assertEquals( 0, root.children().count() );
	}

	/**
	 * Exercises inheritance
	 */
	@Test
	void inheritance() {
		ConcreteRootInteraction basis = new ConcreteRootInteraction(
				AVA, new Msg( "req" ),
				BEN, new Msg( "res" ),
				Tags.tags( "a", "b" ) );
		basis.with( new ConcreteInteraction( basis, null, DAN, null, Tags.tags( "c" ) ) );

		MutableRootInteraction root = new MutableRootInteraction( basis );

		root.requester( CHE )
				.response( new Msg( "overridden" ) );

		assertEquals( CHE, root.requester() );
		assertEquals( "Msg[Child^1 of 'req']", root.request().assertable() );
		assertEquals( BEN, root.responder() );
		assertEquals( "Msg[Child^1 of 'overridden']", root.response().assertable() );
		assertEquals( 1, root.children().count() );

		assertEquals( BEN, root.children().findFirst().get().requester() );
		assertEquals( DAN, root.children().findFirst().get().responder() );
	}

	/**
	 * Exercises adding a child calls
	 */
	@Test
	void call() {
		MutableInteraction root = new MutableRootInteraction()
				.requester( AVA )
				.responder( BEN );
		MutableInteraction ret = root
				.call( c -> c.to( CHE ).request( null ).response( null ) )
				.call( 0, c -> c.to( DAN ).request( null ).response( null ) )
				.call( 1, c -> c.to( EFA ).request( null ).response( null ) )
				.call( 10, c -> c.to( AVA ).request( null ).response( null ) )
				.call( -2, c -> c.to( BEN ).request( null ).response( null ) );

		assertSame( root, ret, "fluent API" );
		assertEquals( ""
				+ "DAN\n"
				+ "EFA\n"
				+ "CHE\n"
				+ "BEN\n"
				+ "AVA",
				root.children()
						.map( i -> i.responder().name() )
						.collect( joining( "\n" ) ) );
	}
}
