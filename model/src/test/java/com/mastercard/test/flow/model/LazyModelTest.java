package com.mastercard.test.flow.model;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.model.mock.Flw;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.util.Tags;

/**
 * Execising {@link LazyModel}
 */
@SuppressWarnings("static-method")
class LazyModelTest {
	/**
	 * A model with no dependencies
	 */
	public static class NoDeps extends EagerModel {
		/**
		 * The tags for {@link Flow}s in this model
		 */
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "no_deps" ).union( "a" );

		/***/
		public NoDeps() {
			super( MODEL_TAGS );
			members( flatten(
					new Flw( "flow_a", "no_deps" ),
					new Flw( "flow_b", "no_deps", "a" ) ) );
		}
	}

	/**
	 * A model with one dependency
	 */
	public static class Deps extends EagerModel {
		/**
		 * The tags for {@link Flow}s in this model
		 */
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "deps" ).union( "b" );

		/**
		 * @param nd the model whose {@link Flow}s we need to build ours
		 */
		public Deps( NoDeps nd ) {
			super( MODEL_TAGS );
			members( flatten(
					new Flw( "flow_c", "deps" ),
					new Flw( "flow_d", "deps", "b" ) ) );
		}
	}

	/**
	 * A model with a transitive dependency
	 */
	public static class Transitive extends EagerModel {
		/**
		 * The tags for {@link Flow}s in this model
		 */
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "transitive" ).union( "c" );

		/**
		 * @param nd the model whose {@link Flow}s we need to build ours
		 */
		public Transitive( Deps nd ) {
			super( MODEL_TAGS );
			members( flatten(
					new Flw( "flow_e", "transitive" ),
					new Flw( "flow_f", "transitive", "c" ) ) );
		}
	}

	/**
	 * A model that fails on construction
	 */
	public static class Failure extends EagerModel {
		/**
		 * No flows, so no tags
		 */
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup();

		/***/
		public Failure() {
			super( MODEL_TAGS );
			throw new IllegalStateException( "no thanks!" );
		}
	}

	/**
	 * A model with two constructors. This will fail on registration with the
	 * {@link LazyModel}
	 */
	public static class TwoConstructor extends EagerModel {
		/***/
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup();

		/***/
		public TwoConstructor() {
			super( MODEL_TAGS );
		}

		/**
		 * @param m of no account
		 */
		public TwoConstructor( Model m ) {
			super( MODEL_TAGS );
		}
	}

	/**
	 * A model with a non-model argument to its constructor. This will fail on
	 * registration with the {@link LazyModel}
	 */
	public static class BadConstructor extends EagerModel {

		/***/
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup();

		/**
		 * @param b what should we supply?
		 */
		public BadConstructor( boolean b ) {
			super( MODEL_TAGS );
		}
	}

	/**
	 * Default and custom titles
	 */
	@Test
	void title() {
		assertEquals( "LazyModel", new LazyModel().title() );
		assertEquals( "custom title", new LazyModel( "custom title" ).title() );
	}

	/**
	 * Submodels are constructed and reported
	 */
	@Test
	void submodels() {
		LazyModel lm = new LazyModel()
				.with( NoDeps.class )
				.with( Deps.class )
				.with( Transitive.class );

		BuildListener bl = new BuildListener();
		lm.withListener( bl );

		// request a flow to cause a single submodel to be built
		assertEquals( 1, lm.flows( Collections.singleton( "a" ), Collections.emptySet() ).count() );

		assertEquals( ""
				+ "start NoDeps\n"
				+ "count NoDeps 0 2\n"
				+ "end NoDeps", bl.toString() );

		bl.clear();

		// iterate submodels
		assertEquals( "Deps, NoDeps, Transitive",
				lm.subModels()
						.map( Model::title )
						.collect( joining( ", " ) ) );

		// show that only the submodels that hadn't been built before have been built by
		// iterating the submodels
		assertEquals( ""
				+ "start Deps\n"
				+ "count Deps 0 2\n"
				+ "end Deps\n"
				+ "start Transitive\n"
				+ "count Transitive 0 2\n"
				+ "end Transitive", bl.toString() );
	}

	/**
	 * Simplest case - one submodel, no dependencies
	 */
	@Test
	void noDeps() {
		Model m = new LazyModel().with( NoDeps.class );

		// no chance of tag match, no build
		assertModel( m, Tags.tags( "nomatch" ), Tags.tags(),
				"∩[no_deps]⋃[a, no_deps]", "", "" );

		// tag match, build
		assertModel( m, Tags.tags( "a" ), Tags.tags(),
				"∩[no_deps]⋃[a, no_deps]",
				""
						+ "flow_b [a, no_deps]",
				""
						+ "start NoDeps\n"
						+ "count NoDeps 0 2\n"
						+ "end NoDeps" );

		// submodel already built, no need to build again
		assertModel( m, Tags.tags(), Tags.tags(),
				"∩[no_deps]⋃[a, no_deps]",
				""
						+ "flow_a [no_deps]\n"
						+ "flow_b [a, no_deps]",
				"count NoDeps 0 2" );
	}

	/**
	 * Two dependent submodels
	 */
	@Test
	void deps() {
		Model m = new LazyModel().with( NoDeps.class, Deps.class );

		// tag match, build
		assertModel( m, Tags.tags( "b" ), Tags.tags(),
				"∩[]⋃[a, b, deps, no_deps]",
				""
						+ "flow_d [b, deps]",
				""
						+ "start Deps\n"
						+ "start NoDeps\n"
						+ "count NoDeps 0 2\n"
						+ "end NoDeps\n"
						+ "count Deps 0 2\n"
						+ "end Deps" );

		// submodel already built, no need to build again
		assertModel( m, Tags.tags(), Tags.tags(),
				"∩[]⋃[a, b, deps, no_deps]",
				""
						+ "flow_a [no_deps]\n"
						+ "flow_b [a, no_deps]\n"
						+ "flow_c [deps]\n"
						+ "flow_d [b, deps]",
				""
						+ "count Deps 0 2\n"
						+ "count NoDeps 0 2" );
	}

	/**
	 * Three dependent submodels
	 */
	@Test
	void transitive() {
		Model m = new LazyModel().with( NoDeps.class, Deps.class, Transitive.class );

		// tag match, build
		assertModel( m, Tags.tags( "c" ), Tags.tags(),
				"∩[]⋃[a, b, c, deps, no_deps, transitive]",
				""
						+ "flow_f [c, transitive]",
				""
						+ "start Transitive\n"
						+ "start Deps\n"
						+ "start NoDeps\n"
						+ "count NoDeps 0 2\n"
						+ "end NoDeps\n"
						+ "count Deps 0 2\n"
						+ "end Deps\n"
						+ "count Transitive 0 2\n"
						+ "end Transitive" );
	}

	/**
	 * Exercising regsitration failure - dependencies must be registered before
	 * their dependents
	 */
	@Test
	void registrationOrder() {
		LazyModel m = new LazyModel();
		String e = assertThrows( IllegalArgumentException.class,
				() -> m.with( Deps.class, NoDeps.class ) ).getMessage();
		assertEquals(
				"Unsatisfied dependencies [class com.mastercard.test.flow.model.LazyModelTest$NoDeps] for "
						+ "public com.mastercard.test.flow.model.LazyModelTest$Deps(com.mastercard.test.flow.model.LazyModelTest$NoDeps)",
				e );
	}

	/**
	 * Exercising instantiation failure
	 */
	@Test
	void buildFailure() {
		// flow streams are evaluated lazily
		Stream<Flow> explodingFlow = new LazyModel().with( Failure.class ).flows();

		// the exception in Failure constructor only triggers when the stream is
		// evaluated
		assertThrows( IllegalStateException.class,
				() -> explodingFlow.count() );
	}

	/**
	 * Exercising constructor validation
	 */
	@Test
	void constructors() {
		LazyModel m = new LazyModel();
		assertThrows( IllegalArgumentException.class,
				() -> m.with( TwoConstructor.class ) );

		assertThrows( IllegalArgumentException.class,
				() -> m.with( BadConstructor.class ) );
	}

	private static void assertModel( Model m,
			Set<String> include, Set<String> exclude,
			String tags, String flows, String builds ) {
		BuildListener bl = new BuildListener();
		assertSame( m, m.withListener( bl ) );

		assertEquals( tags, m.tags().toString() );
		assertEquals( "", bl.buildEvents(), "tag access does not build models" );
		assertEquals( flows, m.flows( include, exclude )
				.map( String::valueOf )
				.sorted()
				.collect( joining( "\n" ) ) );
		assertEquals( builds, bl.toString() );
	}

	/**
	 * Keeps track of submodel build events
	 */
	static class BuildListener implements Model.Listener {

		private List<String> buildEvents = new ArrayList<>();

		/**
		 * Clears the current set of build events
		 *
		 * @return <code>this</code>
		 */
		public BuildListener clear() {
			buildEvents.clear();
			return this;
		}

		@Override
		public void start( Class<? extends Model> type ) {
			buildEvents.add( "start " + type.getSimpleName() );
		}

		@Override
		public void end( Model instance ) {
			buildEvents.add( "end " + instance.getClass().getSimpleName() );
		}

		@Override
		public void count( Model instance, int models, int flows ) {
			buildEvents.add( String.format( "count %s %s %s",
					instance.getClass().getSimpleName(), models, flows ) );
		}

		@Override
		public String toString() {
			return buildEvents.stream().collect( joining( "\n" ) );
		}

		/**
		 * @return A string of only model-build events that have been collected by this
		 *         listener
		 */
		public String buildEvents() {
			return buildEvents.stream()
					.filter( e -> !e.startsWith( "count" ) )
					.collect( joining( "\n" ) );
		}

	}
}
