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
		FlowSequence fseq = dseq
				.flow()
				.onTransmission( "BEN response" );

		fseq.onExpected()
				.hasUrlArgs( "display=Expected", "msg=5" )
				.hasMessage(
						"Sorry Ava, no brie today" );

		fseq.onActual()
				.hasUrlArgs( "msg=5" ) // Actual is the default, no arg required
				.hasMessage(
						"Sorry Ava, no brie today, or ever." );

		fseq.onDiff()
				.hasUrlArgs( "display=Diff", "msg=5" )
				.hasMessage(
						"1 - Sorry Ava, no brie today",
						"1 + Sorry Ava, no brie today, or ever." );

		// we can deep-link to a view
		dseq.detail( "display=Diff", "msg=2" )
				.flow()
				.hasMessage(
						"1 1   No, I'm worried about her dairy consumption.",
						"2 2   I'm cutting you both off" );
	}

	/**
	 * Exercises the message-content search facility
	 */
	@Test
	void search() {
		FlowSequence fseq = dseq
				.flow()
				.hasSearchHits( /* initially no hits */ );

		fseq.toggleSearch()
				.search( "brie" )
				.hasUrlArgs(
						"msg=5",
						"search=brie" )
				.hasSearchHits(
						"BEN request : expected",
						"CHE request : expected",
						"BEN response : expected actual" );

		fseq.toggleSearch()
				.hasSearchHits( /* closing the input clears the search */ );

		fseq.toggleSearch()
				.search( "or" )
				.hasUrlArgs(
						"msg=5",
						"search=or" )
				.hasSearchHits(
						"CHE response : expected actual",
						"BEN response : expected actual" );

		fseq.onTransmission( "CHE response" )
				.onExpected()
				.hasMessage(
						"No, I'm w[or]ried about her dairy consumption.",
						"I'm cutting you both off" );

		fseq.onTransmission( "BEN response" )
				.onActual()
				.hasMessage(
						"S[or]ry Ava, no brie today, [or] ever." );

		// we can deep-link to a search
		dseq.detail( "search=ever" ).flow()
				.hasSearch( "ever" )
				.hasSearchHits( "BEN response : actual" )
				.onTransmission( "BEN response" )
				.onActual()
				.hasMessage( "Sorry Ava, no brie today, or [ever]." );
	}
}
