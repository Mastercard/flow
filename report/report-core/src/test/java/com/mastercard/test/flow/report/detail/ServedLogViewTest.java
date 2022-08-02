package com.mastercard.test.flow.report.detail;

/**
 * Exercises the flow message data on an http-served report
 */
class ServedLogViewTest extends AbstractLogViewTest {
	/***/
	ServedLogViewTest() {
		super( report.url() );
	}
}
