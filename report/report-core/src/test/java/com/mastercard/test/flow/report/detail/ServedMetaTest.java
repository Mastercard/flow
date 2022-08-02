package com.mastercard.test.flow.report.detail;

/**
 * Exercises the flow metadata on an http-served report
 */
class ServedMetaTest extends AbstractMetaTest {
	/***/
	ServedMetaTest() {
		super( report.url() );
	}
}
