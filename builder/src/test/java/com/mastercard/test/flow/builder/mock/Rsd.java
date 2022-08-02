package com.mastercard.test.flow.builder.mock;

import com.mastercard.test.flow.Residue;

/**
 * {@link Residue} implementation for use in testing
 */
public class Rsd implements Residue {

	private String value;

	/**
	 * @param v the new value
	 * @return this
	 */
	public Rsd value( String v ) {
		value = v;
		return this;
	}

	@Override
	public String name() {
		return "Rsd[" + value + "]";
	}

	@Override
	public Residue child() {
		return new Rsd().value( value );
	}

	@Override
	public String toString() {
		return name();
	}
}
