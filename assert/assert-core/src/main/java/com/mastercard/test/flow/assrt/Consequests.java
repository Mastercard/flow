package com.mastercard.test.flow.assrt;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.mastercard.test.flow.Actor;

/**
 * Convenience utility capturing consequent requests - those requests made by an
 * {@link Actor} as it processes a request. Capture the requests in your
 * assertion behaviour then provide that data to be asserted via
 * {@link Assertion#assertConsequests(Consequests)}
 */
public class Consequests {

	private final Map<Actor, List<byte[]>> captures = new HashMap<>();

	/**
	 * Deletes all captured data
	 *
	 * @return <code>this</code>
	 */
	public Consequests clear() {
		captures.clear();
		return this;
	}

	/**
	 * Adds a captured request
	 *
	 * @param target  The target of the request
	 * @param request The content of the request
	 * @return <code>this</code>
	 */
	public Consequests capture( Actor target, byte[] request ) {
		captures.computeIfAbsent( target, a -> new ArrayList<>() ).add( request );
		return this;
	}

	/**
	 * @return The captured data
	 * @see Assertion#assertConsequests(Function)
	 */
	Function<Actor, List<byte[]>> harvest() {
		Map<Actor, List<byte[]>> copy = new HashMap<>( captures );
		clear();
		return a -> copy.getOrDefault( a, emptyList() );
	}

}
