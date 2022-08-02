package com.mastercard.test.flow.builder.mutable;

import java.util.function.Consumer;
import java.util.function.Function;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.concrete.ConcreteDependency;
import com.mastercard.test.flow.builder.concrete.ConcreteFlow;

/**
 * Builder for {@link ConcreteDependency}
 */
public class MutableDependency {

	private final MutableFieldAddress source;
	private Function<Object, Object> mutation = Function.identity();
	private final MutableFieldAddress sink;

	/**
	 * Initially empty
	 */
	public MutableDependency() {
		source = new MutableFieldAddress();
		sink = new MutableFieldAddress();
	}

	/**
	 * @param basis The initial state
	 */
	public MutableDependency( Dependency basis ) {
		source = new MutableFieldAddress( basis.source() );
		mutation = basis.mutation();
		sink = new MutableFieldAddress( basis.sink() );
	}

	/**
	 * Updates {@link Dependency#source()}
	 *
	 * @param s How to update the source
	 * @return <code>this</code>
	 */
	public MutableDependency source( Consumer<MutableFieldAddress> s ) {
		s.accept( source );
		return this;
	}

	/**
	 * Sets the {@link Dependency#mutation()} operation
	 *
	 * @param m The new mutation
	 * @return <code>this</code>
	 */
	public MutableDependency mutation( Function<Object, Object> m ) {
		mutation = m;
		return this;
	}

	/**
	 * Updates the {@link Dependency#sink()}
	 *
	 * @param s How to update the sink
	 * @return <code>this</code>
	 */
	public MutableDependency sink( Consumer<MutableFieldAddress> s ) {
		s.accept( sink );
		return this;
	}

	/**
	 * @param defaultFlow The {@link Flow} to use if the {@link Flow} in the field
	 *                    addresses is <code>null</code>
	 * @return The immutable version of the current state
	 */
	public ConcreteDependency build( ConcreteFlow defaultFlow ) {
		return new ConcreteDependency(
				source.build( defaultFlow ),
				mutation,
				sink.build( defaultFlow ) );
	}
}
