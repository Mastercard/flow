package com.mastercard.test.flow.report.detail;

/**
 * Exercises the context tab on a file-served report
 */
class FileContextTest extends AbstractContextTest {

	/***/
	FileContextTest() {
		super( report.fileUrl() );
	}
}
