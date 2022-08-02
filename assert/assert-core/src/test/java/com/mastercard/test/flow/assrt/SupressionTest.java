package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.Reporting.NEVER;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;

/**
 * Demonstrates the effects of the various behaviour suppression {@link Options}
 */
@SuppressWarnings("static-method")
class SupressionTest {

	/**
	 * Shows that when assertion failures are suppressed we compare both messages
	 * rather than aborting after the first failure
	 */
	@Test
	void assertionFailure() {
		TestFlocessor tf = new TestFlocessor( "unreportedFailure", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( NEVER )
				.behaviour( assrt -> {
					assrt.actual()
							.request( "unexpected req content!".getBytes( UTF_8 ) )
							.response( "unexpected res content!".getBytes( UTF_8 ) );
				} );

		try {
			Options.SUPPRESS_ASSERTION_FAILURE.set( "true" );
			tf.execute();
		}
		finally {
			Options.SUPPRESS_ASSERTION_FAILURE.clear();
		}

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] request",
				" | A request to B | unexpected req content! |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | unexpected res content! |" ),
				copypasta( tf.events() ),
				"Both messages are asserted" );
	}

	/**
	 * Shows that flows with unmet system dependencies are processed if that check
	 * is suppressed
	 */
	@Test
	void missingImplicit() {
		TestFlocessor tf = new TestFlocessor( "missingImplicit", TestModel.abcWithImplicit() )
				.system( State.FUL, B )
				.reporting( NEVER )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		try {
			Options.SUPPRESS_SYSTEM_CHECK.set( "true" );
			tf.execute();
		}
		finally {
			Options.SUPPRESS_SYSTEM_CHECK.clear();
		}

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abcWithImplicit(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ),
				"Flow is processed despite not being supported by the system" );
	}

	/**
	 * Shows that flows where the basis has failed will not be skipped if that check
	 * is suppressed
	 */
	@Test
	void failedBasis() {
		TestFlocessor tf = new TestFlocessor( "failedBasis", TestModel.abcWithChild() )
				.system( State.FUL, B )
				.reporting( NEVER )
				.behaviour( assrt -> {
					assrt.actual().response( "fail!".getBytes( UTF_8 ) );
				} );

		try {
			Options.SUPPRESS_BASIS_CHECK.set( "true" );
			tf.execute();
		}
		finally {
			Options.SUPPRESS_BASIS_CHECK.clear();
		}

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abcWithChild(TestModel.java:_) A->B [] response",
				" | B response to A | fail! |",
				"",
				"COMPARE child []",
				"com.mastercard.test.flow.assrt.TestModel.abcWithChild(TestModel.java:_) A->B [] response",
				" | B response to A | fail! |" ),
				copypasta( tf.events() ),
				"The child flow is processed" );
	}

	/**
	 * Shows that flows where the dependency has failed will not be skipped if that
	 * check is suppressed
	 */
	@Test
	void failedDependency() {
		TestFlocessor tf = new TestFlocessor( "failedDependency", TestModel.abcWithDependency() )
				.system( State.FUL, B )
				.reporting( NEVER )
				.behaviour( assrt -> {
					throw new RuntimeException( "kaboom!" );
				} );

		try {
			Options.SUPPRESS_DEPENDENCY_CHECK.set( "true" );
			tf.execute();
		}
		finally {
			Options.SUPPRESS_DEPENDENCY_CHECK.clear();
		}

		assertEquals( copypasta(
				"dependency [] error kaboom!",
				"dependent [] error kaboom!" ),
				copypasta( tf.events() ),
				"both flows are processed" );
	}
}
