package com.mastercard.test.flow.validation;

import static com.mastercard.test.flow.validation.AbstractValidator.defaultChecks;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
				+ "Model uniqueness\n"
				+ "Reflective model tagging\n"
				+ "Result tag misuse\n"
				+ "Trace uniqueness",
				tv.validations()
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

	/**
	 * Illustrates the effect of {@link AbstractValidator#batching(int)}
	 */
	@Test
	void batchedChecks() {
		TestValidator tv = new TestValidator()
				.with(
						validation( "empty", 0 ),
						validation( "single", 1 ),
						validation( "pair", 2 ),
						validation( "triple", 3 ),
						validation( "quad", 4 ) );

		BiConsumer<Integer, String> test = ( size, expected ) -> assertEquals(
				expected,
				tv.batching( size )
						.validations()
						.flatMap( tv::batchedChecks )
						.map( Check::name )
						.collect( joining( "\n" ) ),
				"batch size " + size );

		// batch sizes of 1 and lower disable batching behaviour
		IntStream.of( -1, 0, 1 )
				.forEach( i -> test.accept( i, ""
						+ "check pair [0]\n"
						+ "check pair [1]\n"
						+ "check quad [0]\n"
						+ "check quad [1]\n"
						+ "check quad [2]\n"
						+ "check quad [3]\n"
						+ "check single [0]\n"
						+ "check triple [0]\n"
						+ "check triple [1]\n"
						+ "check triple [2]" ) );

		test.accept( 2, ""
				+ "pair (1-2)\n"
				+ "quad (1-2)\n"
				+ "quad (3-4)\n"
				+ "check single [0]\n"
				+ "triple (1-2)\n"
				+ "check triple [2]" );

		test.accept( 3, ""
				+ "pair (1-2)\n"
				+ "quad (1-3)\n"
				+ "check quad [3]\n"
				+ "check single [0]\n"
				+ "triple (1-3)" );

		test.accept( 10, ""
				+ "pair (1-2)\n"
				+ "quad (1-4)\n"
				+ "check single [0]\n"
				+ "triple (1-3)" );
	}

	/**
	 * Shows the results of failing batched checks
	 */
	@Test
	void batchedFailures() {
		TestValidator tv = new TestValidator()
				.with( validation( "pent", 5, 2, 3 ) )
				.batching( 5 );

		assertEquals(
				"Failure on index 2",
				tv.validations()
						.flatMap( tv::batchedChecks )
						.map( Check::check )
						.filter( Optional::isPresent )
						.map( Optional::get )
						.findFirst()
						.map( Violation::details )
						.orElse( null ),
				"Checks 2 and 3 will both fail, but only the first violation is reported" );
	}

	private static Validation validation( String name, int count, int... failureIndices ) {
		return new Validation() {

			@Override
			public String name() {
				return name;
			}

			@Override
			public String explanation() {
				return String.format(
						"Produces %s checks, %s of which will fail",
						count, failureIndices.length );
			}

			@Override
			public Stream<Check> checks( Model model ) {
				Arrays.sort( failureIndices );
				return IntStream.range( 0, count )
						.mapToObj( i -> new Check(
								this,
								"check " + name + " [" + i + "]",
								() -> Arrays.binarySearch( failureIndices, i ) >= 0
										? new Violation( this, "Failure on index " + i )
										: null ) );
			}
		};
	}
}
