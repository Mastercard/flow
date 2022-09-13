package com.mastercard.test.flow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;

/**
 * Exercises {@link InteractionPredicate}
 */
@SuppressWarnings("static-method")
class InteractionPredicateTest {

	private enum Actrs implements Actor {
		AVA, BEN, CHE, DAN, EFA
	}

	private static final Interaction interaction( Actrs from, Actrs to, String... tags ) {
		Interaction ntr = Mockito.mock( Interaction.class );
		Mockito.when( ntr.requester() ).thenReturn( from );
		Mockito.when( ntr.responder() ).thenReturn( to );
		Set<String> ts = new TreeSet<>();
		Collections.addAll( ts, tags );
		Mockito.when( ntr.tags() ).thenReturn( ts );
		return ntr;
	}

	/**
	 * The empty predicate accepts anything
	 */
	@Test
	void empty() {
		InteractionPredicate empty = new InteractionPredicate();
		assertTrue( empty.test( null ) );
		assertTrue( empty.test( interaction( Actrs.AVA, Actrs.BEN, "tag" ) ) );

		assertEquals( "anything", empty.toString() );
	}

	/**
	 * Exercising {@link InteractionPredicate#from(Actor...)}
	 */
	@Test
	void from() {
		InteractionPredicate empty = new InteractionPredicate();
		InteractionPredicate single = empty.from( Actrs.AVA );

		assertNotSame( empty, single );

		assertTrue( single.test( interaction( Actrs.AVA, null ) ) );
		assertFalse( single.test( interaction( Actrs.BEN, null ) ) );
		assertFalse( single.test( interaction( Actrs.CHE, null ) ) );
		assertFalse( single.test( interaction( Actrs.DAN, null ) ) );
		assertFalse( single.test( interaction( Actrs.EFA, null ) ) );

		InteractionPredicate duble = single.from( Actrs.BEN, Actrs.CHE );

		assertNotSame( single, duble );

		assertFalse( duble.test( interaction( Actrs.AVA, null ) ) );
		assertTrue( duble.test( interaction( Actrs.BEN, null ) ) );
		assertTrue( duble.test( interaction( Actrs.CHE, null ) ) );
		assertFalse( duble.test( interaction( Actrs.DAN, null ) ) );
		assertFalse( duble.test( interaction( Actrs.EFA, null ) ) );

		assertEquals( "from [AVA]", single.toString() );
		assertEquals( "from [BEN, CHE]", duble.toString() );
	}

	/**
	 * Exercising {@link InteractionPredicate#to(Actor...)}
	 */
	@Test
	void to() {
		InteractionPredicate empty = new InteractionPredicate();
		InteractionPredicate single = empty.to( Actrs.AVA );

		assertNotSame( empty, single );

		assertTrue( single.test( interaction( null, Actrs.AVA ) ) );
		assertFalse( single.test( interaction( null, Actrs.BEN ) ) );
		assertFalse( single.test( interaction( null, Actrs.CHE ) ) );
		assertFalse( single.test( interaction( null, Actrs.DAN ) ) );
		assertFalse( single.test( interaction( null, Actrs.EFA ) ) );

		InteractionPredicate duble = single.to( Actrs.BEN, Actrs.CHE );

		assertNotSame( single, duble );

		assertFalse( duble.test( interaction( null, Actrs.AVA ) ) );
		assertTrue( duble.test( interaction( null, Actrs.BEN ) ) );
		assertTrue( duble.test( interaction( null, Actrs.CHE ) ) );
		assertFalse( duble.test( interaction( null, Actrs.DAN ) ) );
		assertFalse( duble.test( interaction( null, Actrs.EFA ) ) );

		assertEquals( "to [AVA]", single.toString() );
		assertEquals( "to [BEN, CHE]", duble.toString() );
	}

	/**
	 * Exercises {@link InteractionPredicate#with(String...)}
	 */
	@Test
	void with() {

		InteractionPredicate empty = new InteractionPredicate();
		InteractionPredicate with = empty.with( "b", "a", "c" );

		assertNotSame( empty, with );
		assertEquals( "has tags [a, b, c]", with.toString() );

		assertFalse( with.test( interaction( null, null ) ) );
		assertTrue( with.test( interaction( null, null, "a", "b", "c" ) ) );
		assertTrue( with.test( interaction( null, null, "a", "b", "c", "d" ) ) );
		assertFalse( with.test( interaction( null, null, "a", "b" ) ) );
		assertFalse( with.test( interaction( null, null, "a", "c" ) ) );
		assertFalse( with.test( interaction( null, null, "b", "c" ) ) );
	}

	/**
	 * Exercises {@link InteractionPredicate#without(String...)}
	 */
	@Test
	void without() {

		InteractionPredicate empty = new InteractionPredicate();
		InteractionPredicate without = empty.without( "b", "a", "c" );

		assertNotSame( empty, without );
		assertEquals( "lacks tags [a, b, c]", without.toString() );

		assertTrue( without.test( interaction( null, null ) ) );
		assertFalse( without.test( interaction( null, null, "a" ) ) );
		assertFalse( without.test( interaction( null, null, "b" ) ) );
		assertFalse( without.test( interaction( null, null, "c" ) ) );
		assertFalse( without.test( interaction( null, null, "a", "b", "c" ) ) );

		assertTrue( without.test( interaction( null, null, "d" ) ) );
	}

	/**
	 * Demonstrates a predicate with multiple conditions
	 */
	@Test
	void combined() {
		InteractionPredicate ip = new InteractionPredicate()
				.from( Actrs.BEN )
				.to( Actrs.DAN, Actrs.EFA )
				.with( "yes" )
				.without( "no" );

		assertEquals( "from [BEN] and to [DAN, EFA] and has tags [yes] and lacks tags [no]",
				ip.toString() );

		assertTrue( ip.test( interaction( Actrs.BEN, Actrs.DAN, "yes" ) ) );
		assertTrue( ip.test( interaction( Actrs.BEN, Actrs.EFA, "yes" ) ) );
		assertTrue( ip.test( interaction( Actrs.BEN, Actrs.DAN, "yes", "extra" ) ) );
		assertTrue( ip.test( interaction( Actrs.BEN, Actrs.EFA, "yes", "tags" ) ) );

		assertFalse( ip.test( interaction( Actrs.BEN, Actrs.EFA, "extra" ) ),
				"missing tag" );
		assertFalse( ip.test( interaction( Actrs.BEN, Actrs.EFA, "yes", "no" ) ),
				"verboten tag" );
		assertFalse( ip.test( interaction( Actrs.AVA, Actrs.DAN, "yes" ) ),
				"bad source" );
		assertFalse( ip.test( interaction( Actrs.BEN, Actrs.CHE, "yes" ) ),
				"bad destination" );
	}
}
