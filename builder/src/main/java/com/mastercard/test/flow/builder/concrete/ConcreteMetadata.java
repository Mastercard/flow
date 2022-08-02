package com.mastercard.test.flow.builder.concrete;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;

/**
 * Data attached to {@link Flow} s for human consumption
 */
public class ConcreteMetadata implements Metadata {

	private final String description;
	private final Set<String> tags;
	private final String motivation;
	private final String trace;

	/**
	 * @param description A short description of the transaction
	 * @param tags        A set of descriptive tags for the {@link Flow}
	 * @param motivation  A longer description of why the {@link Flow} exists
	 * @param trace       An identifier for where in the codebase the {@link Flow}
	 *                    is defined
	 */
	public ConcreteMetadata( String description, Collection<String> tags, String motivation,
			String trace ) {
		this.description = description;
		this.tags = Collections.unmodifiableSet( new TreeSet<>( tags ) );
		this.motivation = motivation;
		this.trace = trace;
	}

	@Override
	public String id() {
		return String.format( "%s %s", description, tags );
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public Set<String> tags() {
		return tags;
	}

	@Override
	public String motivation() {
		return motivation;
	}

	@Override
	public String trace() {
		return trace;
	}

}
