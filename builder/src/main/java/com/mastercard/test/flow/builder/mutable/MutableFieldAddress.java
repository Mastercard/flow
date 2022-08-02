package com.mastercard.test.flow.builder.mutable;

import java.util.function.Function;

import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.concrete.ConcreteFieldAddress;

/**
 * Builder for {@link ConcreteFieldAddress}
 */
public class MutableFieldAddress {

	private Flow flow = null;
	private Function<Flow, Interaction> interaction = null;
	private Function<Interaction, Message> message = null;
	private String field = null;

	/**
	 * Initially empty
	 */
	public MutableFieldAddress() {
	}

	/**
	 * @param basis The {@link FieldAddress} to copy
	 */
	public MutableFieldAddress( FieldAddress basis ) {
		flow = basis.flow();
		interaction = basis.interaction();
		message = basis.message();
		field = basis.field();
	}

	/**
	 * Defines the addressed {@link Flow}
	 *
	 * @param f The {@link Flow} that contains the field
	 * @return <code>this</code>
	 */
	public MutableFieldAddress flow( Flow f ) {
		flow = f;
		return this;
	}

	/**
	 * Defines the addressed {@link Interaction} within the {@link Flow}
	 *
	 * @param ntr How to extract the {@link Interaction} that contains the field
	 *            from the {@link Flow}
	 * @return <code>this</code>
	 */
	public MutableFieldAddress interaction( Function<Flow, Interaction> ntr ) {
		interaction = ntr;
		return this;
	}

	/**
	 * Defines the addressed {@link Message} within the {@link Interaction}
	 *
	 * @param m How to extract the {@link Message} that contains the field from the
	 *          {@link Interaction}
	 * @return <code>this</code>
	 */
	public MutableFieldAddress message( Function<Interaction, Message> m ) {
		message = m;
		return this;
	}

	/**
	 * Defines the addressed field within the {@link Message}
	 *
	 * @param f The field address in the {@link Message}
	 * @return <code>this</code>
	 */
	public MutableFieldAddress field( String f ) {
		field = f;
		return this;
	}

	/**
	 * @param defaultFlow The {@link Flow} to use if the {@link Flow} in this object
	 *                    is <code>null</code>
	 * @return The immutable version of the current state
	 */
	public ConcreteFieldAddress build( Flow defaultFlow ) {
		return new ConcreteFieldAddress( flow != null ? flow : defaultFlow,
				interaction, message, field );
	}
}
