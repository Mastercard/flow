package com.mastercard.test.flow.model;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.model.LazyModelTest.BuildListener;
import com.mastercard.test.flow.model.LazyModelTest.Deps;
import com.mastercard.test.flow.model.LazyModelTest.NoDeps;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercsing {@link CombineModel}
 */
@SuppressWarnings("static-method")
class CombineModelTest {

	/**
	 * Default and custom model titles
	 */
	@Test
	void title() {
		assertEquals( "CombineModel", new CombineModel().title() );
		assertEquals( "custom title", new CombineModel( "custom title" ).title() );
	}

	/**
	 * Sub models reported as expected
	 */
	@Test
	void submodels() {
		CombineModel gm = new CombineModel().with(
				new EagerModelTest.TaggedModel(),
				new EagerModelTest.LumpyModel() );

		assertEquals( "tagged, lumpy",
				gm.subModels()
						.map( Model::title )
						.collect( Collectors.joining( ", " ) ) );
	}

	/**
	 * Tag combination
	 */
	@Test
	void tags() {
		CombineModel gm = new CombineModel();
		assertEquals( "∩[]⋃[]", gm.tags().toString() );

		gm.with( new EagerModelTest.TaggedModel() );
		assertEquals( "∩[c]⋃[a, b, c, d, e]", gm.tags().toString() );

		gm.with( new EagerModelTest.LumpyModel() );
		assertEquals( "∩[]⋃[a, b, c, d, e]", gm.tags().toString() );
	}

	/**
	 * Flow combination
	 */
	@Test
	void flows() {
		CombineModel gm = new CombineModel().with(
				new EagerModelTest.TaggedModel(),
				new EagerModelTest.LumpyModel() );

		assertEquals( ""
				+ "a\n"
				+ "abc\n"
				+ "b\n"
				+ "b\n"
				+ "bcd\n"
				+ "cde\n"
				+ "d\n"
				+ "e\n"
				+ "f",
				gm.flows()
						.map( f -> f.meta().description() )
						.sorted()
						.collect( joining( "\n" ) ) );
	}

	/**
	 * Construction reporting
	 */
	@Test
	void listener() {
		CombineModel gm = new CombineModel().with(
				new LazyModel().with(
						NoDeps.class,
						Deps.class ) );
		BuildListener bl = new BuildListener();
		Model ret = gm.withListener( bl );
		assertSame( gm, ret );

		assertEquals( ""
				+ "flow_a\n"
				+ "flow_c\n"
				+ "flow_d",
				gm.flows( Tags.tags(), Tags.tags( "a" ) )
						.map( f -> f.meta().description() )
						.sorted()
						.collect( joining( "\n" ) ) );

		assertEquals( ""
				+ "start Deps\n"
				+ "start NoDeps\n"
				+ "count NoDeps 0 2\n"
				+ "end NoDeps\n"
				+ "count Deps 0 2\n"
				+ "end Deps", bl.toString() );
	}
}
