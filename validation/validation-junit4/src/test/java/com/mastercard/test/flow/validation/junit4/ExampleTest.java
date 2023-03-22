package com.mastercard.test.flow.validation.junit4;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Illustrates how to validate a {@link Model} in a junit 4 test class. This
 * test will fail, so you don't want to run it normally. It is instead invoked
 * by {@link MetaTest}.
 */
@RunWith(Parameterized.class)
public class ExampleTest {

	/**
	 * Set this to true if you want the test to do anything
	 */
	public static boolean active = false;

	/**
	 * Stops this fail-prone test from stinking up normal test runs
	 */
	@BeforeClass
	public static void activation() {
		Assume.assumeTrue( "Test activation", active );
	}

	/**
	 * @return The validation check parameters
	 */
	@Parameters(name = "{0}")
	public static Collection<Object[]> flows() {
		return new Validator()
				.checking( mockModel() )
				.with( passing(), failing(), accepted() )
				.accepting( v -> "accept".equals( v.details() ) )
				.parameters();
	}

	/**
	 * Human-readable name for the current test case
	 */
	@Parameter(0)
	public String name;

	/**
	 * The current validation check
	 */
	@Parameter(1)
	public Runnable check;

	/**
	 * Exercises the current validation check
	 */
	@Test
	public void test() {
		check.run();
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
			public Model listener( Listener l ) {
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
								() -> new Violation( this, "fail" ) ),
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
								() -> new Violation( this, "accept" ) ) );
			}
		};
	}
}
