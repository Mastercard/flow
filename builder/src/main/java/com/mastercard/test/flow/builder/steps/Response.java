package com.mastercard.test.flow.builder.steps;

import java.util.function.Function;

import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;

/**
 * The final stage of defining an {@link Interaction} - defining what happens as
 * a result of the request being processed
 *
 * @param <R> The type that initiated the call sequence
 */
public class Response<R> {

	private final R returnTo;
	private final MutableInteraction build;

	/**
	 * @param returnTo The object to return control to when the call sequence is
	 *                 complete
	 * @param build    The interaction that we're building
	 */
	Response( R returnTo, MutableInteraction build ) {
		this.returnTo = returnTo;
		this.build = build;
	}

	/**
	 * The request has triggered a another
	 *
	 * @param call How to define the child request
	 * @return <code>this</code>
	 */
	public Response<R> call( Function<To<Response<R>>, Response<R>> call ) {
		MutableInteraction ntr = new MutableInteraction( build );
		To<Response<R>> c = new To<>( this, ntr );
		Response<R> ret = call.apply( c );
		if( ret != this ) {
			throw new IllegalStateException( "Failed to return to origin" );
		}
		build.children( l -> l.add( ntr ) );
		return ret;
	}

	/**
	 * Sets the content of the interaction response
	 *
	 * @param msg the response to the request
	 * @return the call initiator
	 */
	public R response( Message msg ) {
		build.response( msg );
		return returnTo;
	}
}
