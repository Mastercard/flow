package com.mastercard.test.flow.report.detail;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.seq.FlowSequence;

/**
 * Asserts on the sequence diagram and message views of the detail page
 */
abstract class AbstractFlowSequenceTest extends AbstractDetailTest {

	/**
	 * @param url report url
	 */
	AbstractFlowSequenceTest( String url ) {
		super( url );
	}

	/**
	 * Checks message content
	 */
	@Test
	void messages() {
		FlowSequence fseq = dseq.flow().onTransmission( "BEN response" );

		fseq.onExpected()
				.hasUrlArgs( "display=Expected", "msg=3" )
				.hasMessage(
						"Sorry Ava, no brie today" );

		fseq.onActual()
				.hasUrlArgs( "msg=3" ) // Actual is the default, no arg required
				.hasMessage(
						"Sorry Ava, no brie today, or ever." );

		fseq.onDiff()
				.hasUrlArgs( "display=Diff", "msg=3" )
				.hasMessage(
						"1 - Sorry Ava, no brie today",
						"1 + Sorry Ava, no brie today, or ever." );

		// we can deep-link to a view
		dseq.detail( "display=Diff", "msg=2" )
				.flow()
				.hasMessage(
						"1 1 No, I'm worried about her dairy consumption.",
						"2 2 I'm cutting you both off" );
	}
}
