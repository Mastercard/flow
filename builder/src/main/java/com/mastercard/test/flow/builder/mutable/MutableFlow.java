package com.mastercard.test.flow.builder.mutable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.builder.SkipTrace;
import com.mastercard.test.flow.builder.concrete.ConcreteFlow;

/**
 * A builder for {@link ConcreteFlow}
 */
@SkipTrace
public class MutableFlow {

	private final Flow basis;
	private final MutableMetadata meta;
	private MutableRootInteraction root;
	private Set<Actor> implicit = new HashSet<>();
	private final List<MutableDependency> dependencies = new ArrayList<>();
	private final Map<Class<? extends Context>, Context> contexts = new HashMap<>();
	private final Map<Class<? extends Residue>, Residue> residues = new HashMap<>();

	/**
	 * Initially empty
	 */
	public MutableFlow() {
		basis = null;
		meta = new MutableMetadata();
	}

	/**
	 * @param basis The {@link Flow} to copy content from
	 */
	public MutableFlow( Flow basis ) {
		this.basis = basis;
		meta = new MutableMetadata( basis.meta() );
		root = Optional.ofNullable( basis.root() )
				.map( MutableRootInteraction::new )
				.orElse( null );
		basis.context()
				.forEach( c -> contexts.put( c.getClass(), c.child() ) );
		basis.residue()
				.forEach( r -> residues.put( r.getClass(), r.child() ) );
	}

	/**
	 * Updates {@link Flow} {@link Metadata}
	 *
	 * @param data How to update {@link Metadata}
	 * @return <code>this</code>
	 */
	public MutableFlow meta( Consumer<MutableMetadata> data ) {
		data.accept( meta );
		return this;
	}

	/**
	 * Basis accessor
	 *
	 * @return The {@link Flow} upon which this one is based
	 */
	public Flow basis() {
		return basis;
	}

	/**
	 * Set's the {@link Interaction} structure
	 *
	 * @param ntr The {@link Flow}'s call structure
	 * @return <code>this</code>
	 */
	public MutableFlow root( MutableRootInteraction ntr ) {
		root = ntr;
		return this;
	}

	/**
	 * Root interaction accessor
	 *
	 * @return The root {@link Interaction} of this {@link Flow}
	 */
	public MutableInteraction root() {
		return root;
	}

	/**
	 * Updates the set of system components that are implicitly required to support
	 * this {@link Flow}'s behaviour
	 *
	 * @param update How to update the set of implied {@link Actor}s
	 * @return <code>this</code>
	 */
	public MutableFlow implicit( Consumer<Set<Actor>> update ) {
		update.accept( implicit );
		return this;
	}

	/**
	 * Adds a {@link Dependency}
	 *
	 * @param dep A new {@link Dependency}
	 * @return <code>this</code>
	 */
	public MutableFlow dependency( MutableDependency dep ) {
		dependencies.add( dep );
		return this;
	}

	/**
	 * Adds a {@link Context}
	 *
	 * @param ctx A new {@link Context}
	 * @return <code>this</code>
	 */
	public MutableFlow context( Context ctx ) {
		contexts.put( ctx.getClass(), ctx );
		return this;
	}

	/**
	 * Updates an existing {@link Context}
	 *
	 * @param <T>    The {@link Context} type
	 * @param type   The {@link Context} type
	 * @param update How to change the context, or <code>null</code> to remove it
	 *               from the {@link Flow}
	 * @return <code>this</code>
	 */
	public <T extends Context> MutableFlow context( Class<T> type, Consumer<T> update ) {
		if( update == null ) {
			contexts.remove( type );
			return this;
		}

		@SuppressWarnings("unchecked")
		T ctx = (T) contexts.get( type );
		if( ctx == null ) {
			throw new IllegalStateException( "No such context for type " + type );
		}
		update.accept( ctx );
		return this;
	}

	/**
	 * Adds a {@link Residue}
	 *
	 * @param rsd A new {@link Residue}
	 * @return <code>this</code>
	 */
	public MutableFlow residue( Residue rsd ) {
		residues.put( rsd.getClass(), rsd );
		return this;
	}

	/**
	 * Updates an existing {@link Context}
	 *
	 * @param <T>    The {@link Context} type
	 * @param type   The {@link Context} type
	 * @param update How to change the context, or <code>null</code> to remove it
	 *               from the {@link Flow}
	 * @return <code>this</code>
	 */
	public <T extends Residue> MutableFlow residue( Class<T> type, Consumer<T> update ) {
		if( update == null ) {
			residues.remove( type );
			return this;
		}

		@SuppressWarnings("unchecked")
		T rsd = (T) residues.get( type );
		if( rsd == null ) {
			throw new IllegalStateException( "No such residue for type " + type );
		}
		update.accept( rsd );
		return this;
	}

	/**
	 * Builds an immutable copy of the current state
	 *
	 * @return An immutable {@link Flow} instance
	 */
	public Flow build() {
		ConcreteFlow flow = new ConcreteFlow( basis, meta.build(),
				root != null ? root.build() : null, implicit, contexts, residues );
		dependencies.forEach( d -> flow.with( d.build( flow ) ) );
		flow.complete();
		return flow;
	}
}
