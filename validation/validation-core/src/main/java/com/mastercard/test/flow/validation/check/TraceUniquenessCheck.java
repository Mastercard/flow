package com.mastercard.test.flow.validation.check;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;

/**
 * Checks that all {@link Flow}s in a {@link Model} have a unique trace
 */
public class TraceUniquenessCheck extends FlowKeyCheck {

	/***/
	public TraceUniquenessCheck() {
		super( "Trace uniqueness", "All flows in a model have a unique trace", "Shared trace" );
	}

	@Override
	protected String key( Flow flow ) {
		return flow.meta().trace();
	}

}
