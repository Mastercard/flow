package com.mastercard.test.flow.assrt.filter.cli;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import com.mastercard.test.flow.assrt.filter.Filter;
import com.mastercard.test.flow.assrt.filter.FilterOptions;

/**
 * Use this superclass for tests that build {@link Filter} instances.
 */
public abstract class AbstractFilterTest {

	private static String updateFilter;

	/**
	 * This property will interfere with the test
	 */
	@BeforeAll
	public static void saveProperties() {
		updateFilter = FilterOptions.FILTER_UPDATE.clear();
	}

	/**
	 * Restore the pre-test state
	 */
	@AfterAll
	public static void restoreProperties() {
		if( updateFilter != null ) {
			FilterOptions.FILTER_UPDATE.set( updateFilter );
		}
	}

}
