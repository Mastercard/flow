package com.mastercard.test.flow.validation.junit5;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Illustrates how to validate a {@link Model} in a junit 5 test class. This
 * test will fail, so you don't want to run it normally. It is instead invoked
 * by {@link MetaTest}.
 */
@SuppressWarnings("static-method")
class ExampleTest {

	/**
	 * Set this to true if you want the test to do anything
	 */
	public static boolean active = false;

	/**
	 * Stops this fail-prone test from stinking up normal test runs
	 */
	@BeforeAll
	public static void activation() {
		Assumptions.assumeTrue( active, "Test activation" );
	}

	/**
	 * @return Test instances to exercise our fake model. One will pass, one will
	 *         fail.
	 */
	@TestFactory
	Stream<DynamicNode> checks() {
		return new Validator()
				.checking( mockModel() )
				.with( passing(), failing(), accepted() )
				.accepting( v -> "accept".equals( v.details() ) )
				.tests();
	}

	private static Model mockModel() {
		return new Model() {

			@Override
			public String title() {
				return "Mock model";
			}

			@Override
			public TaggedGroup tags() {
				return new TaggedGroup();
			}

			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return Stream.empty();
			}

			@Override
			public Stream<Model> subModels() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Model withListener( Listener l ) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private static Validation passing() {
		return new Validation() {

			@Override
			public String name() {
				return "Passing";
			}

			@Override
			public String explanation() {
				return "This validation always passes";
			}

			@Override
			public Stream<Check> checks( Model model ) {
				return Stream.of( new Check( this, "pass", () -> null ) );
			}
		};
	}

	private static Validation failing() {
		return new Validation() {

			@Override
			public String name() {
				return "Failing";
			}

			@Override
			public String explanation() {
				return "This validation always fails";
			}

			@Override
			public Stream<Check> checks( Model model ) {
				return Stream.of(
						new Check( this, "fail",
								() -> new Violation( this, "fail", null, null ) ),
						new Check( this, "cmp fail",
								() -> new Violation( this, "fail", "expect", "actual" ) ) );
			}
		};
	}

	private static Validation accepted() {
		return new Validation() {

			@Override
			public String name() {
				return "Accepted";
			}

			@Override
			public String explanation() {
				return "This validation always fails, but is accepted anyway";
			}

			@Override
			public Stream<Check> checks( Model model ) {
				return Stream.of(
						new Check( this, "accept",
								() -> new Violation( this, "accept", null, null ) ) );
			}
		};
	}
}
