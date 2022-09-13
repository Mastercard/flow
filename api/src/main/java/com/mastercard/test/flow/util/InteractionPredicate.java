package com.mastercard.test.flow.util;

import static java.util.Collections.emptySet;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;

/**
 * <p>
 * A convenience implementation of {@link Predicate} that offers friendlier
 * {@link #toString()} behaviour.
 * </p>
 * <p>
 * Note that this type is immutable - the methods that appear to be mutators
 * actually return new instances.
 * </p>
 */
public class InteractionPredicate implements Predicate<Interaction> {

	private final Set<Actor> tx;
	private final Set<Actor> rx;
	private final Set<String> include;
	private final Set<String> exclude;

	/**
	 * Constructs an empty {@link Predicate} that will match every
	 * {@link Interaction}
	 */
	public InteractionPredicate() {
		this( emptySet(), emptySet(), emptySet(), emptySet() );
	}

	private InteractionPredicate( Set<Actor> tx, Set<Actor> rx, Set<String> include,
			Set<String> exclude ) {
		this.tx = tx;
		this.rx = rx;
		this.include = include;
		this.exclude = exclude;
	}

	/**
	 * Defines the allowed set of requesting {@link Actor}s
	 *
	 * @param senders {@link Interaction}s that originate from these {@link Actor}s
	 *                will be matched
	 * @return A new {@link InteractionPredicate} based on <code>this</code>, but
	 *         with the new condition
	 */
	public InteractionPredicate from( Actor... senders ) {
		Set<Actor> nf = new TreeSet<>( comparing( Actor::name ) );
		Collections.addAll( nf, senders );
		return new InteractionPredicate( nf, rx, include, exclude );
	}

	/**
	 * Defines the allowed set of responding {@link Actor}s
	 *
	 * @param receivers interactions that destinate(?) at the {@link Actor}s will be
	 *                  matched
	 * @return A new {@link InteractionPredicate} based on <code>this</code>, but
	 *         with the new condition
	 */
	public InteractionPredicate to( Actor... receivers ) {
		Set<Actor> nt = new TreeSet<>( comparing( Actor::name ) );
		Collections.addAll( nt, receivers );
		return new InteractionPredicate( tx, nt, include, exclude );
	}

	/**
	 * Defines the set of tags that must be present
	 *
	 * @param tags interactions that bear all of these tags will be matched
	 * @return A new {@link InteractionPredicate} based on <code>this</code>, but
	 *         with the new condition
	 */
	public InteractionPredicate with( String... tags ) {
		Set<String> ni = new TreeSet<>();
		Collections.addAll( ni, tags );
		return new InteractionPredicate( tx, rx, ni, exclude );
	}

	/**
	 * Defines the set of tags that must be absent
	 *
	 * @param tags interactions that bear none of these tags will be matched
	 * @return A new {@link InteractionPredicate} based on <code>this</code>, but
	 *         with the new condition
	 */
	public InteractionPredicate without( String... tags ) {
		Set<String> ne = new TreeSet<>();
		Collections.addAll( ne, tags );
		return new InteractionPredicate( tx, rx, include, ne );
	}

	@Override
	public boolean test( Interaction t ) {
		boolean match = true;
		if( !tx.isEmpty() ) {
			match &= tx.contains( t.requester() );
		}
		if( !rx.isEmpty() ) {
			match &= rx.contains( t.responder() );
		}
		if( !include.isEmpty() ) {
			match &= include.stream().allMatch( t.tags()::contains );
		}
		if( !exclude.isEmpty() ) {
			match &= exclude.stream().noneMatch( t.tags()::contains );
		}
		return match;
	}

	@Override
	public String toString() {
		List<String> conditions = new ArrayList<>();
		if( !tx.isEmpty() ) {
			conditions.add( "from " + tx );
		}
		if( !rx.isEmpty() ) {
			conditions.add( "to " + rx );
		}
		if( !include.isEmpty() ) {
			conditions.add( "has tags " + include );
		}
		if( !exclude.isEmpty() ) {
			conditions.add( "lacks tags " + exclude );
		}

		if( conditions.isEmpty() ) {
			return "anything";
		}

		return conditions.stream().collect( joining( " and " ) );
	}
}
