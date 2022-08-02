package com.mastercard.test.flow.validation.check;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that the tags that we use to signal flow outcome in execution reports
 * are not already present on {@link Flow}s
 */
public class ResultTagCheck implements Validation {

	/**
	 * Added to {@link Flow}s and {@link Interaction}s that pass assertions
	 */
	public static final String PASS_TAG = "PASS";
	/**
	 * Added to {@link Flow}s and {@link Interaction}s that fail assertions
	 */
	public static final String FAIL_TAG = "FAIL";
	/**
	 * Added to {@link Flow}s and {@link Interaction}s that are not exercised in the
	 * test
	 */
	public static final String SKIP_TAG = "SKIP";
	/**
	 * Added to {@link Flow}s that suffer some non-assertion error
	 */
	public static final String ERROR_TAG = "ERROR";

	@Override
	public String name() {
		return "Result tag misuse";
	}

	@Override
	public String explanation() {
		return "Certain tag values are assumed by the report components to signal the outcome of testing a flow."
				+ " These values are reserved for that purpose";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		return model.flows()
				.map( flow -> new Check( this, flow.meta().id(), () -> {
					String misused = Stream.of( ERROR_TAG, FAIL_TAG, PASS_TAG, SKIP_TAG )
							.filter( flow.meta().tags()::contains )
							.collect( Collectors.joining( ", " ) );

					if( !misused.isEmpty() ) {
						return new Violation( this, "Use of reserved tags: " + misused, null, null )
								.offender( flow );
					}

					return null;
				} ) );
	}
}
