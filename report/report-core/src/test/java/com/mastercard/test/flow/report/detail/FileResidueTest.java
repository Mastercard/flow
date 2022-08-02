package com.mastercard.test.flow.report.detail;

/**
 * Exercises the residue tab on a file-served report
 */
class FileResidueTest extends AbstractResidueTest {

	/***/
	FileResidueTest() {
		super( report.fileUrl() );
	}
}
