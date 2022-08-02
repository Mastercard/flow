package com.mastercard.test.flow.report.detail;

/**
 * Exercises the flow message data on an http-served report
 */
class FileLogViewTest extends AbstractLogViewTest {
	/***/
	FileLogViewTest() {
		super( report.fileUrl() );
	}
}
