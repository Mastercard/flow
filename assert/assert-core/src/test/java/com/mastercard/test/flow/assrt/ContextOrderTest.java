package com.mastercard.test.flow.assrt;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.mock.Flw;

/**
 * Exercises {@link ContextOrder}
 */
@SuppressWarnings("static-method")
class ContextOrderTest {

	private static List<Applicator<?>> APPLICATORS = Arrays.asList(
			new HeavyApplicator(), new MediumApplicator(), new LightApplicator() );

	/**
	 * Shows that an order that minimises the most expensive context switches is
	 * computed
	 */
	@Test
	void order() {
		// 64 flows, with the full range of context permutations
		List<Flow> flows = new ArrayList<>();
		for( int i = 0; i < 4; i++ ) {
			for( int j = 0; j < 4; j++ ) {
				for( int k = 0; k < 4; k++ ) {
					flows.add( new Flw( String.format( "flw [h%s, m%s, l%s]", i, j, k ) )
							.context( i == 0 ? null : new HeavyContext( i ) )
							.context( j == 0 ? null : new MediumContext( j ) )
							.context( k == 0 ? null : new LightContext( k ) ) );
				}
			}
		}

		assertEquals( 64, flows.size() );

		// start with a random order
		Collections.shuffle( flows, new Random( 20220107L ) );

		// oh dear, that's a lot of context switches
		Assertions.assertEquals( 35, countContextSwitches( flows, HeavyContext.class ) );
		Assertions.assertEquals( 30, countContextSwitches( flows, MediumContext.class ) );
		Assertions.assertEquals( 29, countContextSwitches( flows, LightContext.class ) );

		// optimise the order
		ContextOrder order = new ContextOrder( flows, APPLICATORS );
		Collections.sort( flows, order );

		// much better!
		Assertions.assertEquals( 3, countContextSwitches( flows, HeavyContext.class ) );
		Assertions.assertEquals( 12, countContextSwitches( flows, MediumContext.class ) );
		Assertions.assertEquals( 48, countContextSwitches( flows, LightContext.class ) );
	}

	/**
	 * Shows that alphabetical order is enforced within context blocks
	 */
	@Test
	void alpha() {

		// 27 flows, with the full range of context permutations
		List<Flow> flows = new ArrayList<>();
		for( int i = 1; i < 3; i++ ) {
			for( int j = 1; j < 3; j++ ) {
				flows.add( new Flw( String.format( "a [h%s, m%s]", i, j ) )
						.context( new HeavyContext( i ) )
						.context( new MediumContext( j ) ) );
				flows.add( new Flw( String.format( "b [h%s, m%s]", i, j ) )
						.context( new HeavyContext( i ) )
						.context( new MediumContext( j ) ) );
				flows.add( new Flw( String.format( "c [h%s, m%s]", i, j ) )
						.context( new HeavyContext( i ) )
						.context( new MediumContext( j ) ) );
			}
		}

		// start with a random order
		Collections.shuffle( flows, new Random( 20220107L ) );

		assertEquals( "" +
				"b [h1, m1]\n" +
				"a [h2, m1]\n" +
				"a [h1, m2]\n" +
				"c [h2, m1]\n" +
				"c [h2, m2]\n" +
				"c [h1, m1]\n" +
				"c [h1, m2]\n" +
				"b [h2, m1]\n" +
				"a [h1, m1]\n" +
				"b [h2, m2]\n" +
				"a [h2, m2]\n" +
				"b [h1, m2]",
				flows.stream()
						.map( f -> f.meta().id() )
						.collect( joining( "\n" ) ) );

		// optimise the order
		ContextOrder order = new ContextOrder( flows, APPLICATORS );
		Collections.sort( flows, order );

		assertEquals( "" +
				"a [h1, m1]\n" +
				"b [h1, m1]\n" +
				"c [h1, m1]\n" +
				"a [h1, m2]\n" +
				"b [h1, m2]\n" +
				"c [h1, m2]\n" +
				"a [h2, m1]\n" +
				"b [h2, m1]\n" +
				"c [h2, m1]\n" +
				"a [h2, m2]\n" +
				"b [h2, m2]\n" +
				"c [h2, m2]",
				flows.stream()
						.map( f -> f.meta().id() )
						.collect( joining( "\n" ) ) );
	}

	private static <T extends IntContext> int countContextSwitches( List<Flow> flows,
			Class<T> type ) {
		int switches = 0;
		int current = -1;
		for( Flow flow : flows ) {

			int v = flow.context()
					.filter( c -> c.getClass().equals( type ) )
					.findFirst()
					.map( type::cast )
					.map( IntContext::value )
					.orElse( -1 );

			if( v != -1 && v != current ) {
				switches++;
				current = v;
			}
		}
		return switches;
	}

	private static abstract class IntContext implements Context {

		private final String name;
		private final int value;

		protected IntContext( String name, int value ) {
			this.name = name;
			this.value = value;
		}

		@Override
		public String name() {
			return name;
		}

		public int value() {
			return value;
		}

		@Override
		public Set<Actor> domain() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Context child() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return name + ":" + value;
		}
	}

	private static abstract class IntApplicator<T extends IntContext> extends Applicator<T> {
		protected IntApplicator( Class<T> type, int cost ) {
			super( type, cost );
		}

		@Override
		public Comparator<T> order() {
			return ( a, b ) -> a.value() - b.value();
		}

		@Override
		public void transition( T from, T to ) {
			throw new UnsupportedOperationException();
		}
	}

	private static class HeavyContext extends IntContext {
		public HeavyContext( int value ) {
			super( "hvy", value );
		}
	}

	private static class MediumContext extends IntContext {
		public MediumContext( int value ) {
			super( "mdm", value );
		}
	}

	private static class LightContext extends IntContext {
		public LightContext( int value ) {
			super( "lht", value );
		}
	}

	private static class HeavyApplicator extends IntApplicator<HeavyContext> {
		public HeavyApplicator() {
			super( HeavyContext.class, 3 );
		}
	}

	private static class MediumApplicator extends IntApplicator<MediumContext> {
		public MediumApplicator() {
			super( MediumContext.class, 2 );
		}
	}

	private static class LightApplicator extends IntApplicator<LightContext> {
		public LightApplicator() {
			super( LightContext.class, 1 );
		}
	}
}
