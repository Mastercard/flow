package com.mastercard.test.flow.builder;

import static com.mastercard.test.flow.builder.Sets.set;
import static com.mastercard.test.flow.builder.mock.Actrs.AVA;
import static com.mastercard.test.flow.builder.mock.Actrs.BEN;
import static com.mastercard.test.flow.builder.mock.Actrs.CHE;
import static com.mastercard.test.flow.builder.mock.Actrs.DAN;
import static com.mastercard.test.flow.builder.mock.Actrs.EFA;
import static com.mastercard.test.flow.builder.mock.Actrs.GIA;
import static com.mastercard.test.flow.util.Tags.set;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.builder.mock.Msg;
import com.mastercard.test.flow.builder.steps.Response;

/**
 * Exercises building {@link Flow} instances from scratch
 */
@SuppressWarnings("static-method")
public class CreatorTest extends BuilderTest {

	/**
	 * @return A flow with populated metadata
	 */
	public static Flow metadataFlow() {
		return Creator.build( flw -> flw
				.meta( data -> data
						.description( "metadata only" )
						.tags( set( "foo", "bar" ) )
						.motivation( "Demonstrating the population of metadata" ) ) );
	}

	/**
	 * Demonstrates the population of metadata
	 */
	@Test
	void metadata() {
		Flow flow = metadataFlow();

		assertEquals( "metadata only", flow.meta().description() );
		assertEquals( "[bar, foo]", flow.meta().tags().toString() );
		assertEquals( "metadata only [bar, foo]", flow.meta().id() );
		assertEquals( "Demonstrating the population of metadata", flow.meta().motivation() );
		assertEquals(
				"com.mastercard.test.flow.builder.CreatorTest.metadataFlow(CreatorTest.java:<line_number>)",
				flow.meta().trace().replaceAll( ":\\d+\\)$", ":<line_number>)" ) );

		assertEquals( null, flow.basis() );
		assertEquals( null, flow.root() );
	}

	/**
	 * @return A simple call/response flow
	 */
	public static Flow basicFlow() {
		return Creator.build( flw -> flw
				.meta( data -> data.description( "basic" ) )
				.call( a -> a.from( AVA ).to( BEN )
						.request( new Msg( "Hi! My name is Ava. I have a voracious appetite for brie." ) )
						.response( new Msg( "Nice to meet you Ava, my name is Ben. I can provide brie." ) ) ) );
	}

	/**
	 * The simplest possible flow - a single request/response pair
	 */
	@Test
	void basic() {
		assertStructure( basicFlow(), "The simplest possible flow",
				"AVA->BEN Msg[Child^1 of 'Hi! My name is Ava. I have a voracious appetite for brie.'] []",
				"BEN->AVA Msg[Child^1 of 'Nice to meet you Ava, my name is Ben. I can provide brie.'] []" );
	}

	/**
	 * @return A flow with a nested interaction structure
	 */
	static Flow nestedFlow() {
		return Creator.build( flw -> flw
				.call( a -> a.from( AVA ).to( BEN )
						.request( new Msg( "Give me brie please" ) )
						.call( b -> b.to( CHE )
								.request( new Msg( "Ava wants some brie" ) )
								.call( c -> c.to( DAN )
										.request( new Msg( "Is it ok if I give some brie to Ben?" ) )
										.response( new Msg( "No, give them cheddar instead" ) ) )
								.response( new Msg( "I haven't got any brie, but here is some cheddar" ) ) )
						.call( d -> d.to( EFA )
								.request( new Msg( "Please swap this cheddar for brie" ) )
								.response( new Msg( "OK, here is some brie" ) ) )
						.response( new Msg( "Here is your brie" ) ) ) );
	}

	/**
	 * Demonstrates a more complex call structure
	 */
	@Test
	void nested() {
		assertStructure( nestedFlow(), "A more comlex structure",
				"AVA->BEN Msg[Child^1 of 'Give me brie please'] []",
				"  BEN->CHE Msg[Child^1 of 'Ava wants some brie'] []",
				"    CHE->DAN Msg[Child^1 of 'Is it ok if I give some brie to Ben?'] []",
				"    DAN->CHE Msg[Child^1 of 'No, give them cheddar instead'] []",
				"  CHE->BEN Msg[Child^1 of 'I haven't got any brie, but here is some cheddar'] []",
				"  BEN->EFA Msg[Child^1 of 'Please swap this cheddar for brie'] []",
				"  EFA->BEN Msg[Child^1 of 'OK, here is some brie'] []",
				"BEN->AVA Msg[Child^1 of 'Here is your brie'] []" );
	}

	/**
	 * @return A {@link Flow} with tags on the interactions
	 */
	static Flow taggedFlow() {
		return Creator.build( flw -> flw
				.call( a -> a.from( AVA ).to( BEN )
						.request( new Msg( "Give me brie" ) )
						.call( b -> b.to( CHE ).tags( set( "query" ) )
								.request( new Msg( "Got any spare brie lying around?" ) )
								.response( new Msg( "I mean, _yeah_, but Dan says not to give it to you" ) ) )
						.call( b -> b.to( CHE ).tags( set( "guilt_trip" ) )
								.request( new Msg( "Remember when I helped you move that couch?" ) )
								.response( new Msg( "Fine! Take some brie, but we're square now" ) ) )
						.response( new Msg( "Here is your brie" ) ) ) );
	}

	/**
	 * Interactions between the same actors can be disambiguated with tags
	 */
	@Test
	void tagging() {
		assertStructure( taggedFlow(), "interaction disambiguation",
				"AVA->BEN Msg[Child^1 of 'Give me brie'] []",
				"  BEN->CHE Msg[Child^1 of 'Got any spare brie lying around?'] [query]",
				"  CHE->BEN Msg[Child^1 of 'I mean, _yeah_, but Dan says not to give it to you'] [query]",
				"  BEN->CHE Msg[Child^1 of 'Remember when I helped you move that couch?'] [guilt_trip]",
				"  CHE->BEN Msg[Child^1 of 'Fine! Take some brie, but we're square now'] [guilt_trip]",
				"BEN->AVA Msg[Child^1 of 'Here is your brie'] []" );
	}

	/**
	 * Setting the implicitly-involved actors
	 */
	@Test
	void implicit() {
		Flow flow = Creator.build( flw -> flw
				.implicit( set( BEN, AVA ) )
				.meta( data -> data.description( "with implied actors" ) ) );
		assertEquals( "[AVA, BEN]", flow.implicit().collect( Collectors.toList() ).toString() );
		assertEquals( "with implied actors", flow.meta().description() );
	}

	/**
	 * Empty dependencies can be used to add ordering constraints
	 */
	@Test
	void order() {
		Flow intro = Creator.build( flw -> flw
				.call( a -> a.from( AVA ).to( BEN )
						.request( new Msg( "My friend Gia is also a brie afficionado" ) )
						.response( new Msg( "Great, I'll call her!" ) ) ) );

		Flow supply = Creator.build( flw -> flw
				.prerequisite( intro )
				.call( a -> a.from( BEN ).to( GIA )
						.request( new Msg( "Ava tells me you like brie?" ) )
						.response( new Msg( "I do!" ) ) ) );

		// The system model contains the link between the two flows. Thus assertion
		// components can schedule them appropriately
		assertDependency( "order", supply.dependencies().iterator().next(),
				intro, null, null,
				supply, null, null );
	}

	/**
	 * @return The source of a data {@link Dependency}
	 */
	static Flow redirectFlow() {
		return Creator.build( flw -> flw
				.call( a -> a.from( AVA ).to( BEN )
						.request( new Msg( "brie now" ) )
						.response( new Msg( "Efa can hook you up."
								+ " Tell her 'bloop' so she knows I sent you" ) ) ) );
	}

	/**
	 * @param redirect The source of the data
	 * @param extra    Extra build steps
	 * @return The sink of a data {@link Dependency}
	 */
	@SafeVarargs
	static Flow queryFlow( Flow redirect, Consumer<Creator>... extra ) {
		// these can be defined inline, but readability is lower
		Predicate<Interaction> AVA_BEN = i -> i.requester() == AVA && i.responder() == BEN;
		Predicate<Interaction> AVA_EFA = i -> i.requester() == AVA && i.responder() == EFA;

		return Creator.build( flw -> flw
				.call( a -> a.from( AVA ).to( EFA )
						.request( new Msg( "I need brie, Ben says to say 'bloop'" ) )
						.response( new Msg( "OK, here is some brie" ) ) )
				.dependency( redirect, d -> d
						// field locations are specific to the message type
						.from( AVA_BEN, RESPONSE, "<response bloop location>" )
						.mutate( o -> String.valueOf( o ).toUpperCase() )
						.to( AVA_EFA, REQUEST, "<request bloop location>" ) )
				.meta( data -> data.description( "query" ) ),
				Stream.of( extra ).reduce( f -> { // identity
				}, Consumer::andThen ) );
	}

	/**
	 * Dependencies can also transfer data from one flow to another. This is useful
	 * when unpredictable values from one flow are required in another
	 */
	@Test
	void data() {
		Flow redirect = redirectFlow();
		Flow query = queryFlow( redirect );

		// The system model now contains enough information to copy bloop from one flow
		// to the next. Thus at runtime Ben can use any word instead of bloop, and the
		// assertion components can ensure it gets populated correctly in the request
		// to Efa
		assertMutatingDependency( "data", query.dependencies().iterator().next(),
				redirect,
				"Msg[Child^1 of 'Efa can hook you up. Tell her 'bloop' so she knows I sent you']",
				"<response bloop location>",
				query,
				"Msg[Child^1 of 'I need brie, Ben says to say 'bloop'' {<request bloop location>=retrived value of '<response bloop location>'}]",
				"<request bloop location>",
				"value", "VALUE" );
	}

	/**
	 * The API makes it pretty difficult to go off-piste when defining interactions,
	 * but here's what happens if you manage it
	 */
	@Test
	void badCall() {

		// sneakily save the references to the builders that we're given
		AtomicReference<Creator> cref = new AtomicReference<>();
		AtomicReference<Response<Creator>> rref = new AtomicReference<>();
		Creator.build( flw -> {
			cref.set( flw );
			flw.call(
					a -> a.from( AVA ).to( BEN )
							.request( new Msg( "abc" ) )
							.call( b -> {
								rref.set( b.to( CHE )
										.request( new Msg( "def" ) )
										.response( new Msg( "fed" ) ) );
								return rref.get();
							} )
							.response( new Msg( "cba" ) ) );
		} );
		{
			Exception e = Assertions.assertThrows(
					IllegalStateException.class, () -> Creator
							.build( flw -> flw
									// return it as the result in a different creator
									.call( a -> cref.get() ) ) );
			assertEquals( "Failed to return to origin", e.getMessage() );
		}
		{
			Exception e = Assertions.assertThrows(
					IllegalStateException.class, () -> Creator
							.build( flw -> flw
									.call( a -> a.from( AVA ).to( BEN )
											.request( new Msg( "abc" ) )
											// works the same with child calls
											.call( b -> rref.get() )
											.response( new Msg( "cba" ) ) ) ) );
			assertEquals( "Failed to return to origin", e.getMessage() );
		}
		{
			Exception e = Assertions.assertThrows(
					IllegalStateException.class, () -> Creator
							.build( flw -> flw
									// also on dependencies
									.dependency( metadataFlow(), p -> cref.get() ) ) );
			assertEquals( "Failed to return to origin", e.getMessage() );
		}
	}
}
