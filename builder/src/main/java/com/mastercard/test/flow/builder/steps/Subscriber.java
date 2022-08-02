package com.mastercard.test.flow.builder.steps;

import java.util.function.Function;
import java.util.function.Predicate;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.mutable.MutableDependency;
import com.mastercard.test.flow.util.Flows;
import com.mastercard.test.flow.util.Transmission.Type;

/**
 * The second stage of defining a subscription - identifying the field to copy
 * the published value to
 *
 * @param <R> The type that initiated the subscription sequence
 */
public class Subscriber<R> {

	private final MutableDependency building;
	private final R returnTo;

	/**
	 * @param building The {@link Dependency} to populate
	 * @param returnTo The object to return control to after this sequence
	 */
	Subscriber( MutableDependency building, R returnTo ) {
		this.building = building;
		this.returnTo = returnTo;
	}

	/**
	 * Defines how data is updated as it is processed by the dependency
	 *
	 * @param mutation How to produce the value to populate into the destination
	 *                 {@link Message}
	 * @return <code>this</code>
	 */
	public final Subscriber<R> mutate( Function<Object, Object> mutation ) {
		building.mutation( mutation );
		return this;
	}

	/**
	 * Identifies the field of interest in the sink {@link Flow}
	 *
	 * @param ntr   Extracts the {@link Interaction} of interest from a {@link Flow}
	 * @param msg   Extracts the {@link Message} of interest from the
	 *              {@link Interaction}
	 * @param field The address of the field to populate
	 * @return The next stage of subscription definition
	 */
	public R sink( Function<Flow, Interaction> ntr, Function<Interaction, Message> msg,
			String field ) {
		building.sink( s -> s.interaction( ntr ).message( msg ).field( field ) );
		return returnTo;
	}

	/**
	 * Identifies the field of interest in the sink {@link Flow}
	 *
	 * @param ntr   Identifies {@link Interaction} of interest in a {@link Flow}
	 * @param type  Whether the message of interest is the request or response
	 * @param field The address of the field to publish
	 * @return The next stage in subscription definition
	 */
	public R to( Predicate<Interaction> ntr, Type type, String field ) {
		return sink( f -> Flows.interactions( f )
				.filter( ntr )
				.findFirst().orElse( null ),
				type.msg,
				field );
	}
}
