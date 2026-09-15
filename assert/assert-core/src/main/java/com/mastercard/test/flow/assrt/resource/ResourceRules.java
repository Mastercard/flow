package com.mastercard.test.flow.assrt.resource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
	private final Map<String, Set<String>> isolatedChains = new LinkedHashMap<>();

	/**
	 * Audits entire named chains for outside overlap. This does not classify any
	 * member's resources: UNKNOWN and exclusive members still exclude all work.
	 *
	 * @param name   Unique nonblank audit name
	 * @param chains Existing chain tag suffixes, without the chain: prefix
	 * @return this configuration
	 */
	public ResourceRules isolatedChains( String name, String... chains ) {
		validateName( name );
		Set<String> names = new LinkedHashSet<>();
		for( String chain : chains ) {
			if( Objects.requireNonNull( chain ).isBlank() )
				throw new IllegalArgumentException( "Isolated chain name must not be blank" );
			names.add( chain );
		}
		if( names.isEmpty() )
			throw new IllegalArgumentException( "A whole-chain audit must name chains" );
		isolatedChains.put( name, names );
		return this;
	}

	/**
	 * Freezes selected membership and whole-chain isolation without rerunning
	 * rules.
	 *
	 * @param flows        Selected flows in canonical order
	 * @param requirements Previously resolved member requirements in the same order
	 * @return Whole-unit reservation plan
	 */
	public ChainPlan chains( List<Flow> flows, List<ResourceRequirements> requirements ) {
		Map<String, Set<String>> isolated = new LinkedHashMap<>();
		isolatedChains.forEach( ( rule, names ) -> names.forEach( name -> isolated
				.computeIfAbsent( name, key -> new LinkedHashSet<>() ).add( rule ) ) );
		return new ChainPlan( flows, requirements, isolated );
	}

	private void validateName( String name ) {
		if( Objects.requireNonNull( name ).isBlank() || rules.containsKey( name )
				|| isolatedChains.containsKey( name ) )
			throw new IllegalArgumentException( "Resource rule must be named and unique: " + name );
	}

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
		validateName( name );
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
