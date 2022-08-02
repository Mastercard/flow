package com.mastercard.test.flow.report.detail;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.seq.LogSequence;

/**
 * Exercises the flow detail report page
 */
abstract class AbstractLogViewTest extends AbstractDetailTest {

	/**
	 * @param url The report url
	 */
	protected AbstractLogViewTest( String url ) {
		super( url );
	}

	/**
	 * Checks the contents of the logging tab
	 */
	@Test
	void logs() {
		LogSequence lseq = dseq.logs();

		lseq.hasMessages(
				"0s Δ 0ms WARN abc message 1",
				"0.05s Δ 50ms INFO def message 2",
				"1.1s Δ 1050ms TRACE def message 3",
				"1.3s Δ 200ms INFO ghi message 4",
				"1.7s Δ 400ms INFO def message 5",
				"2.359s Δ 659ms WARN abc message 6" );
	}

	/**
	 * Shows that logs can be filtered
	 */
	@Test
	void logFilters() {
		LogSequence lseq = dseq.logs();

		lseq.levels( "WARN", "INFO" )
				.hasUrlArgs( "lv=WARN%2CINFO", "msg=3", "tab=3" )
				.hasMessages(
						"0s Δ 0ms WARN abc message 1",
						"0.05s Δ 50ms INFO def message 2",
						"1.3s Δ 1250ms INFO ghi message 4",
						"1.7s Δ 400ms INFO def message 5",
						"2.359s Δ 659ms WARN abc message 6" );

		lseq.source( "def" )
				.hasUrlArgs( "lv=WARN%2CINFO", "msg=3", "sf=def", "tab=3" )
				.hasMessages(
						"0.05s Δ 0ms INFO def message 2",
						"1.7s Δ 1650ms INFO def message 5" );

		lseq.message( "5" )
				.hasUrlArgs( "lv=WARN%2CINFO", "mf=5", "msg=3", "sf=def", "tab=3" )
				.hasMessages( "1.7s Δ 0ms INFO def message 5" );

		// we can deep-link direct to filtered views
		lseq.logs( "lv=WARN%2CINFO", "mf=5", "sf=def", "tab=3" )
				.hasMessages( "1.7s Δ 0ms INFO def message 5" );
	}

}
