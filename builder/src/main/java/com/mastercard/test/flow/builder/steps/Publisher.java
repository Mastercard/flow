package com.mastercard.test.flow.builder.steps;

import java.util.function.Function;
import java.util.function.Predicate;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.mutable.MutableDependency;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.util.Transmission.Type;

/**
 * The first stage of defining a subscription - identifying the field to publish
 *
 * @param <R> The type that initiated the subscription sequence
 */
public class Publisher<R> {
	private final MutableDependency building;
	private final R returnTo;

	/**
	 * @param building The builder to populate
	 * @param returnTo The object to return control to after this sequence
	 */
	public Publisher( MutableDependency building, R returnTo ) {
		this.building = building;
		this.returnTo = returnTo;
	}

	/**
	 * Identifies the field of interest in the source {@link Flow}
	 *
	 * @param ntr   Extracts the {@link Interaction} of interest from a {@link Flow}
	 * @param msg   Extracts the {@link Message} of interest from the
	 *              {@link Interaction}
	 * @param field The address of the field to publish
	 * @return The next stage in subscription definition
	 */
	public Subscriber<R> source( Function<Flow, Interaction> ntr, Function<Interaction, Message> msg,
			String field ) {
		building.source( s -> s
				.interaction( ntr )
				.message( msg )
				.field( field ) );
		return new Subscriber<>( building, returnTo );
	}

	/**
	 * Identifies the field of interest in the source {@link Flow}
	 *
	 * @param ntr   Identifies {@link Interaction} of interest in a {@link Flow}
	 * @param type  Whether the message of interest is the request or response
	 * @param field The address of the field to publish
	 * @return The next stage in subscription definition
	 */
	public Subscriber<R> from( Predicate<Interaction> ntr, Type type, String field ) {
		return source( f -> Flows.interactions( f )
				.filter( ntr )
				.findFirst().orElse( null ),
				type.msg,
				field );
	}
}
