package com.mastercard.test.flow.validation.check;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that we don't have any dependency loops. Such loops prevent the
 * calculation of a valid execution schedule - at least one {@link Flow} would
 * be forced to be executed before at least one of its dependencies.
 */
public class DependencyLoopCheck implements Validation {

	@Override
	public String name() {
		return "Dependency loop";
	}

	@Override
	public String explanation() {
		return "Dependency loops cannot be honoured during execution";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		return model.flows()
				.map( flow -> new Check( this, flow.meta().id(), () -> {
					Deque<Flow> loop = dependencyLoop( flow, new HashSet<>() );
					if( loop != null ) {
						return new Violation( this, "Dependency loop",
								"",
								loop.stream()
										.map( f -> f.meta().id() )
										.collect( joining( "\n" ) ) )
												.offender( flow );
					}
					return null;
				} ) );
	}

	private static Deque<Flow> dependencyLoop( Flow flow, Set<Flow> seen ) {
		if( seen.contains( flow ) ) {
			// recursion base case: by following the chain of dependencies we've hit a flow
			// that we've seen before. The dequeue that we return here will be built up with
			// the loop members as the call stack unwinds
			return new ArrayDeque<>( Arrays.asList( flow ) );
		}

		seen.add( flow );

		// find the set of flows that we depend on
		Set<Flow> upstream = flow.dependencies()
				.map( d -> d.source().flow() )
				.filter( Objects::nonNull )
				.filter( f -> flow != f ) // allow self dependencies
				.collect( toSet() );

		// recurse into them
		Optional<Deque<Flow>> upLoop = upstream.stream()
				.map( up -> dependencyLoop( up, new HashSet<>( seen ) ) )
				.filter( Objects::nonNull )
				.findFirst();

		// one of our deps has reported a loop! Add ourselves to it
		upLoop.ifPresent( d -> d.addFirst( flow ) );

		return upLoop
				.orElse( null );
	}
}
