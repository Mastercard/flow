package com.mastercard.test.flow.report.duct;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class DuctTest {

	@Test
	void serve() {
		Duct.serve( Paths.get( "../../example/app-itest/target/mctf/latest" ) );
	}
}
