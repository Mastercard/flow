package com.mastercard.test.flow.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Model;

/**
 * Utility for processing dependencies as tests results become available
 */
public class Dependencies {

	/**
	 * A map from {@link Flow}s to the dependencies that flow from them
	 */
	private final Map<Flow, List<Dependency>> publishers = new HashMap<>();

	/**
	 * @param model The system model
	 */
	public Dependencies( Model model ) {
		model.flows()
				.flatMap( Flow::dependencies )
				.filter( d -> d.source().isComplete() && d.sink().isComplete() )
				.forEach( dep -> publishers
						.computeIfAbsent( dep.source().flow(), f -> new ArrayList<>() )
						.add( dep ) );
	}

	/**
	 * Called when an interaction has been processed against the system under test
	 *
	 * @param flow  The {@link Flow} that contains the {@link Interaction}
	 * @param ntr   The {@link Interaction} that has been processed
	 * @param msg   The {@link Message} for which we have actual content
	 * @param bytes The actual content as observed in the system
	 * @return The bytes, parsed as the expected message type
	 */
	public Message publish( Flow flow, Interaction ntr, Message msg, byte[] bytes ) {
		List<Dependency> dependencies = publishers.getOrDefault( flow, emptyList() )
				.stream()
				.filter( dep -> dep.source().getInteraction().filter( ntr::equals ).isPresent() )
				.filter( dep -> dep.source().getMessage().filter( msg::equals ).isPresent() )
				.collect( Collectors.toList() );
		try {
			Message actual = msg.peer( bytes );

			for( Dependency dependency : dependencies ) {
				Object value = actual.get( dependency.source().field() );
				Object mutated = dependency.mutation().apply( value );
				dependency.sink().getMessage()
						.ifPresent( m -> m.set( dependency.sink().field(), mutated ) );
			}

			return actual;
		}
		catch( Exception e ) {
			// we've harvested bad data from the system, let's give as much information as
			// possible in the exception
			throw new IllegalArgumentException(
					String.format( "Failed to parse %s->%s %s %s from"
							+ "\nUTF8:[%s]"
							+ "\n hex:[%s]",
							ntr.requester().name(), ntr.responder().name(), ntr.tags(),
							msg.getClass().getSimpleName(),
							new String( bytes, UTF_8 ), Bytes.toHex( bytes ) ),
					e );
		}
	}
}
