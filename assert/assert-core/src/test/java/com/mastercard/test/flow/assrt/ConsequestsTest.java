package com.mastercard.test.flow.assrt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Actor;

/**
 * Exercises {@link Consequests}
 */
@SuppressWarnings("static-method")
class ConsequestsTest {

	private enum Actors implements Actor {
		AVA, BEN,
	}

	/**
	 * Exercises the capture, harvest and clearing of consequests
	 */
	@Test
	void capture() {
		Consequests csq = new Consequests()
				.capture( Actors.AVA, "to ava".getBytes( UTF_8 ) )
				.capture( Actors.AVA, "to ava again".getBytes( UTF_8 ) )
				.capture( Actors.BEN, "to ben".getBytes( UTF_8 ) );

		Function<Actor, List<byte[]>> harvested = csq.harvest();

		assertSame( csq, csq.clear() );

		// harvested data has survived the clear()
		dump( harvested, Actors.AVA, "to ava, to ava again" );
		dump( harvested, Actors.BEN, "to ben" );

		harvested = csq.harvest();

		// the second harvest is empty
		dump( harvested, Actors.AVA, "" );
		dump( harvested, Actors.BEN, "" );
	}

	private static void dump( Function<Actor, List<byte[]>> harvested, Actor actor,
			String expected ) {
		assertEquals( expected, harvested.apply( actor ).stream()
				.map( b -> new String( b, UTF_8 ) )
				.collect( joining( ", " ) ),
				"to " + actor );
	}
}
