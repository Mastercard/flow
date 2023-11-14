package com.mastercard.test.flow.autodoc;

/**
 * An abstraction away from a particular test library
 */
@FunctionalInterface
public interface Assert {

	/**
	 * Checks that the expected and actual values are identical
	 *
	 * @param expected    What the test expected
	 * @param actual      What the system produced
	 * @param description A human-readable description of what is being asserted
	 */
	void assertEquals( Object expected, Object actual, String description );
}
