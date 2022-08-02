package com.mastercard.test.flow.assrt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;

/**
 * The unit of assertion processing: a single interaction. The model provides
 * what we expect to happen, populate the actual result with what did happen
 */
public class Assertion {

	private final Flow flow;
	private final Interaction expected;
	private final AbstractFlocessor<?> flocessor;
	private final Actual actual = new Actual();

	private final List<Assertion> children = new ArrayList<>();

	/**
	 * @param flow      The context for the {@link Interaction}
	 * @param expected  The expected {@link Interaction}
	 * @param flocessor The engine driving the test
	 */
	Assertion( Flow flow, Interaction expected, AbstractFlocessor<?> flocessor ) {
		this.flow = flow;
		this.expected = expected;
		this.flocessor = flocessor;
	}

	/**
	 * Flow accessor
	 *
	 * @return The context for the {@link Interaction}
	 */
	public Flow flow() {
		return flow;
	}

	/**
	 * Expected data accessor
	 *
	 * @return The expected {@link Interaction}
	 */
	public Interaction expected() {
		return expected;
	}

	/**
	 * Actual data accessor
	 *
	 * @return The actual interaction
	 */
	public Actual actual() {
		return actual;
	}

	/**
	 * Allows assertion on downstream interactions
	 *
	 * @param selector Returns <code>true</code> for the child interaction that you
	 *                 want to assert on
	 * @return {@link Assertion}s on the selected children
	 */
	public Stream<Assertion> assertChildren( Predicate<Interaction> selector ) {
		return expected.children()
				.filter( selector )
				.map( i -> {
					Assertion a = new Assertion( flow, i, flocessor );
					children.add( a );
					return a;
				} );
	}

	/**
	 * Adds assertions on downstream requests captured during request process
	 *
	 * @param cs The captured downstream requests
	 * @return <code>this</code>
	 */
	public Assertion assertConsequests( Consequests cs ) {
		return assertConsequests( cs.harvest() );
	}

	/**
	 * Traverses the child interaction structure to find the interactions where the
	 * flow behaviour exits the system under test, gives an opportunity to provide
	 * actual data for those consequent requests
	 *
	 * @param data Supplied with {@link Actor}s outside of the system under test,
	 *             should return an exhaustive list of the requests received by that
	 *             {@link Actor} during the test, or <code>null</code> if we don't
	 *             have that information
	 * @return <code>this</code>
	 */
	public Assertion assertConsequests( Function<Actor, List<byte[]>> data ) {
		// find all the interactions that leave the system
		Map<Actor, List<Assertion>> exits = findSystemBoundaries( new HashMap<>() );

		// for each actor that is expected to receive a request from the system
		exits.forEach( ( actor, asrts ) -> {
			// find the list of requests that actor actually received
			List<byte[]> actualRequests = data.apply( actor );

			if( actualRequests != null ) {
				// make sure reality matches our expectations
				if( asrts.size() != actualRequests.size() ) {
					// the structure of the flow is wrong - too few or too many interactions
					// with that actor
					flocessor.compare(
							String.format( "Consequent request to %s mismatch: expected %s got %s", actor,
									asrts.size(), actualRequests.size() ),
							asrts.stream()
									.map( a -> a.expected().request().assertable() )
									.collect( Collectors.joining( "\n-----\n" ) ),
							actualRequests.stream()
									.map( b -> new String( b, UTF_8 ) )
									.collect( Collectors.joining( "\n-----\n" ) ) );
				}
				else {
					// zipper the data into the assertions
					Iterator<Assertion> ai = asrts.iterator();
					Iterator<byte[]> bi = actualRequests.iterator();
					while( ai.hasNext() && bi.hasNext() ) {
						ai.next().actual().request( bi.next() );
					}
				}
			}
		} );
		return this;
	}

	private Map<Actor, List<Assertion>>
			findSystemBoundaries( Map<Actor, List<Assertion>> exits ) {
		if( !flocessor.system().contains( expected().responder() ) ) {
			exits.computeIfAbsent( expected().responder(),
					a -> new ArrayList<>() ).add( this );
		}
		else {
			assertChildren( i -> true ).forEach( a -> a.findSystemBoundaries( exits ) );
		}
		return exits;
	}

	/**
	 * Adds this and any child assertions to a list
	 *
	 * @param l the list to add to
	 * @return the supplied list
	 */
	List<Assertion> collect( List<Assertion> l ) {
		l.add( this );
		children.forEach( c -> c.collect( l ) );
		return l;
	}
}
