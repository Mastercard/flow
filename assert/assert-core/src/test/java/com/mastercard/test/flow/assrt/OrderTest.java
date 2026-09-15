package com.mastercard.test.flow.assrt;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.builder.Creator;

/**
 * Exercises {@link Order}
 */
@SuppressWarnings("static-method")
class OrderTest {

	private static final Collection<Applicator<?>> EMPTY = Collections.emptyList();

	/** An unchained flow ID is not an explicit chain membership declaration. */
	@Test
	void unchainedIdentityCannotJoinAnActualChain() {
		Flow a = Creator.build( f -> f.meta( m -> m.description( "A" )
				.tags( tags -> tags.add( "chain:B []" ) ) ) );
		Flow b = Creator.build( f -> f.meta( m -> m.description( "B" ) ).prerequisite( a ) );
		Flow c = Creator.build( f -> f.meta( m -> m.description( "C" )
				.tags( tags -> tags.add( "chain:B []" ) ) ).prerequisite( b ) );
		assertTrue( assertThrows( IllegalArgumentException.class,
				() -> new Order( Stream.of( c, b, a ), EMPTY ).order() )
						.getMessage().contains( "contracted chains" ) );
	}

	/**
	 * No order constraints between {@link Flow}s, preferred alphabetical ordering
	 * is applied
	 */
	@Test
	void isolated() {
		assertOrder( new Order( flws( "d[],b[],a[],c[]", null, null ), EMPTY ),
				"[a [], b [], c [], d []]" );
	}

	/**
	 * If on {@link Flow} is based on another, we'd prefer to process the basis
	 * first. If the basis fails, we can skip the descendant as it is likely to fail
	 * in the same way.
	 */
	@Test
	void basis() {
		assertOrder( new Order( flws( "a[],b[]", "ab", null ), EMPTY ),
				"[b [], a []]" );
	}

	/**
	 * A {@link Flow}s prerequisites must be processed first
	 */
	@Test
	void prerequisite() {
		assertOrder( new Order( flws( "a[],b[]", null, "ab" ), EMPTY ),
				"[b [], a []]" );
	}

	/**
	 * Chains of constraints are honoured
	 */
	@Test
	void transitive() {
		assertOrder( new Order( flws( "a[],b[],c[],d[]", "bc", "ab cd" ), EMPTY ),
				"[d [], c [], b [], a []]" );
	}

	/**
	 * Basis constrains will be broken to satisfy a prerequisite constraint
	 */
	@Test
	void precedence() {
		assertOrder( new Order( flws( "a[],b[]", "ba", "ab" ), EMPTY ),
				"[b [], a []]" );
	}

	/**
	 * Non-chained {@link Flow}s are not interleaved into chains
	 */
	@Test
	void chains() {
		assertOrder( new Order( flws( "a[chain:foo],b[],c[chain:foo]", null, null ), EMPTY ),
				"[a [chain:foo], c [chain:foo], b []]" );
	}

	/**
	 * Order constraints are also applied inside chains
	 */
	@Test
	void chainOrder() {
		assertOrder( new Order( flws( "a[chain:foo],b[],c[chain:foo]", null, "ac" ), EMPTY ),
				"[b [], c [chain:foo], a [chain:foo]]" );
		// also note that b comes before the chain, as it compares favourably with the
		// new head of the chain
	}

	/** Hard edges cannot be removed to repair an impossible dependency order. */
	@Test
	void hardCyclesAreRejected() {
		IllegalArgumentException failure = assertThrows( IllegalArgumentException.class,
				() -> new Order( flws( "a[],b[],c[]", "ab", "ab bc ca" ), EMPTY ).order() );
		assertTrue( failure.getMessage().contains( "Hard prerequisite cycle" ), failure::getMessage );
	}

	/** A1 -> B1 -> A2 cannot coexist with uninterrupted A1/A2 execution. */
	@Test
	void contractedChainContradictionIsRejected() {
		Flw a1 = new Flw( "A1 [chain:A]" );
		Flw b1 = new Flw( "B1 []" ).depedency( a1 );
		Flw a2 = new Flw( "A2 [chain:A]" ).depedency( b1 );
		// Neither a contrary basis preference nor dropping a hard edge is a repair.
		a1.basis( a2 );
		IllegalArgumentException failure = assertThrows( IllegalArgumentException.class,
				() -> new Order( Stream.of( a2, b1, a1 ), EMPTY ).order() );
		assertTrue( failure.getMessage().contains( "contracted chains" ), failure::getMessage );
		assertTrue( failure.getMessage().contains( "A1" ), failure::getMessage );
		assertTrue( failure.getMessage().contains( "B1" ), failure::getMessage );
		assertTrue( failure.getMessage().contains( "A2" ), failure::getMessage );
	}

	/**
	 * Ordering ignores absent references and never turns a self binding into a
	 * wait.
	 */
	@Test
	void absentAndSelfReferencesKeepExistingOrderSemantics() {
		Flw a = new Flw( "a []" );
		Flw b = new Flw( "b []" ).depedency( a ).depedency( a );
		a.depedency( a ).depedency( null ).depedency( new Flw( "outside []" ) );
		assertOrder( new Order( Stream.of( b, a ), EMPTY ), "[a [], b []]" );
	}

	private static void assertOrder( Order order, String expect ) {
		assertEquals( expect, order.order().collect( Collectors.toList() ).toString() );
	}

	private static Stream<Flow> flws( String flows, String bases, String deps ) {
		Map<String, Flw> flws = new HashMap<>();
		for( String spec : flows.split( "," ) ) {
			Flw flw = new Flw( spec.trim() );
			flws.put( flw.meta().description(), flw );
		}

		if( bases != null ) {
			for( String base : bases.split( " " ) ) {
				flws.get( base.substring( 0, 1 ) ).basis( flws.get( base.substring( 1 ) ) );
			}
		}

		if( deps != null ) {

			for( String dep : deps.split( " " ) ) {
				flws.get( dep.substring( 0, 1 ) ).depedency( flws.get( dep.substring( 1 ) ) );
			}
		}

		return flws.values().stream().map( Flow.class::cast );
	}
}
