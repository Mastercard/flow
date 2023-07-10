package com.mastercard.test.flow.report.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.mastercard.test.flow.report.seq.Browser;

/**
 * Exercises the report index as browsed on the file system. Being able to
 * browse from the file system is an important use-case - we don't want to have
 * to spin up a web server just to view test results
 */
@ExtendWith(Browser.class)
class FileIndexTest extends AbstractIndexTest {

	/***/
	FileIndexTest() {
		super( report.fileUrl() );
	}

	/**
	 * Shows that the interactions panel is not shown when we can't load the flow
	 * details
	 */
	@Test
	void noInteractions() {
		iseq.hasInteractionSummary( "" );
	}
}
