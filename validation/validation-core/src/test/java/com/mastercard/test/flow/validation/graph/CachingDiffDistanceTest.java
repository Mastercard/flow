package com.mastercard.test.flow.validation.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link CachingDiffDistance}
 */
@SuppressWarnings("static-method")
class CachingDiffDistanceTest {

	/**
	 * Shows that stringification results are cached
	 */
	@Test
	void stringify() {
		AtomicInteger toStringCount = new AtomicInteger( 0 );
		CachingDiffDistance<Item> cdd = new CachingDiffDistance<>( i -> {
			toStringCount.incrementAndGet();
			return i.toString();
		}, null );

		Item i = new Item( 1 );

		assertEquals( "1", cdd.stringify( i ), "results as expected" );
		assertEquals( 1, toStringCount.get(), "stringified only once" );

		assertEquals( "1", cdd.stringify( i ), "results as expected again" );
		assertEquals( 1, toStringCount.get(), "and we haven't repeated stringification" );
	}

	/**
	 * Shows that distance calculations are cached
	 */
	@Test
	void apply() {
		AtomicInteger toStringCount = new AtomicInteger( 0 );
		AtomicInteger distanceCount = new AtomicInteger( 0 );
		CachingDiffDistance<Item> cdd = new CachingDiffDistance<>(
				i -> {
					toStringCount.incrementAndGet();
					return i.toString();
				},
				( a, b ) -> {
					distanceCount.incrementAndGet();
					return Item.distance( a, b );
				} );

		Item i = new Item( 3 );
		Item j = new Item( 5 );

		assertEquals( 2, cdd.apply( i, j ), "results as expected" );
		assertEquals( 2, toStringCount.get(), "stringifed each item once" );
		assertEquals( 1, distanceCount.get(), "distance computed once" );

		assertEquals( 2, cdd.apply( i, j ), "results as expected again" );
		assertEquals( 2, toStringCount.get(), "still only stringifed each item once" );
		assertEquals( 1, distanceCount.get(), "distance computed still only once" );
	}
}
