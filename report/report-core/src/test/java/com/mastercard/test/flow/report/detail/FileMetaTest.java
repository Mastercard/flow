package com.mastercard.test.flow.report.detail;

/**
 * Exercises the flow metadata on an file-served report
 */
class FileMetaTest extends AbstractMetaTest {
	/***/
	FileMetaTest() {
		super( report.fileUrl() );
	}
}
