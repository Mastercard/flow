package com.mastercard.test.flow.validation.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Exercises {@link ReflectiveModelTaggingCheck}
 */
@SuppressWarnings("unused")
class ReflectiveModelTaggingCheckTest extends AbstractValidationTest {

	/***/
	ReflectiveModelTaggingCheckTest() {
		super( new ReflectiveModelTaggingCheck(), "Reflective model tagging",
				"Models that offer reflective tagging information do so accurately" );
	}

	private static class UnTagged extends Branch {
		public UnTagged() {
			super( new TaggedGroup( "no", "tags", "field" ) );
		}
	}

	/**
	 * Base case - model that does not have a MODEL_TAGS field, so no check is
	 * performed.
	 */
	@Test
	void untagged() {
		test( new UnTagged() );
	}

	private static class Valid extends Branch {
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "valid" );

		public Valid() {
			super( MODEL_TAGS );
		}
	}

	/**
	 * Correctly-implemented model. A check success is reported
	 */
	@Test
	void valid() {
		test( new Valid(),
				"Valid : pass" );
	}

	private static class Inaccurate extends Branch {
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "foo" );

		public Inaccurate() {
			super( new TaggedGroup( "bar" ) );
		}
	}

	/**
	 * A model that reports different tags reflectively and via method call, so a
	 * failure is raised
	 */
	@Test
	void inaccurate() {
		test( new Inaccurate(),
				"  details: com.mastercard.test.flow.validation.check.ReflectiveModelTaggingCheckTest$Inaccurate.tags() should just return MODEL_TAGS\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: " );
	}

	private static class NonIdentity extends Branch {
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "foo" );

		public NonIdentity() {
			super( new TaggedGroup( "foo" ) );
		}
	}

	/**
	 * A model that reports the same tags but not the same instance, so a failure is
	 * raised.
	 */
	@Test
	void nonIdentity() {
		test( new NonIdentity(),
				"  details: com.mastercard.test.flow.validation.check.ReflectiveModelTaggingCheckTest$NonIdentity.tags() should just return MODEL_TAGS\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: " );
	}

	private static class Access extends Branch {
		private static final TaggedGroup MODEL_TAGS = new TaggedGroup( "access" );

		public Access() {
			super( MODEL_TAGS );
		}
	}

	/**
	 * MODEL_TAGS field has wrong accessibility, so no check is performed
	 */
	@Test
	void access() {
		test( new Access() );
	}

	private static class Instance extends Branch {
		public final TaggedGroup MODEL_TAGS = new TaggedGroup( "access" );

		public Instance() {
			super( null );
		}

		@Override
		public TaggedGroup tags() {
			return MODEL_TAGS;
		}
	}

	/**
	 * MODEL_TAGS field is not static, so no check is performed
	 */
	@Test
	void instance() {
		test( new Instance() );
	}

	private static class Mutable extends Branch {
		public static TaggedGroup MODEL_TAGS = new TaggedGroup( "mutable" );

		public Mutable() {
			super( MODEL_TAGS );
		}
	}

	/**
	 * MODEL_TAGS field is not final
	 */
	@Test
	void mutable() {
		test( new Mutable() );
	}

	private static class WrongType extends Branch {
		public static final String MODEL_TAGS = "wrong type";

		public WrongType() {
			super( new TaggedGroup( MODEL_TAGS ) );
		}

	}

	/**
	 * MODEL_TAGS field is not a {@link TaggedGroup}, so no check is performed
	 */
	@Test
	void wrongType() {
		test( new WrongType() );
	}

	private static class WrongName extends Branch {
		public static final TaggedGroup MOOOODEL_TEEEEGS = new TaggedGroup( "wrong name" );

		public WrongName() {
			super( MOOOODEL_TEEEEGS );
		}

	}

	/**
	 * The TaggedGroup field has an unexpected name, so no check is performed
	 */
	@Test
	void wrongName() {
		test( new WrongName() );
	}

	private static class AccessFailure extends Branch {
		public static final TaggedGroup MODEL_TAGS = new TaggedGroup( "access failure" );

		public AccessFailure() {
			super( MODEL_TAGS );
		}

		@Override
		public TaggedGroup tags() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * A failure is thrown while accessing the tags - this is a fatal validation
	 * error
	 */
	@Test
	void accessFailure() {
		Model m = new AccessFailure();
		IllegalStateException ise = assertThrows( IllegalStateException.class, () -> test( m ) );
		assertEquals( "Failed to access tags for class "
				+ "com.mastercard.test.flow.validation.check.ReflectiveModelTaggingCheckTest$AccessFailure",
				ise.getMessage() );
	}

	/**
	 * Spelunking down into a model tree
	 */
	@Test
	void recursive() {
		test( new Branch( new TaggedGroup( "root" ),
				new Branch( new TaggedGroup( "branch" ),
						new UnTagged(),
						new Valid(),
						new Branch( new TaggedGroup( "twig" ),
								new Access(),
								new Inaccurate() ) ),
				new NonIdentity() ),
				"Valid : pass",
				"  details: com.mastercard.test.flow.validation.check.ReflectiveModelTaggingCheckTest$Inaccurate.tags() should just return MODEL_TAGS\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: ",
				"  details: com.mastercard.test.flow.validation.check.ReflectiveModelTaggingCheckTest$NonIdentity.tags() should just return MODEL_TAGS\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: " );
	}

	/**
	 * A model with submodels
	 */
	private static class Branch implements Model {

		private final TaggedGroup tags;
		private final List<Model> subs = new ArrayList<>();

		/**
		 * @param tags model tags
		 * @param sm   submodels
		 */
		protected Branch( TaggedGroup tags, Model... sm ) {
			this.tags = tags;
			Collections.addAll( subs, sm );
		}

		@Override
		public String title() {
			return getClass().getSimpleName();
		}

		@Override
		public TaggedGroup tags() {
			return tags;
		}

		@Override
		public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Stream<Model> subModels() {
			return subs.stream();
		}

		@Override
		public Model listener( Listener l ) {
			throw new UnsupportedOperationException();
		}
	}
}
