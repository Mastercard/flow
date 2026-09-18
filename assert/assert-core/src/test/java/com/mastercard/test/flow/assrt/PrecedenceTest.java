package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.mock.AltTestContext;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.assrt.mock.TestContext;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.Transmission.Type;

/**
 * Exercises {@link Precedence}
 */
@SuppressWarnings("static-method")
class PrecedenceTest {

	/** A flow waits for the flows it binds values from. */
	@Test
	void dependencyBindings() {
		Flow a = flow( "a" );
		Flow b = flow( "b", a );
		Flow c = flow( "c", a );
		Precedence order = new Precedence( List.of( a, b, c ) );
		assertEquals( List.of( 0 ), order.roots() );
		assertEquals( Set.of( 1, 2 ), order.successors( 0 ) );
		assertEquals( Set.of(), order.successors( 1 ) );
		Precedence.Readiness readiness = order.readiness();
		assertEquals( List.of( 1, 2 ), readiness.finished( 0 ) );
		assertEquals( List.of(), readiness.finished( 1 ) );
	}

	/**
	 * A descendant waits for its nearest selected basis ancestor, even through
	 * unselected intermediates. Sibling branches are not serialised.
	 */
	@Test
	void basisAncestry() {
		Flw root = flw( "root []" );
		Flw left = flw( "left []" ).basis( root );
		Flw unselected = flw( "unselected []" ).basis( root );
		Flw right = flw( "right []" ).basis( unselected );
		Flw leaf = flw( "leaf []" ).basis( right );
		Precedence order = new Precedence( List.of( root, left, right, leaf ) );
		assertEquals( List.of( 0 ), order.roots() );
		assertEquals( Set.of( 1, 2 ), order.successors( 0 ) );
		assertEquals( Set.of(), order.successors( 1 ) );
		assertEquals( Set.of( 3 ), order.successors( 2 ) );
	}

	/** Canonical order is kept between comparable flows, whichever is the basis. */
	@Test
	void invertedBasisRankKeepsCanonicalOrder() {
		Flw ancestor = flw( "ancestor []" );
		Flw child = flw( "child []" ).basis( ancestor );
		Precedence order = new Precedence( List.of( child, ancestor ) );
		assertEquals( List.of( 0 ), order.roots() );
		assertEquals( Set.of( 1 ), order.successors( 0 ) );
	}

	/**
	 * Flows that publish into the same destination are serialised in canonical
	 * order; an unrelated publisher is not.
	 */
	@Test
	void sameDestinationPublication() {
		Flow a = publisher( "a" );
		Flow b = publisher( "b" );
		Flow c = Creator.build( f -> f.meta( m -> m.description( "c" ) )
				.call( i -> i.from( TestModel.Actors.A ).to( TestModel.Actors.B )
						.request( new Text( "pending" ) ).response( new Text( "pending" ) ) )
				.dependency( a, d -> d.from( i -> true, Type.RESPONSE, ".+" )
						.to( i -> true, Type.REQUEST, ".+" ) )
				.dependency( b, d -> d.from( i -> true, Type.RESPONSE, ".+" )
						.to( i -> true, Type.RESPONSE, ".+" ) ) );
		Precedence order = new Precedence( List.of( a, b, c, publisher( "d" ) ) );
		assertEquals( List.of( 0, 3 ), order.roots() );
		assertEquals( Set.of( 1, 2 ), order.successors( 0 ) );
		assertEquals( Set.of( 2 ), order.successors( 1 ) );
		assertEquals( Set.of(), order.successors( 3 ) );
	}

	/**
	 * Edges into a chain land on its first member, edges out leave from its last,
	 * and consecutive members are linked, so nothing outside can interleave.
	 */
	@Test
	void chainContraction() {
		Flow c = flow( "c" );
		Flow a1 = chained( "a1", "A" );
		Flow a2 = chained( "a2", "A", c );
		Flow b = flow( "b", a1 );
		Precedence order = new Precedence( List.of( c, a1, a2, b ) );
		assertEquals( List.of( 0 ), order.roots() );
		assertEquals( Set.of( 1, 2 ), order.successors( 0 ) );
		assertEquals( Set.of( 2, 3 ), order.successors( 1 ) );
		assertEquals( Set.of( 3 ), order.successors( 2 ) );
		Precedence.Readiness readiness = order.readiness();
		assertEquals( List.of( 1 ), readiness.finished( 0 ) );
		assertEquals( List.of( 2 ), readiness.finished( 1 ) );
		assertEquals( List.of( 3 ), readiness.finished( 2 ) );
	}

	/**
	 * Flows that apply a context are serialised in canonical order; flows without
	 * one stay free to overlap them.
	 */
	@Test
	void contextApplication() {
		Flw first = flw( "first []" ).context( new TestContext() );
		Flw free = flw( "free []" );
		Flw second = flw( "second []" ).context( new AltTestContext() );
		Flw third = flw( "third []" ).context( new TestContext() );
		Precedence order = new Precedence( List.of( first, free, second, third ) );
		assertEquals( List.of( 0, 1 ), order.roots() );
		assertEquals( Set.of( 2 ), order.successors( 0 ) );
		assertEquals( Set.of(), order.successors( 1 ) );
		assertEquals( Set.of( 3 ), order.successors( 2 ) );
	}

	/** Model errors fail preparation with the existing messages. */
	@Test
	void failures() {
		Flw a = flw( "a []" );
		Flw b = flw( "b []" ).basis( a );
		a.basis( b );
		assertEquals( "Cyclic Flow basis", assertThrows( IllegalArgumentException.class,
				() -> new Precedence( List.of( a, b ) ) ).getMessage() );

		Flow source = flow( "source" );
		Flow dependent = flow( "dependent", source );
		assertEquals( "Absent or noncanonical Flow prerequisite",
				assertThrows( IllegalArgumentException.class,
						() -> new Precedence( List.of( dependent ) ) ).getMessage() );
		assertEquals( "Absent or noncanonical Flow prerequisite",
				assertThrows( IllegalArgumentException.class,
						() -> new Precedence( List.of( dependent, source ) ) ).getMessage() );

		assertEquals( "Duplicate selected Flow reference", assertThrows( IllegalArgumentException.class,
				() -> new Precedence( List.of( source, source ) ) ).getMessage() );

		// A1 -> B -> A2 cannot coexist with uninterrupted A1/A2 execution
		Flow a1 = chained( "a1", "A" );
		Flow outside = flow( "b", a1 );
		Flow a2 = chained( "a2", "A", outside );
		assertEquals( "Contradictory contracted chain precedence",
				assertThrows( IllegalArgumentException.class,
						() -> new Precedence( List.of( a1, outside, a2 ) ) ).getMessage() );
	}

	private static Flow chained( String name, String chain, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ).tags( t -> t.add( Order.CHAIN_TAG_PREFIX + chain ) ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Flow publisher( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( TestModel.Actors.A ).to( TestModel.Actors.B )
						.request( new Text( "request" ) ).response( new Text( name ) ) ) );
	}

	private static Flow flow( String name, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	/**
	 * @param id description and tags
	 * @return An interaction-free mock, for basis, chain and context structure
	 */
	private static Flw flw( String id ) {
		return new Flw( id ) {
			@Override
			public com.mastercard.test.flow.Interaction root() {
				return null;
			}
		};
	}
}
