package com.mastercard.test.flow.model;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;

/**
 * Abstract superclass for dealing with {@link Model} titles
 */
public abstract class TitledModel implements Model {

	private final String title;

	/**
	 * Uses the classname as the title
	 */
	protected TitledModel() {
		title = getClass().getSimpleName();
	}

	/**
	 * @param title A human-readable title for this group of {@link Flow}s
	 */
	protected TitledModel( String title ) {
		this.title = title;
	}

	@Override
	public String title() {
		return title;
	}

}
