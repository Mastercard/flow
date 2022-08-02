package com.mastercard.test.flow.builder.concrete;

import java.util.function.Function;

import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;

/**
 * An immutable implementation of {@link FieldAddress}
 */
public class ConcreteFieldAddress implements FieldAddress {

	private final Flow flow;
	private final Function<Flow, Interaction> interaction;
	private final Function<Interaction, Message> message;
	private final String field;

	/**
	 * @param flow        The target flow
	 * @param interaction How to get the {@link Interaction} from the target
	 *                    {@link Flow}
	 * @param message     How to get the {@link Message} from the
	 *                    {@link Interaction}
	 * @param field       The field address
	 */
	public ConcreteFieldAddress( Flow flow, Function<Flow, Interaction> interaction,
			Function<Interaction, Message> message, String field ) {
		this.flow = flow;
		this.interaction = interaction;
		this.message = message;
		this.field = field;
	}

	@Override
	public Flow flow() {
		return flow;
	}

	@Override
	public Function<Flow, Interaction> interaction() {
		return interaction;
	}

	@Override
	public Function<Interaction, Message> message() {
		return message;
	}

	@Override
	public String field() {
		return field;
	}

}
