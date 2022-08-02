package com.mastercard.test.flow.doc.quick;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.validation.AbstractValidator;
import com.mastercard.test.flow.validation.junit5.Validator;

/**
 * Trivial example of model validation.
 */
@SuppressWarnings("static-method")
class ValidationTest {

	/**
	 * @return test instances
	 */
	// snippet-start:validation
	@TestFactory
	Stream<DynamicNode> checks() {
		return new Validator()
				.checking( new Greetings() )
				.with( AbstractValidator.defaultChecks() )
				.tests();
	}
	// snippet-end:validation
}
