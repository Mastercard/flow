package com.mastercard.test.flow.assrt.filter.gui;

import java.awt.GridBagConstraints;

/**
 * Fluent builder interface for {@link GridBagConstraints}
 */
class GBCB {
	private final GridBagConstraints gbc = new GridBagConstraints();

	/**
	 * @param x horizontal grid position
	 * @return <code>this</code>
	 */
	GBCB gridx( int x ) {
		gbc.gridx = x;
		return this;
	}

	/**
	 * @param y vertical grid position
	 * @return <code>this</code>
	 */
	GBCB gridy( int y ) {
		gbc.gridy = y;
		return this;
	}

	/**
	 * @param h vertical grid extent
	 * @return this
	 */
	GBCB gridheight( int h ) {
		gbc.gridheight = h;
		return this;
	}

	/**
	 * @param w horizontal grid extent
	 * @return <code>this</code>
	 */
	GBCB gridwidth( int w ) {
		gbc.gridwidth = w;
		return this;
	}

	/**
	 * @param wx The relative proportion of extra horizontal space that will go to
	 *           the component
	 * @return <code>this</code>
	 */
	GBCB weightx( int wx ) {
		gbc.weightx = wx;
		return this;
	}

	/**
	 * @param wy The relative proportion of extra vertical space that will go to the
	 *           component
	 * @return <code>this</code>
	 */
	GBCB weighty( int wy ) {
		gbc.weighty = wy;
		return this;
	}

	/**
	 * How to grow the component when it is smaller than its display area
	 *
	 * @param f The fill flag
	 * @return <code>this</code>
	 */
	GBCB fill( int f ) {
		gbc.fill = f;
		return this;
	}

	/**
	 * @return The populated contraint
	 */
	GridBagConstraints get() {
		return gbc;
	}
}
