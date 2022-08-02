package com.mastercard.test.flow.builder;

import static java.util.Comparator.comparing;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;

/**
 * Convenient superclass for {@link Context} implementations
 */
public abstract class AbstractContext implements Context {

	private final String name;
	private final Set<Actor> domain;

	/**
	 * @param name   A human-readable name for this context
	 * @param domain The set of actors for which this context is applicable
	 */
	protected AbstractContext( String name, Actor... domain ) {
		this.name = name;
		TreeSet<Actor> set = new TreeSet<>( comparing( Actor::name ) );
		Collections.addAll( set, domain );
		this.domain = Collections.unmodifiableSet( set );
	}

	/**
	 * Copy constructor. Useful in your {@link #child()} implementations
	 * 
	 * @param parent The {@link Context} to copy from
	 */
	protected AbstractContext( AbstractContext parent ) {
		name = parent.name;
		domain = parent.domain();
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public Set<Actor> domain() {
		return domain;
	}
}
