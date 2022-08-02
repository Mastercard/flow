package com.mastercard.test.flow.validation.check;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that all {@link Flow}s in a {@link Model} have a unique identity
 */
public class FlowIdentityCheck implements Validation {

	@Override
	public String name() {
		return "Flow Identity";
	}

	@Override
	public String explanation() {
		return "All flows in a model have a unique identity";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		List<Check> checks = new ArrayList<>();
		Flow[] flows = model.flows().toArray( Flow[]::new );

		for( int i = 0; i < flows.length; i++ ) {
			Flow a = flows[i];
			for( int j = i + 1; j < flows.length; j++ ) {
				Flow b = flows[j];

				checks.add( new Check( this,
						a.meta().id() + " x " + b.meta().id(),
						() -> {
							if( a.meta().id().equals( b.meta().id() ) ) {
								return new Violation( this, "Shared ID", null, null )
										.offender( a )
										.offender( b );
							}
							return null;
						} ) );
			}
		}
		return checks.stream();
	}

}
