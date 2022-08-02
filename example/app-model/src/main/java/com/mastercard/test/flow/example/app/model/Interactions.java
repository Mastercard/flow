package com.mastercard.test.flow.example.app.model;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;

/**
 * Utilities for working with the interactions in our system
 */
public class Interactions {

	private Interactions() {
		// no instances
	}

	/**
	 * Identifies all interactions
	 */
	public static final Predicate<Interaction> ALL = i -> true;

	/**
	 * Identifies interactions with the {@link Actors#WEB_UI}
	 */
	public static final Predicate<Interaction> WEB_UI = ntr -> ntr.responder() == Actors.WEB_UI;

	/**
	 * Identifies interactions with the {@link Actors#UI}
	 */
	public static final Predicate<Interaction> UI = ntr -> ntr.responder() == Actors.UI;

	/**
	 * Identifies interactions with the {@link Actors#CORE}
	 */
	public static final Predicate<Interaction> CORE = ntr -> ntr.responder() == Actors.CORE;

	/**
	 * Identifies interactions with the {@link Actors#QUEUE}
	 */
	public static final Predicate<Interaction> QUEUE = ntr -> ntr.responder() == Actors.QUEUE;

	/**
	 * Identifies interactions with the {@link Actors#STORE}
	 */
	public static final Predicate<Interaction> STORE = ntr -> ntr.responder() == Actors.STORE;

	/**
	 * Identifies interactions with the {@link Actors#HISTOGRAM}
	 */
	public static final Predicate<Interaction> HISTOGRAM = ntr -> ntr.responder() == Actors.HISTOGRAM;

	/**
	 * Changes actors in an existing interaction structure
	 *
	 * @param before The actor to replace
	 * @param after  The actor to replace with
	 * @return An operation that replaces one actor for another
	 */
	public static Consumer<MutableInteraction> replace( Actor before, Actor after ) {
		return i -> {
			if( i.requester() == before ) {
				i.requester( after );
			}
			if( i.responder() == before ) {
				i.responder( after );
			}
		};
	}
}
