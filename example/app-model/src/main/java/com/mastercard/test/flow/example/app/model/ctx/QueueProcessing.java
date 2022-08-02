package com.mastercard.test.flow.example.app.model.ctx;

import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.QUEUE;
import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.example.app.Queue;

/**
 * Controls aspects of {@link Queue} background processing
 */
public class QueueProcessing implements Context {

	private static final Set<Actor> DOMAIN = Stream.of( QUEUE )
			.collect( toSet() );

	private boolean active = true;
	private boolean cleared = false;
	private boolean exhausted = false;

	/**
	 * @return Whether the queue is processing items
	 */
	@JsonProperty("active")
	public boolean active() {
		return active;
	}

	/**
	 * @param e Whether the queue is processing items
	 * @return this
	 */
	@JsonProperty("active")
	public QueueProcessing active( boolean e ) {
		active = e;
		return this;
	}

	/**
	 * @return Whether the queue should be cleared
	 */
	@JsonProperty("cleared")
	public boolean cleared() {
		return cleared;
	}

	/**
	 * @param c Whether the queue should be cleared when this context is applied
	 * @return this
	 */
	@JsonProperty("cleared")
	public QueueProcessing cleared( boolean c ) {
		cleared = c;
		return this;
	}

	/**
	 * @return Whether execution should wait until the queue naturally empties
	 *         itself
	 */
	@JsonProperty("exhausted")
	public boolean exhausted() {
		return exhausted;
	}

	/**
	 * @param e Whether execution should wait until the queue naturally empties
	 *          itself
	 * @return this
	 */
	@JsonProperty("exhausted")
	public QueueProcessing exhausted( boolean e ) {
		exhausted = e;
		return this;
	}

	@Override
	public String name() {
		return "Queue processing";
	}

	@Override
	public Set<Actor> domain() {
		return DOMAIN;
	}

	@Override
	public QueueProcessing child() {
		return new QueueProcessing()
				.active( active )
				.cleared( cleared )
				.exhausted( exhausted );
	}

}
