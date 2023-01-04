
package com.mastercard.test.flow.builder.concrete;

import static java.util.Comparator.comparing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.util.Flows;

/**
 * An immutable implementation of {@link Flow}
 */
public class ConcreteFlow implements Flow {

	private final Flow basis;
	private final ConcreteMetadata metadata;
	private final ConcreteRootInteraction root;
	private final Set<Actor> implicit = new TreeSet<>( comparing( Actor::name ) );
	private List<Dependency> dependencies = new ArrayList<>();
	private final List<Context> contexts = new ArrayList<>();
	private final List<Residue> residue = new ArrayList<>();

	/**
	 * @param basis    The {@link Flow} upon which this one is based
	 * @param metadata human-readable data for this {@link Flow}
	 * @param root     The root {@link Interaction} of the {@link Flow}
	 * @param implicit The {@link Actor}s that support this {@link Flow}'s
	 *                 behaviour, but that are not modelled in the
	 *                 {@link Interaction} structure
	 * @param contexts The {@link Context}s in which this {@link Flow} is valid
	 * @param residue  The {@link Residue} that we should expact after the
	 *                 {@link Flow} has been processed
	 */
	public ConcreteFlow( Flow basis, ConcreteMetadata metadata, ConcreteRootInteraction root,
			Set<Actor> implicit, Map<Class<? extends Context>, Context> contexts,
			Map<Class<? extends Residue>, Residue> residue ) {
		this.metadata = metadata;
		this.basis = basis;
		this.root = root;
		if( implicit != null ) {
			this.implicit.addAll( implicit );
		}
		if( contexts != null ) {
			this.contexts.addAll( contexts.values() );
		}
		if( residue != null ) {
			this.residue.addAll( residue.values() );
		}
	}

	@Override
	public Metadata meta() {
		return metadata;
	}

	@Override
	public Interaction root() {
		return root;
	}

	@Override
	public Stream<Actor> implicit() {
		return implicit.stream();
	}

	@Override
	public Flow basis() {
		return basis;
	}

	@Override
	public Stream<Dependency> dependencies() {
		return dependencies.stream();
	}

	/**
	 * Adds a {@link Dependency}. This is only possible before {@link #complete()}
	 * is called.
	 *
	 * @param dependency The new {@link Dependency}
	 * @return <code>this</code>
	 */
	public ConcreteFlow with( ConcreteDependency dependency ) {
		dependencies.add( dependency );
		// invoke the dependency now to copy statically-known content
		if( dependency.source() != null && dependency.source().isComplete()
				&& dependency.sink() != null && dependency.sink().isComplete() ) {

			// this is where malformed dependencies will show themselves, so let's produce
			// helpful failures
			Object value = dependency.source().getValue()
					.orElseThrow( () -> new IllegalStateException( String.format(
							"Failed to find dependency source field '%s' in:\n"
									+ "  flow:%s\n"
									+ "   msg:%s",
							dependency.source().field(),
							Flows.structure( dependency.source().flow() ),
							dependency.source().getMessage()
									.map( Message::assertable )
									.orElse( null ) ) ) );

			Message msg = dependency.sink().getMessage()
					.orElseThrow( () -> new IllegalStateException( String.format(
							"Failed to find dependency sink message in:\n  %s",
							Flows.structure( dependency.sink().flow() ) ) ) );

			String field = dependency.sink().field();
			msg.set( field, value );
		}
		return this;
	}

	/**
	 * Call this when all dependencies has been populated
	 *
	 * @return <code>this</code>
	 */
	public ConcreteFlow complete() {
		dependencies = Collections.unmodifiableList( dependencies );
		return this;
	}

	@Override
	public Stream<Context> context() {
		return contexts.stream();
	}

	@Override
	public Stream<Residue> residue() {
		return residue.stream();
	}
}
