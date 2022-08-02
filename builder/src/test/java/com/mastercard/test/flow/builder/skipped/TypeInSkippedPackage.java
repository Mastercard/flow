package com.mastercard.test.flow.builder.skipped;

import com.mastercard.test.flow.builder.SkipTrace;
import com.mastercard.test.flow.builder.Trace;

/**
 * Helping to exercise the package-skipping capabilities of {@link SkipTrace}
 */
public class TypeInSkippedPackage {

	/**
	 * @return The location where this method is called
	 */
	public static String trace() {
		return Trace.trace();
	}
}
