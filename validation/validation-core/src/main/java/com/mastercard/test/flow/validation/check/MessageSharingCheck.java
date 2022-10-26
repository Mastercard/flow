package com.mastercard.test.flow.validation.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.util.Transmission;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Ensures that all {@link Interaction}s have unique message instances. Sharing
 * message instances leads to really confusing-to-debug problems, as updates to
 * one {@link Flow} will be applied to another.
 */
public class MessageSharingCheck implements Validation {

	@Override
	public String name() {
		return "Message sharing";
	}

	@Override
	public String explanation() {
		return "Ensures that message instances are not shared between distinct interactions";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		List<Check> checks = new ArrayList<>();
		Flow[] flows = model.flows().toArray( Flow[]::new );

		Map<Integer, MessageOwner> messageIdentities = new HashMap<>();

		for( int i = 0; i < flows.length; i++ ) {
			Flow flow = flows[i];

			checks.add( new Check( this,
					flow.meta().id(),
					() -> {
						for( Transmission tx : Flows.transmissions( flow ) ) {
							int objectId = System.identityHashCode( tx.message() );
							MessageOwner current = new MessageOwner( flow, tx.source() );
							MessageOwner previous = messageIdentities.get( objectId );
							if( previous != null ) {
								return new Violation( this, "Shared message:\n" + tx.message().assertable() )
										.offender( previous.flow, previous.interaction )
										.offender( current.flow, current.interaction );
							}
							messageIdentities.put( objectId, current );
						}
						return null;
					} ) );
		}
		return checks.stream();
	}

	private static class MessageOwner {
		final Flow flow;
		final Interaction interaction;

		MessageOwner( Flow flow, Interaction interaction ) {
			this.flow = flow;
			this.interaction = interaction;
		}
	}
}
