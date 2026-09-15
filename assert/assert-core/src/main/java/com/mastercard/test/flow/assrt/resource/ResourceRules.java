package com.mastercard.test.flow.assrt.resource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.mastercard.test.flow.Flow;

/**
 * Named bulk declarations. Resolve once per selected, dependency-expanded flow.
 */
public final class ResourceRules {
	private final Map<String, Rule> rules = new LinkedHashMap<>();

	/**
	 * Declares a known set; an empty set is an affirmative independence audit.
	 *
	 * @param name    Unique nonblank diagnostic name
	 * @param matches Flows covered by the declaration
	 * @param keys    Equal strings must identify the same actual mutable state
	 * @return this configuration
	 */
	public ResourceRules resources( String name, Predicate<Flow> matches, String... keys ) {
		return add( name, matches, false, keys );
	}

	/**
	 * Declares global exclusivity, including against resource-independent work.
	 *
	 * @param name    Unique nonblank diagnostic name
	 * @param matches Flows requiring exclusive use
	 * @return this configuration
	 */
	public ResourceRules exclusive( String name, Predicate<Flow> matches ) {
		return add( name, matches, true );
	}

	private ResourceRules add( String name, Predicate<Flow> matches, boolean exclusive,
			String... keys ) {
		if( Objects.requireNonNull( name ).isBlank() || rules.containsKey( name ) ) {
			throw new IllegalArgumentException( "Resource rule must be named and unique: " + name );
		}
		Set<String> identities = new LinkedHashSet<>();
		for( String key : keys ) {
			if( Objects.requireNonNull( key ).isBlank() ) {
				throw new IllegalArgumentException( "Resource key must not be blank" );
			}
			identities.add( key );
		}
		rules.put( name, new Rule( Objects.requireNonNull( matches ), identities, exclusive ) );
		return this;
	}

	/**
	 * Evaluates every predicate exactly once, without mutating the flow or its
	 * tags.
	 *
	 * @param flow The selected flow
	 * @return Union of all matching declarations; absence is UNKNOWN, not empty
	 */
	public ResourceRequirements resolve( Flow flow ) {
		Set<String> keys = new LinkedHashSet<>();
		Set<String> names = new LinkedHashSet<>();
		boolean exclusive = false;
		for( Map.Entry<String, Rule> entry : rules.entrySet() ) {
			Rule rule = entry.getValue();
			if( rule.matches.test( flow ) ) {
				names.add( entry.getKey() );
				keys.addAll( rule.keys );
				exclusive |= rule.exclusive;
			}
		}
		return new ResourceRequirements( keys, names, exclusive );
	}

	private static final class Rule {
		final Predicate<Flow> matches;
		final Set<String> keys;
		final boolean exclusive;

		Rule( Predicate<Flow> matches, Set<String> keys, boolean exclusive ) {
			this.matches = matches;
			this.keys = keys;
			this.exclusive = exclusive;
		}
	}
}
