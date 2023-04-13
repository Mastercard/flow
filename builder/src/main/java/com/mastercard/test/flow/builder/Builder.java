package com.mastercard.test.flow.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.builder.mutable.MutableDependency;
import com.mastercard.test.flow.builder.mutable.MutableFlow;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;
import com.mastercard.test.flow.builder.mutable.MutableMetadata;
import com.mastercard.test.flow.builder.mutable.MutableRootInteraction;
import com.mastercard.test.flow.builder.steps.Publisher;
import com.mastercard.test.flow.builder.steps.To;

/**
 * Common operations for building {@link Flow} instances
 *
 * @param <T> self type
 */
public abstract class Builder<T extends Builder<T>> {

	/**
	 * Value to use when creating a data dependency within the same {@link Flow}
	 */
	public static final Flow SELF = null;

	/**
	 * The {@link Flow} under construction
	 */
	protected final MutableFlow flow;

	/**
	 * @param flow The {@link Flow} to build on
	 */
	protected Builder( MutableFlow flow ) {
		this.flow = flow;
	}

	/**
	 * Defines flow metadata
	 *
	 * @param data How to define the {@link Flow}'s {@link Metadata}
	 * @return <code>this</code>
	 */
	public T meta( Consumer<MutableMetadata> data ) {
		flow.meta( data );
		return self();
	}

	/**
	 * Defines implied interactions
	 *
	 * @param data How to update the set of implicitly-involved {@link Actor}s
	 * @return <code>this</code>
	 */
	@SafeVarargs
	public final T implicit( Consumer<Set<Actor>>... data ) {
		for( Consumer<Set<Actor>> d : data ) {
			flow.implicit( d );
		}
		return self();
	}

	/**
	 * Updates interactions
	 *
	 * @param update What to do to the {@link Flow}'s {@link Interaction}s
	 * @return <code>this</code>
	 */
	public T interactions( Consumer<Stream<MutableInteraction>> update ) {
		update.accept(
				gatherInteractions( flow.root(), new ArrayList<>() )
						.stream() );
		return self();
	}

	/**
	 * Updates interactions
	 *
	 * @param ntr  Which {@link Interaction}s to update
	 * @param data What to do to those {@link Interaction}s
	 * @return <code>this</code>
	 */
	@SafeVarargs
	public final T update( Predicate<Interaction> ntr, Consumer<MutableInteraction>... data ) {
		Consumer<MutableInteraction> combined = Stream.of( data )
				.reduce( i -> {
					// no-op
				}, Consumer::andThen );

		return interactions( s -> s.filter( ntr ).forEach( combined ) );
	}

	/**
	 * Adds an interaction
	 *
	 * @param ntr   Which {@link Interaction}s to add new child {@link Interaction}s
	 *              to
	 * @param index The index in the targeted {@link Interaction}'s child list to
	 *              add to
	 * @param call  The structure of the new child {@link Interaction}s
	 * @return <code>this</code>
	 */
	public T addCall( Predicate<Interaction> ntr, int index,
			Function<To<MutableInteraction>, MutableInteraction> call ) {
		return update( ntr, mi -> mi.call( index, call ) );
	}

	/**
	 * Removes an interaction
	 *
	 * @param toRemove Which {@link Interaction}s to remove
	 * @return <code>this</code>
	 */
	public T removeCall( Predicate<Interaction> toRemove ) {
		return update( i -> true, mi -> mi.children( l -> l.removeIf( toRemove ) ) );
	}

	/**
	 * Updates the {@link Flow} to have a new root {@link Interaction}, chosen from
	 * the existing structure
	 *
	 * @param newRoot returns <code>true</code> for the interaction that should be
	 *                the new root
	 * @return <code>this</code>
	 */
	public T subset( Predicate<Interaction> newRoot ) {
		AtomicReference<MutableInteraction> nrr = new AtomicReference<>();
		update( newRoot, nrr::set );
		MutableInteraction nr = nrr.get();
		if( nr != null && nr != flow.root() ) {
			flow.root( new MutableRootInteraction( nr ) );
		}
		return self();
	}

	/**
	 * Updates the {@link Flow} to have a new root interaction that provokes the
	 * existing structure
	 *
	 * @param newRoot  The new root actor
	 * @param request  The message that provokes the existing behaviour
	 * @param response The result of the existing behaviour
	 * @return <code>this</code>
	 */
	public T superset( Actor newRoot, Message request, Message response ) {
		MutableRootInteraction nr = new MutableRootInteraction();
		nr.requester( newRoot )
				.request( request )
				.responder( flow.root().requester() )
				.response( response )
				.children( cl -> cl.add( flow.root() ) );
		flow.root( nr );

		return self();
	}

	/**
	 * Adds an interaction
	 *
	 * @param ntr  Which {@link Interaction}s to add new child {@link Interaction}s
	 *             to
	 * @param call The structure of the new child {@link Interaction}s
	 * @return <code>this</code>
	 */
	public T addCall( Predicate<Interaction> ntr,
			Function<To<MutableInteraction>, MutableInteraction> call ) {
		return addCall( ntr, -1, call );
	}

	private static List<MutableInteraction> gatherInteractions( MutableInteraction ntr,
			List<MutableInteraction> list ) {
		list.add( ntr );
		ntr.mutableChildren()
				.forEach( child -> gatherInteractions( child, list ) );
		return list;
	}

	/**
	 * Adds a prerequisite to the {@link Flow}
	 *
	 * @param pre The {@link Flow} that should be processed before this one
	 * @return <code>this</code>
	 */
	public T prerequisite( Flow pre ) {
		flow.dependency( new MutableDependency()
				// no data transfer, just an ordering constraint
				.source( s -> s.flow( pre ) ) );
		return self();
	}

	/**
	 * Adds a data dependency to the {@link Flow}
	 *
	 * @param prerequisite The {@link Flow} that should be processed before this one
	 * @param subscription How to copy data from the prerequisite {@link Flow} to
	 *                     this one
	 * @return <code>this</code>
	 * @see #SELF
	 */
	public T dependency( Flow prerequisite,
			Function<Publisher<T>, T> subscription ) {
		MutableDependency dep = new MutableDependency().source( a -> a.flow( prerequisite ) );
		Publisher<T> pub = new Publisher<>( dep, self() );
		T ret = subscription.apply( pub );
		if( ret != this ) {
			throw new IllegalStateException( "Failed to return to origin" );
		}
		flow.dependency( dep );
		return self();
	}

	/**
	 * Adds a context. Note that only one instance of a given context type can be
	 * present on the {@link Flow}
	 *
	 * @param ctx The new context
	 * @return <code>this</code>
	 */
	public T context( Context ctx ) {
		flow.context( ctx );
		return self();
	}

	/**
	 * Updates an existing context
	 *
	 * @param <C>    context type
	 * @param type   context type
	 * @param update context update, or <code>null</code> to remove the context
	 * @return <code>this</code>
	 */
	public <C extends Context> T context( Class<C> type, Consumer<C> update ) {
		flow.context( type, update );
		return self();
	}

	/**
	 * Adds a residue. Note that only one instance of a given residue type can be
	 * present on the {@link Flow}
	 *
	 * @param rsd The new residue
	 * @return <code>this</code>
	 */
	public T residue( Residue rsd ) {
		flow.residue( rsd );
		return self();
	}

	/**
	 * Updates an existing residue
	 *
	 * @param <C>    residue type
	 * @param type   residue type
	 * @param update residue update, or <code>null</code> to remove the residue
	 * @return <code>this</code>
	 */
	public <C extends Residue> T residue( Class<C> type, Consumer<C> update ) {
		flow.residue( type, update );
		return self();
	}

	/**
	 * Typed self-reference accessor
	 *
	 * @return <code>this</code>
	 */
	@SuppressWarnings("unchecked")
	protected T self() {
		return (T) this;
	}
}
