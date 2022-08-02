package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Actual}
 */
@SuppressWarnings("static-method")
class ActualTest {

	/**
	 * Shows that a defensive copy of accepted and provided data is taken, and that
	 * <code>null</code> is accepted
	 */
	@Test
	void request() {
		test( Actual::request, Actual::request );
	}

	/**
	 * Shows that a defensive copy of accepted and provided data is taken, and that
	 * <code>null</code> is accepted
	 */
	@Test
	void response() {
		test( Actual::response, Actual::response );
	}

	private static void test( BiConsumer<Actual, byte[]> set, Function<Actual, byte[]> get ) {
		Actual actual = new Actual();

		// populate data
		byte[] accepted = { 1 };
		set.accept( actual, accepted );

		// update the data we supplied
		accepted[0] = 0;

		// check that the data held does not reflect that change
		assertEquals( 1, get.apply( actual )[0] );

		// retrieve the held data
		byte[] produced = get.apply( actual );
		// update it
		produced[0] = 2;

		// check that the held data does not reflect that change
		assertEquals( 1, get.apply( actual )[0] );

		// check that null can be set and retrieved
		set.accept( actual, null );
		assertEquals( null, get.apply( actual ) );
	}

}
