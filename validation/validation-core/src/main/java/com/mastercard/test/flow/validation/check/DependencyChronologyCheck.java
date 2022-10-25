package com.mastercard.test.flow.validation.check;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.util.Transmission;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that dependencies within a single {@link Flow} do not violate
 * causality. i.e.: that they copy data from earlier messages to later messages
 * and not vice versa. We can rely on {@link DependencyLoopCheck} to ensure that
 * there exists a schedule of flows that allows inter-flow dependencies to be
 * honoured.
 */
public class DependencyChronologyCheck implements Validation {

	@Override
	public String name() {
		return "Dependency chronology";
	}

	@Override
	public String explanation() {
		return "Dependencies must copy from past messages";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		return model.flows()
				.map( flow -> new Check( this, flow.meta().id(), () -> {
					Dependency delorean = findTimeTraveller( flow );

					if( delorean != null ) {
						return new Violation( this,
								String.format( "Dependency chronology violation: copying data from\n"
										+ "%s"
										+ "\nto\n"
										+ "%s",
										delorean.source().getMessage().map( Message::assertable ).orElse( "???" ),
										delorean.sink().getMessage().map( Message::assertable ).orElse( "???" ) ) )
												.offender( flow,
														delorean.source().getInteraction().orElse( null ),
														delorean.sink().getInteraction().orElse( null ) );
					}
					return null;
				} ) );
	}

	private static Dependency findTimeTraveller( Flow flow ) {
		AtomicInteger idx = new AtomicInteger();
		// maps from interactions to their chronological index
		Map<Message, Integer> order = Flows.transmissions( flow )
				.stream()
				.map( Transmission::message )
				.collect( toMap( k -> k, v -> idx.getAndIncrement() ) );

		return flow.dependencies()
				.filter( d -> {
					Integer src = d.source().getMessage().map( order::get ).orElse( null );
					Integer snk = d.sink().getMessage().map( order::get ).orElse( null );
					return src != null && snk != null // it's a self-dependency
							&& src >= snk; // but it's not going forwards in time!
				} )
				.findFirst()
				.orElse( null );
	}

}
