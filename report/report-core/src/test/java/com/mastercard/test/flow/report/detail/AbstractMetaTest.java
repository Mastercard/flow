package com.mastercard.test.flow.report.detail;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.seq.IndexSequence;

/**
 * Superclass for exercising the flow metadata on the flow detail page
 */
abstract class AbstractMetaTest extends AbstractDetailTest {

	/**
	 * @param url report url
	 */
	protected AbstractMetaTest( String url ) {
		super( url );
	}

	/**
	 * Checks flow metadata
	 */
	@Test
	void meta() {
		dseq.hasTitle( "denied cheese,rejected,supply" )
				.hasHeader( "denied", "cheese", "rejected", "supply" )
				.hasMotivation( "Shows what happens when Che can no longer ignore the problem" )
				.hasMotivationElement( "img",
						"src", "https://i.imgur.com/l39BOrB.gif",
						"alt", "cheese addiction" )
				.hasTrace( "child creation trace" );
	}

	/**
	 * Checks the link to the basis flow
	 */
	@Test
	void basis() {
		dseq.basis()
				.hasHeader( "supplied", "cheese", "fulfilled", "supply" );
	}

	/**
	 * Checks the links to dependency flows
	 */
	@Test
	void dependencies() {
		dseq.dependency( "concernedadvertcheesedemand" )
				.hasHeader( "concerned", "advert", "cheese", "demand" )
				.dependency()
				.hasHeader( "supplied", "cheese", "fulfilled", "supply" );
		dseq.back().back();
		dseq.dependency( "worriedcheesepipelinequery" )
				.hasHeader( "worried", "cheese", "pipeline", "query" );
	}

	/**
	 * Checks the link to the peer flow list
	 */
	@Test
	void peers() {
		IndexSequence iseq = dseq.peers();
		iseq.hasFilters( "", "cheese, rejected, supply", "" )
				.hasFlows(
						"denied  []" );
	}

}
