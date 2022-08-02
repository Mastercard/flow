package com.mastercard.test.flow.doc.mask;

import com.mastercard.test.flow.Unpredictable;

/**
 * These values represent factors that we can't predict in the system model
 */
enum Unpredictables implements Unpredictable {
	/**
	 * Signals the influence of random number generators
	 */
	RNG;
}
