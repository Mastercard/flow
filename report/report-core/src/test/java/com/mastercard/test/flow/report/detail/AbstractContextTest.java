package com.mastercard.test.flow.report.detail;

import org.junit.jupiter.api.Test;

/**
 * Superclass for tests that exercise the context tab
 */
abstract class AbstractContextTest extends AbstractDetailTest {

	/**
	 * @param url report url
	 */
	protected AbstractContextTest( String url ) {
		super( url );
	}

	/**
	 * Exercises viewing context data
	 */
	@Test
	void context() {
		dseq.context()
				.hasPanels( "Psychological state" )
				.hasContent( "Psychological state",
						"Model",
						"{",
						"  'ava': {",
						"    'hunger': 'voracious',",
						"    'morals': 'normal',",
						"    'guilt': 'normal'",
						"  },",
						"  'ben': {",
						"    'hunger': 'normal',",
						"    'morals': 'flexible',",
						"    'guilt': 'normal'",
						"  },",
						"  'che': {",
						"    'hunger': 'normal',",
						"    'morals': 'normal',",
						"    'guilt': 'undeniable'",
						"  }",
						"}" );

	}
}
