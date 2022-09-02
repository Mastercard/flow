package com.mastercard.test.flow.validation.graph;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link DiffGraph}
 */
@SuppressWarnings("static-method")
class DiffGraphTest {

	/**
	 * Exercising fluent addition
	 */
	@Test
	void fluency() {
		DiffGraph<Item> dg = new DiffGraph<>( Item::distance );
		assertSame( dg, dg.add( new Item( 0 ) ) );
	}

	/**
	 * Exercises finding a simple MST
	 */
	@Test
	void minimumSpanningTree() {
		List<Item> items = IntStream.rangeClosed( 1, 5 )
				.mapToObj( Item::new )
				.collect( toList() );

		BiConsumer<Item, String> test = ( in, out ) -> {
			DiffGraph<Item> dg = new DiffGraph<>( Item::distance );
			items.forEach( dg::add );
			assertEquals( out,
					dg.minimumSpanningTree( in ).toString(),
					"rooted at " + in );
		};

		test.accept( items.get( 0 ), ""
				+ "1\n"
				+ "└2\n"
				+ " └3\n"
				+ "  └4\n"
				+ "   └5" );

		test.accept( items.get( 2 ), ""
				+ "3\n"
				+ "├2\n"
				+ "│└1\n"
				+ "└4\n"
				+ " └5" );

		test.accept( items.get( 4 ), ""
				+ "5\n"
				+ "└4\n"
				+ " └3\n"
				+ "  └2\n"
				+ "   └1" );
	}

	/**
	 * Exercising the listener interface
	 */
	@Test
	void listener() {
		int[] fib = { 2, 3, 5, 8, 13 };
		List<Item> items = IntStream.range( 0, 5 )
				.map( i -> fib[i] )
				.mapToObj( Item::new )
				.collect( toList() );

		StringBuilder sb = new StringBuilder();
		DiffGraph<Item> dg = new DiffGraph<>( Item::distance );
		assertSame( dg, dg.withMSTListener( ( p, c ) -> {
			sb.append( "linking " + p + " to " + c + "\n" );
		} ) );
		items.forEach( dg::add );

		assertEquals( ""
				+ "5\n"
				+ "├3\n"
				+ "│└2\n"
				+ "└8\n"
				+ " └13", dg.minimumSpanningTree( items.get( 2 ) ).toString() );
		assertEquals( ""
				+ "linking 5 to 3\n"
				+ "linking 3 to 2\n"
				+ "linking 5 to 8\n"
				+ "linking 8 to 13", sb.toString().trim() );
	}
}
