package com.mastercard.test.flow.report.detail;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.seq.FlowSequence;

/**
 * Exercises the flow message data on a file-served report
 */
class FileFlowSequenceTest extends AbstractFlowSequenceTest {
	/***/
	FileFlowSequenceTest() {
		super( report.fileUrl() );
	}

	/**
	 * Checks the structure of the flow sequence diagram. As we're unable to fetch
	 * the basis flow, the sequence diagram does not show which interactions have a
	 * counterpart there.
	 */
	@Test
	void sequenceDiagram() {
		FlowSequence fseq = dseq.flow();
		fseq.hasActors( "AVA", "BEN", "CHE" )
				.hasTransmissions(
						" BEN request       [e    ]",
						"   CHE request     [e    ]",
						"   CHE response    [e ap ]",
						" BEN response      [e a f]" );
	}

	/**
	 * Exercises the basis diff view. Since we're looking at a file-served report
	 * chrome is is unable to make the ajax call to get the basis flow data, so the
	 * basis diff just shows the full message
	 */
	@Test
	void basis() {
		FlowSequence fseq = dseq.flow().onTransmission( "BEN response" );

		fseq.onBasis()
				.hasUrlArgs( "display=Basis", "msg=3" )
				.hasMessage(
						"1 + Sorry Ava, no brie today" );
	}
}
