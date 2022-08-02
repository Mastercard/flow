package com.mastercard.test.flow.builder.steps;

import java.util.Set;
import java.util.function.Consumer;

import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;
import com.mastercard.test.flow.util.Tags;

/**
 * The third stage of defining an interaction - defining interaction tags and
 * request content
 *
 * @param <R> The type that initiated the call sequence
 */
public class Request<R> {

	private final R returnTo;
	private final MutableInteraction build;

	/**
	 * @param returnTo The object to return control to when the call sequence is
	 *                 complete
	 * @param build    The interaction that we're building
	 */
	Request( R returnTo, MutableInteraction build ) {
		this.returnTo = returnTo;
		this.build = build;
	}

	/**
	 * Updates the tags on the interaction
	 *
	 * @param tags How to update the {@link Interaction#tags()}
	 * @return <code>this</code>
	 * @see Tags
	 */
	public Request<R> tags( Consumer<Set<String>> tags ) {
		build.tags( tags );
		return this;
	}

	/**
	 * Sets the content of the interaction request
	 *
	 * @param msg The request content
	 * @return the next stage of building the interaction
	 */
	public Response<R> request( Message msg ) {
		build.request( msg );
		return new Response<>( returnTo, build );
	}
}
