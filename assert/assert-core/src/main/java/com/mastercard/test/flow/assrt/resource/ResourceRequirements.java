package com.mastercard.test.flow.assrt.resource;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable resolved ownership and the named declarations that produced it. */
public final class ResourceRequirements {
	private final Set<String> keys;
	private final Set<String> rules;
	private final Set<String> isolationRules;
	private final boolean exclusive;
	private final boolean unknown;

	/**
	 * Snapshots a resolved rule union without retaining mutable declaration sets.
	 *
	 * @param keys      Capacity-one resource identities
	 * @param rules     Matching rule names in declaration order; empty means
	 *                  unknown
	 * @param exclusive Whether a matching rule requires global exclusion
	 */
	ResourceRequirements( Set<String> keys, Set<String> rules, boolean exclusive ) {
		this( keys, rules, exclusive, rules.isEmpty(), Set.of() );
	}

	private ResourceRequirements( Set<String> keys, Set<String> rules, boolean exclusive,
			boolean unknown, Set<String> isolationRules ) {
		this.keys = Collections.unmodifiableSet( new LinkedHashSet<>( keys ) );
		this.rules = Collections.unmodifiableSet( new LinkedHashSet<>( rules ) );
		this.isolationRules = Collections.unmodifiableSet( new LinkedHashSet<>( isolationRules ) );
		this.exclusive = exclusive;
		this.unknown = unknown;
	}

	/**
	 * @param members        Selected members' resolved requirements
	 * @param exclusive      Whether the whole interval excludes outside work
	 * @param isolationRules Matching whole-chain audit names, not member
	 *                       classification
	 * @return Whole-unit union; any unknown member remains unknown
	 */
	static ResourceRequirements union( List<ResourceRequirements> members, boolean exclusive,
			Set<String> isolationRules ) {
		Set<String> keys = new LinkedHashSet<>();
		Set<String> names = new LinkedHashSet<>();
		boolean unknown = false;
		for( ResourceRequirements member : members ) {
			keys.addAll( member.keys );
			names.addAll( member.rules );
			exclusive |= member.exclusive();
			unknown |= member.unknown();
		}
		return new ResourceRequirements( keys, names, exclusive, unknown, isolationRules );
	}

	/** @return Capacity-one identities of actual shared mutable state */
	public Set<String> keys() {
		return keys;
	}

	/**
	 * Adds an ownership footprint without classifying an otherwise UNKNOWN flow.
	 *
	 * @param other Additional mandatory ownership, such as a fixture domain
	 * @return The complete immutable union, retaining both audit sets
	 */
	public ResourceRequirements plus( ResourceRequirements other ) {
		Set<String> isolation = new LinkedHashSet<>( isolationRules );
		isolation.addAll( other.isolationRules );
		return union( List.of( this, other ), false, isolation );
	}

	/**
	 * @return Matching resource rule names, excluding whole-chain isolation audits
	 */
	public Set<String> rules() {
		return rules;
	}

	/**
	 * @return Whole-chain isolation audit names, separate from member
	 *         classification
	 */
	public Set<String> isolationRules() {
		return isolationRules;
	}

	/** @return Whether any represented flow remains unclassified */
	public boolean unknown() {
		return unknown;
	}

	/**
	 * @return Whether this work conflicts with all cooperating use, even empty sets
	 */
	public boolean exclusive() {
		return unknown() || exclusive;
	}

	@Override
	public String toString() {
		return (unknown() ? "UNKNOWN" : exclusive ? "EXCLUSIVE" : "KNOWN")
				+ " keys=" + keys + "; rules=" + rules + "; isolationRules=" + isolationRules;
	}
}
