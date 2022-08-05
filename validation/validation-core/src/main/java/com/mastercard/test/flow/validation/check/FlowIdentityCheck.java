package com.mastercard.test.flow.validation.check;

import java.util.Optional;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;

/**
 * Checks that all {@link Flow}s in a {@link Model} have a unique identity
 */
public class FlowIdentityCheck extends FlowPairCheck {

	/***/
	public FlowIdentityCheck() {
		super( "Flow Identity", "All flows in a model have a unique identity" );
	}

	@Override
	protected Optional<String> violation( Flow left, Flow right ) {
		if( left.meta().id().equals( right.meta().id() ) ) {
			return Optional.of( "Shared ID" );
		}
		return Optional.empty();
	}

}
