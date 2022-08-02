package com.mastercard.test.flow.assrt;

import java.util.List;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Residue;

/**
 * Extend this class to provide a mechanism by which {@link Residue} data can be
 * checked against the system under test
 *
 * @param <T> The {@link Residue} type
 */
public abstract class Checker<T extends Residue> {

	private final Class<T> residueType;

	/**
	 * @param residueType The type of {@link Residue} the this checker validates
	 */
	protected Checker( Class<T> residueType ) {
		this.residueType = residueType;
	}

	/**
	 * Defines the type of {@link Residue} that is checked
	 *
	 * @return The type of {@link Residue} that this {@link Checker} operates on
	 */
	public Class<T> residueType() {
		return residueType;
	}

	/**
	 * Constructs a human-readable representation of the expected residue data. This
	 * is called before a {@link Flow} is processed for assertion.
	 *
	 * @param residue The residue from the system model
	 * @return A human-readable representation of that data
	 */
	public abstract Message expected( T residue );

	/**
	 * Extracts the true residual data from the system under test. This is called
	 * after a flow has been processed for assertion.
	 *
	 * @param residue   The residue from the system model
	 * @param behaviour Message data harvested during flow processing
	 * @return residual data bytes, such as can be supplied to
	 *         {@link Message#peer(byte[])} on the output of
	 *         {@link #expected(Residue)}
	 */
	public abstract byte[] actual( T residue, List<Assertion> behaviour );
}
