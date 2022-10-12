package com.mastercard.test.flow.builder.mutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.concrete.ConcreteInteraction;
import com.mastercard.test.flow.builder.steps.To;
import com.mastercard.test.flow.util.Tags;

/**
 * A builder for {@link ConcreteInteraction}
 */
public class MutableInteraction implements Interaction {

	private final MutableInteraction parent;
	private Message request;
	private Actor responder;
	private Message response;
	private final Set<String> tags = new TreeSet<>();
	private final List<MutableInteraction> children = new ArrayList<>();

	/**
	 * @param parent The interaction that causes this one
	 */
	public MutableInteraction( MutableInteraction parent ) {
		this.parent = parent;
	}

	/**
	 * @param parent The interaction that causes this one
	 * @param src    The interaction to inherit state from
	 */
	public MutableInteraction( MutableInteraction parent, Interaction src ) {
		this.parent = parent;
		request( src.request() );
		responder = src.responder();
		response( src.response() );
		tags.addAll( src.tags() );
		src.children().forEach( c -> children.add( new MutableInteraction( this, c ) ) );
	}

	/**
	 * Sets the {@link Actor} that initiates this {@link Interaction}
	 *
	 * @param r The new requester
	 * @return <code>this</code>
	 */
	public MutableInteraction requester( Actor r ) {
		parent.responder( r );
		return this;
	}

	/**
	 * Sets the request content
	 *
	 * @param r Request content
	 * @return <code>this</code>
	 */
	public MutableInteraction request( Message r ) {
		request = Optional.ofNullable( r )
				.map( Message::child )
				.orElse( null );
		return this;
	}

	/**
	 * Sets the {@link Actor} that is the target of this {@link Interaction}
	 *
	 * @param r The new responder
	 * @return <code>this</code>
	 */
	public MutableInteraction responder( Actor r ) {
		responder = r;
		return this;
	}

	@Override
	public Actor responder() {
		return responder;
	}

	/**
	 * Sets the response content
	 *
	 * @param r Response content
	 * @return <code>this</code>
	 */
	public MutableInteraction response( Message r ) {
		response = Optional.ofNullable( r )
				.map( Message::child )
				.orElse( null );
		return this;
	}

	/**
	 * Updates tags
	 *
	 * @param t how to update tag values
	 * @return <code>this</code>
	 * @see Tags
	 */
	public MutableInteraction tags( Consumer<Set<String>> t ) {
		t.accept( tags );
		return this;
	}

	/**
	 * Updates the consequences of this {@link Interaction}
	 *
	 * @param update How to update child {@link Interaction}s
	 * @return <code>this</code>
	 */
	public MutableInteraction children( Consumer<List<MutableInteraction>> update ) {
		update.accept( children );
		return this;
	}

	/**
	 * Adds a new consequence of this {@link Interaction}
	 *
	 * @param index the index in the child list to insert the new call. Negative
	 *              values can be supplied (e.g.: -1 means adds to the end of the
	 *              list, -2 inserts in the penultimate location, etc
	 * @param to    Call sequence, returning to this object
	 * @return <code>this</code>
	 */
	public MutableInteraction call( int index,
			Function<To<MutableInteraction>, MutableInteraction> to ) {
		MutableInteraction ntr = new MutableInteraction( this );
		To<MutableInteraction> call = new To<>( this, ntr );
		MutableInteraction mi = to.apply( call );
		if( mi != this ) {
			throw new IllegalStateException( "Failed to return to origin" );
		}
		return children( c -> {
			int idx = index;
			if( idx < 0 ) {
				idx = c.size() + 1 + idx;
			}
			idx = Math.max( idx, 0 );
			idx = Math.min( idx, c.size() );
			c.add( idx, ntr );
		} );
	}

	/**
	 * Adds a call to the end of this {@link Interaction}'s child list
	 *
	 * @param to Call sequence, returning to this object
	 * @return <code>this</code>
	 */
	public MutableInteraction call( Function<To<MutableInteraction>, MutableInteraction> to ) {
		return call( -1, to );
	}

	/**
	 * @param p The {@link Interaction} that caused this one
	 * @return An immutable {@link Interaction} instance
	 */
	public ConcreteInteraction build( ConcreteInteraction p ) {
		return addChildren( new ConcreteInteraction(
				p, request, responder, response, tags ) );
	}

	/**
	 * Builds the child interactions and adds them to the supplied instance
	 *
	 * @param <T>   Instance type
	 * @param built The instance to populate
	 * @return Populated instance
	 */
	protected <T extends ConcreteInteraction> T addChildren( T built ) {
		children.forEach( c -> built.with( c.build( built ) ) );
		built.complete();
		return built;
	}

	@Override
	public Actor requester() {
		return parent().responder();
	}

	@Override
	public Message request() {
		return request;
	}

	@Override
	public Message response() {
		return response;
	}

	@Override
	public Interaction parent() {
		return parent;
	}

	/**
	 * Consequent interaction accessor
	 *
	 * @return A stream of the children of this {@link Interaction}
	 */
	public Stream<MutableInteraction> mutableChildren() {
		return children.stream();
	}

	@Override
	public Stream<Interaction> children() {
		return mutableChildren()
				.map( Interaction.class::cast );
	}

	@Override
	public Set<String> tags() {
		return tags;
	}
}
