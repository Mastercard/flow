package com.mastercard.test.flow.validation.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Convenience superclass for validation checks that compare pairs of flows
 */
public abstract class FlowPairCheck implements Validation {

	private final String name;
	private final String explanation;

	/**
	 * @param name        to be returned from {@link #name()}
	 * @param explanation to be returned from {@link #explanation()}
	 */
	protected FlowPairCheck( String name, String explanation ) {
		this.name = name;
		this.explanation = explanation;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String explanation() {
		return explanation;
	}

	@Override
	public Stream<Check> checks( Model model ) {
		List<Check> checks = new ArrayList<>();
		Flow[] flows = model.flows().toArray( Flow[]::new );

		for( int i = 0; i < flows.length; i++ ) {
			Flow left = flows[i];
			for( int j = i + 1; j < flows.length; j++ ) {
				Flow right = flows[j];

				checks.add( new Check( this,
						left.meta().id() + " x " + right.meta().id(),
						() -> violation( left, right )
								.map( v -> new Violation( this, v )
										.offender( left )
										.offender( right ) )
								.orElse( null ) ) );
			}
		}
		return checks.stream();

	}

	/**
	 * Implement this with your pair-checking logic
	 *
	 * @param left  A flow
	 * @param right The other flow
	 * @return violation text, if a problem is detected
	 */
	protected abstract Optional<String> violation( Flow left, Flow right );
}
