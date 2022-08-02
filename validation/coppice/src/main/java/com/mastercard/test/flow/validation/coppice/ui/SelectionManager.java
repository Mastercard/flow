/**
 * Copyright (c) 2021 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

import java.util.ArrayList;
import java.util.List;

import com.mastercard.test.flow.Flow;

/**
 * There's a bunch of components that all show {@link Flow}s, this class can be
 * used to ensure that when a {@link Flow} is selected in one it is selected in
 * all of them
 */
public class SelectionManager {

	private final List<Client> clients = new ArrayList<>();
	private Flow selected;
	private boolean forcing = false;

	/**
	 * @return The selected {@link Flow}
	 */
	public Flow getSelected() {
		return selected;
	}

	/**
	 * @param c The party that is interested in flow selection
	 */
	public void register( Client c ) {
		clients.add( c );
		if( !forcing ) {
			forcing = true;
			c.force( selected );
			forcing = false;
		}
	}

	/**
	 * Call this when the selected transaction has changed
	 *
	 * @param txn The new selection
	 */
	public void update( Flow txn ) {
		selected = txn;
		if( !forcing ) {
			forcing = true;
			for( Client c : clients ) {
				c.force( txn );
			}
			forcing = false;
		}
	}

	/**
	 * Implemented by those interested in flow selection
	 */
	public interface Client {

		/**
		 * @param txn Called when the selection is changed by some other client
		 */
		void force( Flow txn );
	}
}
