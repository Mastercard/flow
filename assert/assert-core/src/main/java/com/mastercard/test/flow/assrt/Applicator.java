package com.mastercard.test.flow.assrt;

import java.util.Comparator;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;

/**
 * Extends this class to provide the mechanism by which {@link Context} data can
 * be applied to the system under test
 *
 * @param <T> The {@link Context} type
 */
public abstract class Applicator<T extends Context> {

	private final Class<T> contextType;
	private final int transitionCost;

	/**
	 * @param contextType    The type of {@link Context} that this applicator
	 *                       operates on
	 * @param transitionCost An indication of the relative cost of a state
	 *                       transition
	 */
	protected Applicator( Class<T> contextType, int transitionCost ) {
		this.contextType = contextType;
		this.transitionCost = transitionCost;
	}

	/**
	 * Defines the type of {@link Context} that is applied
	 *
	 * @return The type of {@link Context} that this {@link Applicator} operates on
	 */
	public Class<T> contextType() {
		return contextType;
	}

	/**
	 * Defines a metric for how expensive applications of the relevant state are.
	 * <p>
	 * Some {@link Context}s might be very cheap to apply (e.g.: set the return
	 * value of a mocked method), while some might be more costly (e.g.: update some
	 * rows in a database, and then restart a bunch of services and clear a bunch of
	 * caches). Specify the relative transition cost of the applicator to reflect
	 * these differences and we'll try to come up with an execution schedule that
	 * will minimise expensive state transitions.
	 * </p>
	 *
	 * @return An indication of the relative cost of a state transition
	 */
	public int transitionCost() {
		return transitionCost;
	}

	/**
	 * Builds a comparator for sorting contexts into an idealised
	 * minimal-transition-cost order.
	 *
	 * @return A comparator that will group similar contexts together.
	 */
	public abstract Comparator<T> order();

	/**
	 * Applies a {@link Context} to the system under test. This will be called if
	 * either:
	 * <ul>
	 * <li>The current {@link Flow} has a {@link Context} of the appropriate
	 * type</li>
	 * <li>The previous {@link Flow} has a {@link Context} of the appropriate
	 * type</li>
	 * </ul>
	 * It will not be called if neither the previous nor current {@link Flow}s have
	 * a {@link Context} of the appropriate type
	 *
	 * @param from The current {@link Context}, or <code>null</code> if the current
	 *             {@link Context} is unknown
	 * @param to   The new {@link Context}, or <code>null</code> if the current
	 *             {@link Flow} does not have the a {@link Context} of the relevant
	 *             type for this {@link Applicator}
	 */
	public abstract void transition( T from, T to );
}
