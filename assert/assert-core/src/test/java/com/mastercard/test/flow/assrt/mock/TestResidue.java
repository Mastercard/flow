package com.mastercard.test.flow.assrt.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Residue;

/**
 * Trivial {@link Residue} implementation to use in tests
 */
public class TestResidue implements Residue {

	@JsonProperty("value")
	private String value;

	/**
	 * @return context value
	 */
	public String value() {
		return value;
	}

	/**
	 * @param v context value
	 * @return <code>this</code>
	 */
	@JsonProperty("value")
	public TestResidue value( String v ) {
		value = v;
		return this;
	}

	@Override
	public String name() {
		return "TestResidue";
	}

	@Override
	public TestResidue child() {
		return new TestResidue().value( value );
	}

	@Override
	public String toString() {
		return "TestResidue[" + value + "]";
	}
}
