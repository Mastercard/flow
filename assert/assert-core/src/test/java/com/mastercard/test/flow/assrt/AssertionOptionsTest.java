
package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ensures that the table of system properties in the readme is accurate
 */
@SuppressWarnings("static-method")
class AssertionOptionsTest {

	/**
	 * Exercises property access
	 */
	@Test
	void access() {
		assertEquals( "mctf.report.dir",
				AssertionOptions.REPORT_NAME.property() );
		assertEquals( "The path from the artifact directory to the report destination",
				AssertionOptions.REPORT_NAME.description() );
		assertEquals( null, AssertionOptions.REPORT_NAME.defaultValue() );
	}

	/**
	 * We want a common prefix on all our properties
	 */
	@Test
	void prefix() {
		for( AssertionOptions op : AssertionOptions.values() ) {
			assertTrue( op.property().startsWith( "mctf." ), "bad prefix on " + op );
		}
	}

	/**
	 * Maven is detected
	 */
	@Test
	void artifactDir() {
		assertEquals( "target/mctf", AssertionOptions.ARTIFACT_DIR.value() );
	}
}
