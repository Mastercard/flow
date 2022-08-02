package com.mastercard.test.flow.builder.concrete;

import java.util.Set;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Message;

/**
 * A specialised sub-type of {@link ConcreteInteraction}, in that it:
 * <ul>
 * <li>Doesn't have a parent interaction</li>
 * <li>Holds the requester actor directly, rather than relying on the parent
 * interaction that it doesn't have</li>
 * </ul>
 */
public class ConcreteRootInteraction extends ConcreteInteraction {

	private final Actor root;

	/**
	 * @param root      The actor that sends the requyest
	 * @param request   The request content
	 * @param responder The actor that recieves the request
	 * @param response  The response content
	 * @param tags      tag values
	 */
	public ConcreteRootInteraction( Actor root, Message request, Actor responder,
			Message response, Set<String> tags ) {
		super( null, request, responder, response, tags );
		this.root = root;
	}

	@Override
	public Actor requester() {
		return root;
	}
}
