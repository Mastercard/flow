package com.mastercard.test.flow.report.duct;

import java.nio.file.Paths;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Duct}
 */
class DuctTest {

	@Test
	@Disabled
	void serve() {
		Duct.serve( Paths.get( "../../example/app-itest/target/mctf/latest" ) );
	}
}
