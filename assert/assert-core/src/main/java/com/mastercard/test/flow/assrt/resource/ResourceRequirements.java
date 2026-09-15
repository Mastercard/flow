package com.mastercard.test.flow.assrt.resource;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable resolved ownership and the named declarations that produced it. */
public final class ResourceRequirements {
	private final Set<String> keys;
	private final Set<String> rules;
	private final boolean exclusive;

	/**
	 * Snapshots a resolved rule union without retaining mutable declaration sets.
	 *
	 * @param keys      Capacity-one resource identities
	 * @param rules     Matching rule names in declaration order; empty means
	 *                  unknown
	 * @param exclusive Whether a matching rule requires global exclusion
	 */
	ResourceRequirements( Set<String> keys, Set<String> rules, boolean exclusive ) {
		this.keys = Collections.unmodifiableSet( new LinkedHashSet<>( keys ) );
		this.rules = Collections.unmodifiableSet( new LinkedHashSet<>( rules ) );
		this.exclusive = exclusive;
	}

	/** @return Capacity-one identities of actual shared mutable state */
	public Set<String> keys() {
		return keys;
	}

	/** @return All matching rule names, in declaration order */
	public Set<String> rules() {
		return rules;
	}

	/** @return Whether no rule classified this work */
	public boolean unknown() {
		return rules.isEmpty();
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
				+ " keys=" + keys + "; rules=" + rules;
	}
}
