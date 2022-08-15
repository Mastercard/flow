package com.mastercard.test.flow.assrt.mock;

import java.util.Collections;
import java.util.Set;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.assrt.TestModel.Actors;

/**
 * Trivial {@link Context} implementation to use in tests. Crucially, this is a
 * different class from {@link TestContext}
 */
public class AltTestContext implements Context {

	private String value;

	/**
	 * @return context value
	 */
	public String value() {
		return value;
	}

	/**
	 * @param v context value
	 * @return <code>this</code>
	 */
	public AltTestContext value( String v ) {
		value = v;
		return this;
	}

	@Override
	public String name() {
		return "AltTestContext";
	}

	@Override
	public Set<Actor> domain() {
		return Collections.singleton( Actors.B );
	}

	@Override
	public Context child() {
		return new AltTestContext().value( value );
	}

	@Override
	public String toString() {
		return "AltTestContext[" + value + "]";
	}
}
