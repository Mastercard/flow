package com.mastercard.test.flow.validation.coppice;

import com.mastercard.test.flow.Model;

/**
 * Exercises {@link Coppice} with a large synthetic {@link Model}
 */
class CoppiceTest {

	/**
	 * @param args ignored
	 */
	public static void main( String[] args ) {
		new Coppice().examine( new Forest() );
	}
}
