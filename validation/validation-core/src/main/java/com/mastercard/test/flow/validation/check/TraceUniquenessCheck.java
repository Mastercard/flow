package com.mastercard.test.flow.validation.check;

import java.util.Optional;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;

/**
 * Checks that all {@link Flow}s in a {@link Model} have a unique trace
 */
public class TraceUniquenessCheck extends FlowPairCheck {

	/***/
	public TraceUniquenessCheck() {
		super( "Trace uniqueness", "All flows in a model have a unique trace" );
	}

	@Override
	protected Optional<String> violation( Flow left, Flow right ) {
		if( left.meta().trace().equals( right.meta().trace() ) ) {
			return Optional.of( "Shared trace" );
		}
		return Optional.empty();
	}

}
