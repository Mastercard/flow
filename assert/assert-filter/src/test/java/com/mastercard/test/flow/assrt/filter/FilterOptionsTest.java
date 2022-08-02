package com.mastercard.test.flow.assrt.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ensures that the table of system properties in the readme is accurate
 */
@SuppressWarnings("static-method")
class FilterOptionsTest {

	/**
	 * Exercises property access
	 */
	@Test
	void access() {
		assertEquals( "mctf.filter.cli.min_width",
				FilterOptions.CLI_MIN_WIDTH.property() );
		assertEquals( "The minimum width of the command-line interface",
				FilterOptions.CLI_MIN_WIDTH.description() );
		assertEquals( "80", FilterOptions.CLI_MIN_WIDTH.defaultValue() );
		assertEquals( 80, FilterOptions.CLI_MIN_WIDTH.asInt() );
	}

	/**
	 * We want a common prefix on all our properties
	 */
	@Test
	void prefix() {
		for( FilterOptions op : FilterOptions.values() ) {
			assertTrue( op.property().startsWith( "mctf." ), "bad prefix on " + op );
		}
	}

	/**
	 * Maven is detected
	 */
	@Test
	void artifactDir() {
		assertEquals( "target/mctf", FilterOptions.ARTIFACT_DIR.value() );
	}
}
