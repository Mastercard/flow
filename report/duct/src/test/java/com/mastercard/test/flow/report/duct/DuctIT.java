package com.mastercard.test.flow.report.duct;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class DuctIT {

	@Test
	void duct() {
		Duct.serve( Paths.get( "../../example/app-itest/target/mctf/latest" ) );
	}
}
