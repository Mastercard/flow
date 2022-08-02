package com.mastercard.test.flow.validation.check;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that the tags are in a maximum of one execution chain
 */
public class ChainOverlapCheck implements Validation {

	/**
	 * The prefix of chain tags
	 */
	public static final String TAG_PREFIX = "chain:";

	@Override
	public String name() {
		return "Chain overlap";
	}

	@Override
	public String explanation() {
		return "A flow should only exist in a maximum of one execution chain";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		return model.flows()
				.map( flow -> new Check( this, flow.meta().id(), () -> {

					Set<String> chains = flow.meta().tags().stream()
							.filter( t -> t.startsWith( TAG_PREFIX ) )
							.collect( Collectors.toCollection( TreeSet::new ) );

					if( chains.size() > 1 ) {
						return new Violation( this, "Overlapping chains " + chains, null, null )
								.offender( flow );
					}

					return null;
				} ) );
	}
}
