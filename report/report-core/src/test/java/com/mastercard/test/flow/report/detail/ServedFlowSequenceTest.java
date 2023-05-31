package com.mastercard.test.flow.report.detail;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.seq.FlowSequence;

/**
 * Exercises the flow message data on an http-served report
 */
class ServedFlowSequenceTest extends AbstractFlowSequenceTest {
	/***/
	ServedFlowSequenceTest() {
		super( report.url() );
	}

	/**
	 * Checks the structure of the flow sequence diagram. As we're able to fetch the
	 * basis flow, the sequence diagram marks those interactions that have a
	 * counterpart in the basis
	 */
	@Test
	void sequenceDiagram() {
		FlowSequence fseq = dseq.flow();
		fseq.hasActors( "AVA", "BEN", "CHE" )
				.hasTransmissions(
						" BEN request       [eb   ]",
						"   CHE request     [eb   ]",
						"   CHE response    [ebap ] 100%",
						" BEN response      [eba f] 100%" );
	}

	/**
	 * Exercises the basis diff view. Since we're looking at a http-served report
	 * chrome is free to make the ajax call to fetch the basis flow data, so we get
	 * to see the changes from the basis to this flow
	 */
	@Test
	void basis() {
		FlowSequence fseq = dseq.flow().onTransmission( "BEN response" );

		fseq.onBasis()
				.hasUrlArgs( "display=Basis", "msg=3" )
				.hasMessage(
						"1 - Hi Ava! Here is your brie",
						"1 + Sorry Ava, no brie today" );
	}
}
