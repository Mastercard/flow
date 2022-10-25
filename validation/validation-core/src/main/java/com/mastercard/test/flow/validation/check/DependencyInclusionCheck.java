package com.mastercard.test.flow.validation.check;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that the dependency flows are actually part of the system model
 */
public class DependencyInclusionCheck implements Validation {

	@Override
	public String name() {
		return "Dependency inclusion";
	}

	@Override
	public String explanation() {
		return "Dependency sources must be included in the system model";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		Set<Flow> allFlows = model.flows().collect( toSet() );
		return model.flows()
				.flatMap( Flow::dependencies )
				.map( d -> new Check( this,
						name( d ),
						() -> d.source().getFlow()
								.filter( src -> !allFlows.contains( src ) )
								.map( src -> new Violation( this,
										String.format( "Dependency source '%s' not presented in system model",
												src.meta().id() ) )
														.offender( d.sink().flow() ) )
								.orElse( null ) ) );
	}

	private static String name( Dependency d ) {
		return String.format( "%s → %s",
				d.source().getFlow().map( f -> f.meta().id() ).orElse( null ),
				d.sink().getFlow().map( f -> f.meta().id() ).orElse( null ) );
	}
}
