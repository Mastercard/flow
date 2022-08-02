package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.mock.Flw;

/**
 * Exercises {@link Order}
 */
@SuppressWarnings("static-method")
class OrderTest {

	private static final Collection<Applicator<?>> EMPTY = Collections.emptyList();

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
