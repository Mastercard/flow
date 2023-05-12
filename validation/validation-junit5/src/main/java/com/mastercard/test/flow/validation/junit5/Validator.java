package com.mastercard.test.flow.validation.junit5;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.AbstractValidator;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;

/**
 * Validates a {@link Model} using JUnit5. This should be used as the source of
 * test cases in a <a href=
 * "https://junit.org/junit5/docs/current/user-guide/#writing-tests-dynamic-tests">dynamic
 * test</a>, e.g.:
 *
 * <pre>
 * &#64;TestFactory
 * Stream&lt;DynamicNode&gt; checks() {
 * 	return new Validator()
 * 			.checking( MY_SYSTEM_MODEL )
 * 			.with( AbstractValidator.defaultChecks() )
 * 			.tests();
 * }
 * </pre>
 */
public class Validator extends AbstractValidator<Validator> {

	/**
	 * Call this as the return from your {@link TestFactory} method
	 *
	 * @return A stream of test cases that validate the model
	 */
	public Stream<DynamicNode> tests() {
		return validations()
				.map( this::container );
	}

	private DynamicContainer container( Validation validation ) {
		return DynamicContainer.dynamicContainer(
				validation.name(),
				batchedChecks( validation )
						.map( this::test ) );
	}

	private DynamicTest test( Check check ) {
		return dynamicTest(
				check.name(),
				() -> check.check()
						.filter( v -> !accepted( v ) )
						.ifPresent( violation -> {
							String message = String.format(
									"%s\n%s\n%s",
									check.validation().explanation(),
									violation.details(),
									violation.offenderString() );
							if( violation.expected() != null || violation.actual() != null ) {
								Assertions.assertEquals(
										violation.expected(),
										violation.actual(),
										message );
							}
							else {
								Assertions.fail( message );
							}
						} ) );
	}
}
