package com.mastercard.test.flow.report.detail;

import org.junit.jupiter.api.Test;

/**
 * Superclass for tests that exercise the residue tab
 */
abstract class AbstractResidueTest extends AbstractDetailTest {

	/**
	 * @param url report url
	 */
	protected AbstractResidueTest( String url ) {
		super( url );
	}

	/**
	 * Checks the contents of the residue tab
	 */
	@Test
	void residue() {
		dseq.residue()
				.hasUrlArgs( "msg=3", "tab=2" )
				.hasPanels( "Psychological state" )
				.hasContent( "Psychological state",
						"Model",
						"{",
						"  'ava': {",
						"    'hunger': 'unbearable',",
						"    'morals': 'normal',",
						"    'guilt': 'normal'",
						"  },",
						"  'ben': {",
						"    'hunger': 'normal',",
						"    'morals': 'normal',",
						"    'guilt': 'normal'",
						"  },",
						"  'che': {",
						"    'hunger': 'normal',",
						"    'morals': 'normal',",
						"    'guilt': 'absolved'",
						"  }",
						"}",
						"Full",
						"1 - full expected",
						"1 + full actual",
						"Masked",
						"1 - masked expect",
						"1 + masked actual" );
	}
}
