package com.mastercard.test.flow.model;

import static com.mastercard.test.flow.util.Tags.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.model.mock.Flw;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercising {@link EagerModel}
 */
@SuppressWarnings("static-method")
class EagerModelTest {

	/**
	 * Well-formed model with tags
	 */
	static class TaggedModel extends EagerModel {
		/** Accessed reflectively */
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "c" )
				.union( "a", "b", "d", "e" );

		private static final Flow abc = new Flw( "abc", "a", "b", "c" );
		private static final Flow bcd = new Flw( "bcd", "b", "c", "d" );
		private static final Flow cde = new Flw( "cde", "c", "d", "e" );

		/***/
		TaggedModel() {
			super( "tagged", MODEL_TAGS );
			members( flatten( abc, bcd, cde ) );
		}
	}

	/**
	 * Title is returned as expected
	 */
	@Test
	void title() {
		assertEquals( "tagged", new TaggedModel().title() );
	}

	/**
	 * no submodels
	 */
	@Test
	void submodels() {
		assertEquals( 0, new TaggedModel().subModels().count() );
	}

	/**
	 * Exercises tag collection
	 */
	@Test
	void tags() {
		assertEquals( "∩[c]⋃[a, b, c, d, e]",
				new TaggedModel().tags().toString() );
	}

	/**
	 * Exercises tagged retrieval
	 */
	@Test
	void flows() {
		Model m = new TaggedModel();
		{
			BiConsumer<Set<String>, String> inclusion = ( in, out ) -> Assertions
					.assertEquals( out, m.flows( in, empty() )
							.map( f -> f.meta().description() )
							.collect( Collectors.toCollection( TreeSet::new ) )
							.toString() );
			inclusion.accept( Tags.tags(), "[abc, bcd, cde]" );
			inclusion.accept( Tags.tags( "a" ), "[abc]" );
			inclusion.accept( Tags.tags( "b" ), "[abc, bcd]" );
			inclusion.accept( Tags.tags( "c" ), "[abc, bcd, cde]" );
			inclusion.accept( Tags.tags( "d" ), "[bcd, cde]" );
			inclusion.accept( Tags.tags( "e" ), "[cde]" );
			inclusion.accept( Tags.tags( "b", "c" ), "[abc, bcd]" );
			inclusion.accept( Tags.tags( "c", "d" ), "[bcd, cde]" );
			inclusion.accept( Tags.tags( "a", "e" ), "[]" );
		}
		{
			BiConsumer<Set<String>, String> exclusion = ( in, out ) -> Assertions
					.assertEquals( out, m.flows( empty(), in )
							.map( f -> f.meta().description() )
							.collect( Collectors.toCollection( TreeSet::new ) )
							.toString() );
			exclusion.accept( Tags.tags(), "[abc, bcd, cde]" );
			exclusion.accept( Tags.tags( "a" ), "[bcd, cde]" );
			exclusion.accept( Tags.tags( "b" ), "[cde]" );
			exclusion.accept( Tags.tags( "c" ), "[]" );
			exclusion.accept( Tags.tags( "d" ), "[abc]" );
			exclusion.accept( Tags.tags( "e" ), "[abc, bcd]" );
			exclusion.accept( Tags.tags( "b", "c" ), "[]" );
			exclusion.accept( Tags.tags( "c", "d" ), "[]" );
			exclusion.accept( Tags.tags( "a", "e" ), "[bcd]" );
		}
	}

	/**
	 * Well-formed model with no tags
	 */
	static class LumpyModel extends EagerModel {
		private static final Flow a = new Flw( "a" );
		private static final Flow b = new Flw( "b" );
		private static final Flow c = new Flw( "b" );
		private static final Flow d = new Flw( "d" );
		private static final Flow e = new Flw( "e" );
		private static final Flow f = new Flw( "f" );

		/***/
		LumpyModel() {
			super( "lumpy", new TaggedGroup() );
			members( flatten( a, Stream.of( b, c ), d, Arrays.asList( e, f ) ) );
		}
	}

	/**
	 * Exercises flattening a lumpy list of {@link Flows}
	 */
	@Test
	void flatten() {
		assertEquals( "[a, b, d, e, f]", new LumpyModel().flows().map( f -> f.meta().description() )
				.collect( Collectors.toCollection( TreeSet::new ) ).toString() );
	}

	/**
	 * A model that call members twice. This is forbidden
	 */
	private static class DoubleMemberModel extends EagerModel {
		/** used reflectively */
		public static final TaggedGroup MODEL_TAGS = null;

		DoubleMemberModel() {
			super( "title", MODEL_TAGS );
			members( flatten( new Flw( "a" ) ) );
			members( flatten( new Flw( "b" ) ) );
		}
	}

	/**
	 * Shows that {@link EagerModel} does not tolerate attempts to set the
	 * membership twice
	 */
	@Test
	void doubleMembers() {
		IllegalStateException e = Assertions.assertThrows(
				IllegalStateException.class,
				DoubleMemberModel::new );
		assertEquals( "InstanceModel members must be set exactly once", e.getMessage() );
	}

	private static class EmptyModel extends EagerModel {
		EmptyModel() {
			super( "title", new TaggedGroup() );
		}
	}

	/**
	 * Shows that {@link EagerModel} does not tolerate attempts to set the
	 * membership zero times either
	 */
	@Test
	void emptyModel() {
		EmptyModel m = new EmptyModel();
		IllegalStateException e = Assertions.assertThrows( IllegalStateException.class,
				() -> m.flows() );
		assertEquals( ""
				+ "No flows registered in class com.mastercard.test.flow.model.EagerModelTest$EmptyModel."
				+ " Have you called members() in the constructor?",
				e.getMessage() );
	}

	/**
	 * SHows that the
	 * {@link EagerModel#withListener(com.mastercard.test.flow.Model.Listener)}
	 * returns the same instance
	 */
	@Test
	void withListener() {
		Model m = new TaggedModel();
		Model r = m.withListener( null );
		assertSame( m, r );
	}

	/**
	 * Exercising accessing the assumed TAGS field
	 */
	@Test
	void typeTags() {
		assertEquals( "∩[c]⋃[a, b, c, d, e]",
				EagerModel.typeTags( TaggedModel.class ).toString() );

		assertThrows( IllegalArgumentException.class,
				() -> EagerModel.typeTags( LumpyModel.class ) );

		assertThrows( IllegalArgumentException.class,
				() -> EagerModel.typeTags( DoubleMemberModel.class ) );
	}
}
