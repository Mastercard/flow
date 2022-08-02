package com.mastercard.test.flow.builder.concrete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;

/**
 * An immutable implementation of {@link Interaction}
 */
public class ConcreteInteraction implements Interaction {

	private final ConcreteInteraction parent;
	private final Message request;
	private final Actor responder;
	private final Message response;
	private final Set<String> tags;
	private List<Interaction> children = new ArrayList<>();

	/**
	 * @param parent    The {@link Interaction} that caused this one
	 * @param request   The request content
	 * @param responder The {@link Actor} that receives the request and issues the
	 *                  response
	 * @param response  The response content
	 * @param tags      Descriptive tags
	 */
	public ConcreteInteraction( ConcreteInteraction parent, Message request,
			Actor responder, Message response, Set<String> tags ) {
		this.parent = parent;
		this.request = request;
		this.responder = responder;
		this.response = response;
		this.tags = Collections.unmodifiableSet( new TreeSet<>( tags ) );
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
	public Actor responder() {
		return responder;
	}

	@Override
	public Message response() {
		return response;
	}

	@Override
	public Interaction parent() {
		return parent;
	}

	@Override
	public Stream<Interaction> children() {
		return children.stream();
	}

	@Override
	public Set<String> tags() {
		return tags;
	}

	/**
	 * Adds a child interaction. This is only possible before {@link #complete()} is
	 * called.
	 *
	 * @param child The new child
	 * @return <code>this</code>
	 */
	public ConcreteInteraction with( Interaction child ) {
		children.add( child );
		return this;
	}

	/**
	 * Call this when all children have been populated
	 *
	 * @return <code>this</code>
	 */
	public ConcreteInteraction complete() {
		children = Collections.unmodifiableList( children );
		return this;
	}

}
