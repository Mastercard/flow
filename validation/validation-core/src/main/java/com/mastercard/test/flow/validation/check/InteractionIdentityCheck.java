package com.mastercard.test.flow.validation.check;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that interactions within a {@link Flow} are uniquely addressable
 */
public class InteractionIdentityCheck implements Validation {

	@Override
	public String name() {
		return "Interaction Identity";
	}

	@Override
	public String explanation() {
		return "All interactions in a flow must have a unique identity";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		return model.flows()
				.map( flow -> new Check( this, flow.meta().id(), () -> {
					Map<String, Interaction> ids = new HashMap<>();
					return Flows.interactions( flow )
							.map( ntr -> {
								String id = String.format( "%s->%s %s",
										ntr.requester().name(), ntr.responder().name(), ntr.tags() );
								if( ids.containsKey( id ) ) {
									return new Violation( this, "Shared interaction ID" )
											.offender( flow, ids.get( id ), ntr );
								}
								ids.put( id, ntr );
								return null;
							} )
							.filter( Objects::nonNull )
							.findAny()
							.orElse( null );
				} ) );
	}

}
