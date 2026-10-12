package com.mastercard.test.flow.validation.check;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;

/**
 * Checks that all {@link Flow}s in a {@link Model} have a unique identity
 */
public class FlowIdentityCheck extends FlowKeyCheck {

	/***/
	public FlowIdentityCheck() {
		super( "Flow Identity", "All flows in a model have a unique identity", "Shared ID" );
	}

	@Override
	protected String key( Flow flow ) {
		return flow.meta().id();
	}

}
