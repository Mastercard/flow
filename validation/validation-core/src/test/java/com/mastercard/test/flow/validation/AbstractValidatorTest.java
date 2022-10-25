package com.mastercard.test.flow.validation;

import static com.mastercard.test.flow.validation.AbstractValidator.defaultChecks;
import static java.util.stream.Collectors.joining;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Model;

/**
 * Exercising basic functionality of the validator superclass
 */
@SuppressWarnings("static-method")
class AbstractValidatorTest {

	/**
	 * The superclass is abstract, so we need this
	 */
	static class TestValidator extends AbstractValidator<TestValidator> {
		// nowt
	}

	/**
	 * Exercises {@link Validation} inclusion and ordering
	 */
	@Test
	void with() {
		TestValidator tv = new TestValidator().with( defaultChecks() );
		Assertions.assertEquals( ""
				+ "Chain overlap\n"
				+ "Dependency chronology\n"
				+ "Dependency inclusion\n"
				+ "Dependency loop\n"
				+ "Flow Identity\n"
				+ "Interaction Identity\n"
				+ "Message sharing\n"
				+ "Model tagging\n"
				+ "Result tag misuse\n"
				+ "Trace uniqueness",
				tv.checks()
						.map( Validation::name )
						.collect( joining( "\n" ) ) );
	}

	/**
	 * Exercises {@link Model} accessor
	 */
	@Test
	void checking() {
		Model model = Mockito.mock( Model.class );
		TestValidator tv = new TestValidator().checking( model );
		Assertions.assertSame( model, tv.model() );
	}

	/**
	 * Exercises violation acceptance
	 */
	@Test
	void acceptance() {
		TestValidator tv = new TestValidator()
				.accepting( v -> v.details().contains( "minor" ) );

		Assertions.assertFalse( tv.accepted(
				new Violation( null, "gadzooks! this is a major problem!" ) ) );
		Assertions.assertTrue( tv.accepted(
				new Violation( null, "meh, this is a minor problem" ) ) );

	}
}
