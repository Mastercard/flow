package com.mastercard.test.flow.assrt;

import java.util.function.UnaryOperator;

/**
 * Controls how reports are generated
 */
public enum Reporting {
	/**
	 * Reports are always generated and displayed
	 */
	ALWAYS(true, e -> true),
	/**
	 * Reports are always generated but are only displayed if failures are
	 * encountered
	 */
	FAILURES(true, e -> e),
	/**
	 * Reports are always generated but are not displayed
	 */
	QUIETLY(true, e -> false),
	/**
	 * Reports are not generated
	 */
	NEVER(false, e -> false);

	private final boolean writing;
	private final UnaryOperator<Boolean> shouldOpen;

	Reporting( boolean writing, UnaryOperator<Boolean> shouldOpen ) {
		this.writing = writing;
		this.shouldOpen = shouldOpen;
	}

	/**
	 * Determines if a report is generated
	 *
	 * @return <code>true</code> if a report should be generated
	 */
	public boolean writing() {
		return writing;
	}

	/**
	 * Determines is a report is opened for the user
	 *
	 * @param error <code>true</code> is an error has been encountered
	 * @return <code>true</code> if the report should be opened for the user
	 */
	public boolean shouldOpen( boolean error ) {
		return shouldOpen.apply( error );
	}
}
