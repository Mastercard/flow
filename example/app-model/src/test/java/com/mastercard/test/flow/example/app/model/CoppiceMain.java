package com.mastercard.test.flow.example.app.model;

import com.mastercard.test.flow.validation.coppice.Coppice;

/**
 * Launcher for a {@link Coppice} instance to examine
 * {@link ExampleSystem#MODEL}
 */
public class CoppiceMain {

	/**
	 * @param args command-line arguments
	 */
	public static void main( String[] args ) {
		new Coppice().examine( ExampleSystem.MODEL );
	}
}
