package com.mastercard.test.flow.report.index;

import org.junit.jupiter.api.extension.ExtendWith;

import com.mastercard.test.flow.report.seq.Browser;

/**
 * Exercises the report index as browsed over http
 */
@ExtendWith(Browser.class)
class ServedIndexTest extends AbstractIndexTest {

	/***/
	ServedIndexTest() {
		super( report.url() );
	}
}
