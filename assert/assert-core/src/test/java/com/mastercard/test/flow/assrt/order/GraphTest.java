package com.mastercard.test.flow.assrt.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Exercising {@link Graph}
 */
@SuppressWarnings("static-method")
class GraphTest {

	/**
	 * Simplest case - no edges in the graph, order is defined by supplied
	 * suggestion
	 */
	@Test
	void unconnected() {
		{
			Graph<String> lex = new Graph<>( String::compareTo )
					.with( "e" ).with( "d" ).with( "c" ).with( "d" ).with( "a" );
			assertEquals( "[a, c, d, e]", lex.order().toString() );
		}
		{
			Graph<String> rev = new Graph<String>( ( a, b ) -> b.compareTo( a ) )
					.with( "e" ).with( "d" ).with( "c" ).with( "d" ).with( "a" );
			assertEquals( "[e, d, c, a]", rev.order().toString() );
		}
	}

	/**
	 * A single edge
	 */
	@Test
	void edge() {
		Graph<String> g = new Graph<>( String::compareTo );
		Stream.of( "a", "b", "c", "d" ).forEach( g::with );
		g.edge( 1, "a", "d" );
		assertEquals( "[d, a, b, c]", g.order().toString() );
	}

	/**
	 * Edges that hit nonexistent nodes are ignored
	 */
	@Test
	void badEdges() {
		Graph<String> g = new Graph<>( String::compareTo );
		Stream.of( "a", "b", "c", "d" ).forEach( g::with );
		g.edge( 1, "a", "e" ).edge( 1, "d", "f" );
		assertEquals( ""
				+ "a\n"
				+ "b\n"
				+ "c\n"
				+ "d",
				g.toString() );
		assertEquals( "[a, b, c, d]", g.order().toString() );
	}

	/**
	 * Multiple disconnected edges
	 */
	@Test
	void edges() {
		Graph<String> g = new Graph<>( String::compareTo );
		Stream.of( "a", "b", "c", "d" ).forEach( g::with );
		g.edge( 1, "a", "d" )
				.edge( 1, "b", "c" );
		assertEquals( "[d, a, c, b]", g.order().toString() );
	}

	/**
	 * Exercises the ordering of dependencies with equal rank
	 */
	@Test
	void equalDeps() {
		Graph<String> g = new Graph<>( String::compareTo );
		Stream.of( "a", "b", "c" ).forEach( g::with );
		g.edge( 1, "a", "b" );
		g.edge( 1, "a", "c" );
		assertEquals( "[b, c, a]", g.order().toString() );
	}

	/**
	 * Edge chain
	 */
	@Test
	void transitive() {
		Graph<String> g = new Graph<>( String::compareTo );
		Stream.of( "a", "b", "c", "d" ).forEach( g::with );
		g.edge( 1, "a", "b" )
				.edge( 1, "b", "c" );
		assertEquals( "[c, b, a, d]", g.order().toString() );
	}

	/**
	 * Graph contains cycles
	 */
	@Test
	void cycles() {
		{
			Graph<String> g = new Graph<>( String::compareTo );
			Stream.of( "a", "b", "c", "d" ).forEach( g::with );
			g.edge( 1, "a", "b" )
					.edge( 1, "b", "c" )
					.edge( 1, "c", "d" )
					.edge( 0, "d", "a" );
			assertEquals( ""
					+ "a\n"
					+ "	a-1->b\n"
					+ "b\n"
					+ "	b-1->c\n"
					+ "c\n"
					+ "	c-1->d\n"
					+ "d\n"
					+ "	d-0->a", // this edge will be broken
					g.toString() );
			assertEquals( "[d, c, b, a]", g.order().toString() );
			assertEquals( ""
					+ "a\n"
					+ "	a-1->b\n"
					+ "b\n"
					+ "	b-1->c\n"
					+ "c\n"
					+ "	c-1->d\n"
					+ "d", // this edge will be broken
					g.toString() );
		}
		{
			Graph<String> g = new Graph<>( String::compareTo );
			Stream.of( "a", "b", "c", "d" ).forEach( g::with );
			g.edge( 1, "a", "b" )
					.edge( 0, "b", "c" )
					.edge( 1, "c", "d" )
					.edge( 1, "d", "a" );
			assertEquals( ""
					+ "a\n"
					+ "	a-1->b\n"
					+ "b\n"
					+ "	b-0->c\n" // this edge will be broken
					+ "c\n"
					+ "	c-1->d\n"
					+ "d\n"
					+ "	d-1->a",
					g.toString() );
			assertEquals( "[b, a, d, c]", g.order().toString() );
			assertEquals( ""
					+ "a\n"
					+ "	a-1->b\n"
					+ "b\n"
					+ "c\n"
					+ "	c-1->d\n"
					+ "d\n"
					+ "	d-1->a",
					g.toString() );
		}
		{
			Graph<String> g = new Graph<>( String::compareTo );
			Stream.of( "a", "b" ).forEach( g::with );
			g.edge( 1, "a", "b" )
					.edge( 1, "b", "a" );
			assertEquals( ""
					+ "a\n"
					+ "	a-1->b\n" // it's an arbitrary choice, but this edge is found first
					+ "b\n"
					+ "	b-1->a",
					g.toString() );
			assertEquals( "[a, b]", g.order().toString() );
			assertEquals( ""
					+ "a\n"
					+ "b\n"
					+ "	b-1->a",
					g.toString() );
		}
	}
}
