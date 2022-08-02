package com.mastercard.test.flow.validation.check;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Validation;

/**
 * Makes writing tests for {@link Validation} implementations a bit more
 * convenient
 */
abstract class AbstractValidationTest {

	private final Validation validation;

	private final String name, explanation;

	/**
	 * @param validation  The object under test
	 * @param name        The expected validation name
	 * @param explanation The expacted validation explanation
	 */
	protected AbstractValidationTest( Validation validation, String name, String explanation ) {
		this.validation = validation;
		this.name = name;
		this.explanation = explanation;
	}

	/**
	 * Checks that the {@link Validation#name()} is as expected
	 */
	@Test
	void name() {
		Assertions.assertEquals( name, validation.name() );
	}

	/**
	 * Checks that the {@link Validation#explanation()} is as expected
	 */
	@Test
	void explanation() {
		Assertions.assertEquals( explanation, validation.explanation() );
	}

	/**
	 * @param model              The model to run the validation against
	 * @param expectedViolations The expected results
	 */
	protected void test( Model model, String... expectedViolations ) {
		Assertions.assertEquals(
				copypasta( Stream.of( expectedViolations ) ),
				copypasta( validation.checks( model )
						.map( c -> {
							Assertions.assertSame( validation, c.validation() );
							return c.check()
									.map( v -> {
										Assertions.assertSame( validation, v.validation() );
										return String.format( ""
												+ "  details: %s\n"
												+ " expected: %s\n"
												+ "   actual: %s\n"
												+ "offenders: %s",
												v.details(),
												v.expected(),
												v.actual(),
												v.offenderString() );
									} )
									.orElse( c.name() + " : pass" );
						} ) ) );
	}

	private static String copypasta( Stream<String> content ) {
		return content
				.map( c -> Stream.of( c.split( "\n" ) )
						.map( line -> line.replaceAll( "\"", "\\\\\"" ) )
						.collect( Collectors.joining( "\\n\"\n+\"", "\"", "\"" ) ) )
				.collect( Collectors.joining( ",\n" ) );
	}
}
