package com.mastercard.test.flow.builder.mutable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.builder.concrete.ConcreteDependency;

/**
 * Exercising {@link MutableDependency}
 */
@SuppressWarnings("static-method")
class MutableDependencyTest {

	/**
	 * Execise fluent API
	 */
	@Test
	void fields() {
		Function<Object, Object> mut = String::valueOf;

		MutableDependency dep = new MutableDependency();

		MutableDependency ret = dep
				.source( s -> s.field( "source field" ) )
				.mutation( mut )
				.sink( s -> s.field( "sink field" ) );

		assertSame( dep, ret );
		ConcreteDependency built = dep.build( null );
		assertEquals( "source field", built.source().field() );
		assertEquals( "sink field", built.sink().field() );
		assertSame( mut, built.mutation() );
	}

	/**
	 * Exercise data inheritance
	 */
	@Test
	void inheritance() {
		Function<Object, Object> mut = String::valueOf;
		ConcreteDependency cd = new MutableDependency()
				.source( s -> s.field( "source field" ) )
				.mutation( mut )
				.sink( s -> s.field( "sink field" ) )
				.build( null );

		ConcreteDependency child = new MutableDependency( cd )
				.sink( s -> s.field( "child sink field" ) )
				.build( null );

		assertEquals( "source field", child.source().field() );
		assertEquals( "child sink field", child.sink().field() );
		assertSame( mut, child.mutation() );
	}
}
