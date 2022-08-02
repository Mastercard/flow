package com.mastercard.test.flow.example.app.assrt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operations;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.Flows;

/**
 * The behaviour of the {@link MockInstance} - captures request data and maps
 * that to response data from the system {@link Model}
 */
public class MockService implements Function<byte[], byte[]> {

	/**
	 * The classnames of the system component interfaces
	 */
	protected static final Set<String> SERVICE_CLASS_NAMES;
	/**
	 * Map from regexes that match request paths to the {@link Actor} that services
	 * such requests
	 */
	protected static final Map<Pattern, Actor> REQUEST_PATH_ACTORS;
	static {
		SERVICE_CLASS_NAMES = Stream.of( Actors.values() )
				.map( a -> a.service )
				.filter( Objects::nonNull )
				.map( Class::getName )
				.collect( Collectors.toSet() );

		REQUEST_PATH_ACTORS = new HashMap<>();
		Stream.of( Actors.values() )
				.filter( a -> a.service != null )
				.forEach( actor -> Operations.operations( actor.service )
						.map( method -> method.getAnnotation( Operation.class ) )
						.map( op -> Pattern
								.compile( op.method() + ":"
								// replace path vars with .+? matchers
										+ op.path().replaceAll( "/:[^/]+", "/.+?" )
										// allow for optional query params
										+ "(\\?.*?)?" ) )
						.forEach( pattern -> REQUEST_PATH_ACTORS.put( pattern, actor ) ) );
	}

	/**
	 * Stores actual requests, used later for assertion
	 */
	private Map<Actor, List<byte[]>> requests = new HashMap<>();
	/**
	 * Stores mock responses, harvested from the interaction that is being tested
	 */
	private Map<Actor, Deque<byte[]>> responses = new HashMap<>();

	/**
	 * Sets the current set of testdata to use
	 *
	 * @param ntr the interaction being exercised
	 * @return <code>this</code>
	 */
	public MockService exercising( Interaction ntr ) {
		requests.clear();
		responses.clear();
		Flows.descendents( ntr )
				.forEach( i -> responses
						.computeIfAbsent( i.responder(), a -> new ArrayDeque<>() )
						.add( i.response().content() ) );
		return this;
	}

	@Override
	public byte[] apply( byte[] t ) {
		HttpReq request = new HttpReq( t, Text::new );

		Actor actor = REQUEST_PATH_ACTORS.entrySet().stream()
				.filter( e -> e.getKey().matcher( request.method() + ":" + request.path() ).matches() )
				.findFirst()
				.map( Entry::getValue ).orElse( null );
		// save the request
		requests.computeIfAbsent( actor, a -> new ArrayList<>() ).add( t );
		// return the response
		return Optional.ofNullable( responses.get( actor ) )
				.map( Deque::poll )
				.orElse( (""
						+ "HTTP/1.1 404 No such interaction\r\n"
						+ "\r\n").getBytes( UTF_8 ) );
	}

	/**
	 * @return Captured requests
	 */
	public Map<Actor, List<byte[]>> requests() {
		return requests;
	}
}
