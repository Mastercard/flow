package com.mastercard.test.flow.builder.concrete;

import java.util.function.UnaryOperator;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;

/**
 * An immutable implementation of {@link Dependency}
 */
public class ConcreteDependency implements Dependency {

	private final ConcreteFieldAddress source;
	private final UnaryOperator<Object> mutation;
	private final ConcreteFieldAddress sink;

	/**
	 * @param source   Where field comes from
	 * @param mutation How field is altered in transit
	 * @param sink     Where field goes to
	 */
	public ConcreteDependency( ConcreteFieldAddress source, UnaryOperator<Object> mutation,
			ConcreteFieldAddress sink ) {
		this.source = source;
		this.mutation = mutation;
		this.sink = sink;
	}

	@Override
	public FieldAddress source() {
		return source;
	}

	@Override
	public UnaryOperator<Object> mutation() {
		return mutation;
	}

	@Override
	public FieldAddress sink() {
		return sink;
	}

}
