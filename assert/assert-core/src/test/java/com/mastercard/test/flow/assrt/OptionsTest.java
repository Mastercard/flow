package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ensures that the table of system properties in the readme is accurate
 */
@SuppressWarnings("static-method")
class OptionsTest {
	/**
	 * Exercises property access
	 */
	@Test
	void access() {
		assertEquals( "mctf.report.name",
				Options.REPORT_NAME.property() );
		assertEquals( "The path from the artifact directory to the report destination",
				Options.REPORT_NAME.description() );
		assertEquals( null, Options.REPORT_NAME.defaultValue() );
	}

	/**
	 * We want a common prefix on all our properties
	 */
	@Test
	void prefix() {
		for( Options op : Options.values() ) {
			assertTrue( op.property().startsWith( "mctf." ), "bad prefix on " + op );
		}
	}

	/**
	 * Maven is detected
	 */
	@Test
	void artifactDir() {
		assertEquals( "target/mctf", Options.ARTIFACT_DIR.value() );
	}
}
