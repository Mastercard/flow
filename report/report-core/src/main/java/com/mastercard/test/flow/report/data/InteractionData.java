package com.mastercard.test.flow.report.data;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;

/**
 * Encapsulates the data of {@link Interaction}
 */
public class InteractionData {

	/**
	 * The name of the actor that sends the request
	 */
	@JsonProperty("requester")
	public final String requester;

	/**
	 * The name of the actor that sends the response
	 */
	@JsonProperty("responder")
	public final String responder;

	/**
	 * {@link Interaction} tags
	 */
	@JsonProperty("tags")
	public final Set<String> tags;

	/**
	 * The request
	 */
	@JsonProperty("request")
	public final TransmissionData request;
	/**
	 * The response
	 */
	@JsonProperty("response")
	public final TransmissionData response;

	/**
	 * Child interactions
	 */
	@JsonProperty("children")
	public final List<InteractionData> children;

	/**
	 * The interaction from which this data is extracted
	 */
	@JsonIgnore
	public final Interaction peer;

	/**
	 * @param requester Requesting {@link Actor}
	 * @param responder Responding {@link Actor}
	 * @param tags      metadata
	 * @param request   Request {@link Message}
	 * @param response  Response {@link Message}
	 * @param children  Consequences
	 */
	public InteractionData(
			@JsonProperty("requester") String requester,
			@JsonProperty("responder") String responder,
			@JsonProperty("tags") Set<String> tags,
			@JsonProperty("request") TransmissionData request,
			@JsonProperty("response") TransmissionData response,
			@JsonProperty("children") List<InteractionData> children ) {
		this.requester = requester;
		this.responder = responder;
		this.tags = tags;
		this.request = request;
		this.response = response;
		this.children = children;
		peer = null;
	}

	/**
	 * @param ntr The interaction to capture
	 */
	public InteractionData( Interaction ntr ) {
		peer = ntr;
		requester = ntr.requester().name();
		responder = ntr.responder().name();
		tags = new TreeSet<>( ntr.tags() );
		request = new TransmissionData( ntr.request() );
		response = new TransmissionData( ntr.response() );

		children = ntr.children()
				.map( InteractionData::new )
				.collect( Collectors.toList() );
	}

	/**
	 * Applies data updates
	 *
	 * @param target returns true for interactions to update
	 * @param update How to update appropriate interactions
	 */
	public void update( Predicate<InteractionData> target, Consumer<InteractionData> update ) {
		if( target.test( this ) ) {
			update.accept( this );
		}
		children.forEach( c -> c.update( target, update ) );
	}
}
