
package com.mastercard.test.flow.assrt;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Residue;

/**
 * Implementations will be informed as {@link Flow} are processed
 */
public interface Progress {

	/**
	 * Called when {@link Flow} filtering is being applied
	 */
	default void filtering() {
		// no-op
	}

	/**
	 * Called when a processing order is being computed
	 */
	default void ordering() {
		// no-op
	}

	/**
	 * Called when a {@link Flow} begins processing
	 *
	 * @param flow The {@link Flow} that is about to be processed
	 */
	default void flow( Flow flow ) {
		// no-op
	}

	/**
	 * Called when a {@link Context} is about to be applied
	 *
	 * @param context The {@link Context} that is about to be applied
	 */
	default void context( Context context ) {
		// no-op
	}

	/**
	 * Called when an {@link Interaction} is about to be processed
	 *
	 * @param interaction The {@link Interaction} that is about to be processed
	 */
	default void interaction( Interaction interaction ) {
		// no-op
	}

	/**
	 * Called the initial state of a {@link Residue} is about to be extracted
	 *
	 * @param residue The {@link Residue} that is about to be checked
	 */
	default void before( Residue residue ) {
		// no-op
	}

	/**
	 * Called when the final state of a {@link Residue} is about to be checked
	 *
	 * @param residue The {@link Residue} that is about to be checked
	 */
	default void after( Residue residue ) {
		// no-op
	}

	/**
	 * Called when a {@link Flow} has completed processing
	 *
	 * @param flow The {@link Flow} that has completed
	 */
	default void flowComplete( Flow flow ) {
		// no-op
	}
}
