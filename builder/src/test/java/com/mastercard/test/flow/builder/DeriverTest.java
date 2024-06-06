package com.mastercard.test.flow.builder;

import static com.mastercard.test.flow.builder.Sets.set;
import static com.mastercard.test.flow.builder.mock.Actrs.AVA;
import static com.mastercard.test.flow.builder.mock.Actrs.BEN;
import static com.mastercard.test.flow.builder.mock.Actrs.CHE;
import static com.mastercard.test.flow.builder.mock.Actrs.DAN;
import static com.mastercard.test.flow.builder.mock.Actrs.EFA;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.builder.AbstractContextTest.Cntxt;
import com.mastercard.test.flow.builder.mock.Actrs;
import com.mastercard.test.flow.builder.mock.Msg;
import com.mastercard.test.flow.builder.mock.Rsd;
import com.mastercard.test.flow.builder.mutable.MutableInteraction;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercises creating {@link Flow}s based on existing {@link Flow}s
 */
@SuppressWarnings("static-method")
class DeriverTest extends BuilderTest {

	/**
	 * Demonstrates deriving metadata
	 */
	@Test
	void metadata() {
		Flow flow = Deriver.build( CreatorTest.metadataFlow(), flw -> flw
				.meta( data -> data
						.description( "updated description" )
						.tags( Tags.add( "derived" ) ) ) );

		assertEquals( "updated description", flow.meta().description(),
				"overwritten" );
		assertEquals( "[bar, derived, foo]", flow.meta().tags().toString(),
				"updated" );
		assertEquals( "updated description [bar, derived, foo]", flow.meta().id() );
		assertEquals( "Demonstrating the population of metadata", flow.meta().motivation(),
				"inherited" );
		assertEquals(
				"com.mastercard.test.flow.builder.DeriverTest.metadata(DeriverTest.java:<line_number>)",
				flow.meta().trace().replaceAll( ":\\d+\\)$", ":<line_number>)" ),
				"overwritten" );

		Flow traced = Deriver.build( flow, flw -> flw.meta( data -> data
				.trace( "src/main/resource/data.csv:18" )
				.motivation( "Flow data might be defined in a resource file" )
				.tags( Tags.add( "data.csv" ) ) ) );

		assertEquals( "src/main/resource/data.csv:18",
				traced.meta().trace() );
		assertEquals( "[bar, data.csv, derived, foo]", traced.meta().tags().toString(),
				"updated" );
	}

	/**
	 * Demonstrates a copy with no edits
	 */
	@Test
	void copy() {
		Flow basis = CreatorTest.basicFlow();
		assertStructure( basis, "Original",
				"AVA->BEN Msg[Child^1 of 'Hi! My name is Ava. I have a voracious appetite for brie.'] []",
				"BEN->AVA Msg[Child^1 of 'Nice to meet you Ava, my name is Ben. I can provide brie.'] []" );

		Flow copy = Deriver.build( basis );
		assertStructure( copy, "Derived",
				"AVA->BEN Msg[Child^2 of 'Hi! My name is Ava. I have a voracious appetite for brie.'] []",
				"BEN->AVA Msg[Child^2 of 'Nice to meet you Ava, my name is Ben. I can provide brie.'] []" );
		// notice that the messages in the derived flow are children of the basis
		// messages
	}

	/**
	 * Demonstrates the addition of interactions to an inherited structure
	 */
	@Test
	void add() {
		Flow flow = Deriver.build( CreatorTest.basicFlow(), flw -> flw
				.addCall( i -> i.responder() == BEN,
						// default to adding at the end of the child list
						a -> a.to( EFA )
								.request( new Msg( "You've got some of that brie stuff right?" ) )
								.response( new Msg( "Loads of it, but I want cheddar in return" ) ) )
				.addCall( i -> i.responder() == BEN,
						0, // indexed to insert at the start of the child list
						a -> a.to( Actrs.CHE )
								.request( new Msg( "You've got some of that brie stuff right?" ) )
								.call( b -> b.to( DAN )
										.request( new Msg( "Ben is asking about our brie" ) )
										.response( new Msg( "Fob them off" ) ) )
								.response( new Msg( "Sometimes I guess, but it's mostly cheddar here" ) ) ) );

		assertStructure( flow, "Interaction addition",
				"AVA->BEN Msg[Child^2 of 'Hi! My name is Ava. I have a voracious appetite for brie.'] []",
				"  BEN->CHE Msg[Child^1 of 'You've got some of that brie stuff right?'] []",
				"    CHE->DAN Msg[Child^1 of 'Ben is asking about our brie'] []",
				"    DAN->CHE Msg[Child^1 of 'Fob them off'] []",
				"  CHE->BEN Msg[Child^1 of 'Sometimes I guess, but it's mostly cheddar here'] []",
				"  BEN->EFA Msg[Child^1 of 'You've got some of that brie stuff right?'] []",
				"  EFA->BEN Msg[Child^1 of 'Loads of it, but I want cheddar in return'] []",
				"BEN->AVA Msg[Child^2 of 'Nice to meet you Ava, my name is Ben. I can provide brie.'] []" );
	}

	/**
	 * Demonstrates removing interactions from the inherited structure
	 */
	@Test
	void remove() {
		Flow flow = Deriver.build( CreatorTest.nestedFlow(), flw -> flw
				.removeCall( i -> i.responder() == DAN )
				.meta( data -> data.description( "removed" ) ) );

		assertStructure( flow, "Interaction removal",
				"AVA->BEN Msg[Child^2 of 'Give me brie please'] []",
				"  BEN->CHE Msg[Child^2 of 'Ava wants some brie'] []",
				"  CHE->BEN Msg[Child^2 of 'I haven't got any brie, but here is some cheddar'] []",
				"  BEN->EFA Msg[Child^2 of 'Please swap this cheddar for brie'] []",
				"  EFA->BEN Msg[Child^2 of 'OK, here is some brie'] []",
				"BEN->AVA Msg[Child^2 of 'Here is your brie'] []" );
	}

	/**
	 * Showing that the root actor can be changed
	 */
	@Test
	void reroot() {
		Flow flow = Deriver.build( CreatorTest.basicFlow(), flw -> flw
				.update( i -> true, i -> i
						.requester( EFA )
						.tags( Tags.add( "identity_theft" ) ) ) );

		assertStructure( flow, "Root actor changed",
				"EFA->BEN Msg[Child^2 of 'Hi! My name is Ava. I have a voracious appetite for brie.'] [identity_theft]",
				"BEN->EFA Msg[Child^2 of 'Nice to meet you Ava, my name is Ben. I can provide brie.'] [identity_theft]" );
	}

	/**
	 * Showing that the data flow can be altered
	 */
	@Test
	void reroute() {

		Flow flow = Deriver.build( CreatorTest.nestedFlow(), flw -> flw
				.update( i -> i.requester() == CHE, i -> i
						.responder( EFA )
						.response( new Msg( "I can swap that for him" ) )
						.tags( Tags.add( "back_channel" ) )
						.request( new Msg( "Ben wants brie, but all I have is cheddar" ) ) )
				.update( i -> i.responder() == CHE, i -> i
						.requester( DAN )
						.tags( Tags.add( "priority" ) ) ) );

		assertStructure( flow, "updated data flow",
				"AVA->DAN Msg[Child^2 of 'Give me brie please'] []",
				"  DAN->CHE Msg[Child^2 of 'Ava wants some brie'] [priority]",
				"    CHE->EFA Msg[Child^1 of 'Ben wants brie, but all I have is cheddar'] [back_channel]",
				"    EFA->CHE Msg[Child^1 of 'I can swap that for him'] [back_channel]",
				"  CHE->DAN Msg[Child^2 of 'I haven't got any brie, but here is some cheddar'] [priority]",
				"  DAN->EFA Msg[Child^2 of 'Please swap this cheddar for brie'] []",
				"  EFA->DAN Msg[Child^2 of 'OK, here is some brie'] []",
				"DAN->AVA Msg[Child^2 of 'Here is your brie'] []" );
	}

	/**
	 * Demonstrates the general update mechanism, used here to update
	 * {@link Interaction} tags
	 */
	@Test
	void update() {
		Flow flow = Deriver.build( CreatorTest.taggedFlow(), flw -> flw
				.update( i -> i.responder() == BEN, i -> i.tags( Tags.add( "root" ) ) )
				.update( i -> i.responder() == CHE, i -> i.tags( Tags.remove( "query" ) ) )
				.update( i -> true, i -> i.tags( Tags.add( "tag" ) ) ) );

		assertStructure( flow, "updated interaction tags",
				"AVA->BEN Msg[Child^2 of 'Give me brie'] [root, tag]",
				"  BEN->CHE Msg[Child^2 of 'Got any spare brie lying around?'] [tag]",
				"  CHE->BEN Msg[Child^2 of 'I mean, _yeah_, but Dan says not to give it to you'] [tag]",
				"  BEN->CHE Msg[Child^2 of 'Remember when I helped you move that couch?'] [guilt_trip, tag]",
				"  CHE->BEN Msg[Child^2 of 'Fine! Take some brie, but we're square now'] [guilt_trip, tag]",
				"BEN->AVA Msg[Child^2 of 'Here is your brie'] [root, tag]" );
	}

	/**
	 * Demonstrates that dependencies can be inherited
	 */
	@Test
	void dependency() {
		Flow before = CreatorTest.metadataFlow();
		Flow originalSource = CreatorTest.redirectFlow();
		Flow originalSink = CreatorTest.queryFlow( originalSource, f -> f.prerequisite( before ) );

		assertEquals( 2, originalSink.dependencies().count() );
		assertMutatingDependency( "data", originalSink.dependencies().iterator().next(),
				originalSource,
				"Msg[Child^1 of 'Efa can hook you up. Tell her 'bloop' so she knows I sent you']",
				"<response bloop location>",
				originalSink,
				"Msg[Child^1 of 'I need brie, Ben says to say 'bloop'' {<request bloop location>=retrived value of '<response bloop location>'}]",
				"<request bloop location>",
				"value", "VALUE" );

		Flow derivedSource = Deriver.build( originalSource );

		// Using the two-arg inheritDependencies allows you to target a specific
		// dependency relationships
		{
			Flow derivedSink = Deriver.build( originalSink,
					flw -> flw.inheritDependencies( originalSource, derivedSource )
							.meta( data -> data.description( "inherit" ) ) );

			assertEquals( 1, derivedSink.dependencies().count(),
					"only one of the two available deps copied" );
			Iterator<Dependency> deps = derivedSink.dependencies().iterator();

			assertMutatingDependency( "data", deps.next(),
					derivedSource,
					"Msg[Child^2 of 'Efa can hook you up. Tell her 'bloop' so she knows I sent you']",
					"<response bloop location>",
					derivedSink,
					"Msg[Child^2 of 'I need brie, Ben says to say 'bloop'' {<request bloop location>=retrived value of '<response bloop location>'}]",
					"<request bloop location>",
					"value", "VALUE" );

			assertFalse( deps.hasNext() );
		}

		// using the 1-arg inheritDependencies will bring over every dependency
		{
			Flow derivedSink = Deriver.build( originalSink,
					flw -> flw.inheritDependencies( derivedSource )
							.meta( data -> data.description( "inherit" ) ) );

			assertEquals( 2, derivedSink.dependencies().count(),
					"Both dependencies copied" );
			Iterator<Dependency> deps = derivedSink.dependencies().iterator();

			assertMutatingDependency( "data", deps.next(),
					derivedSource,
					"Msg[Child^2 of 'Efa can hook you up. Tell her 'bloop' so she knows I sent you']",
					"<response bloop location>",
					derivedSink,
					"Msg[Child^2 of 'I need brie, Ben says to say 'bloop'' {<request bloop location>=retrived value of '<response bloop location>'}]",
					"<request bloop location>",
					"value", "VALUE" );

			assertDependency( "order", deps.next(),
					derivedSource, null, null,
					derivedSink, null, null );

			assertFalse( deps.hasNext() );
		}
	}

	/**
	 * Setting the implicitly-involved actors
	 */
	@Test
	void implicit() {
		Flow flow = Deriver.build( CreatorTest.basicFlow(),
				flw -> flw.implicit( set( BEN, AVA ) ) );
		assertEquals( "[AVA, BEN]",
				flow.implicit().collect( Collectors.toList() ).toString() );
	}

	/**
	 * Exercises manipulating {@link Context}s
	 */
	@Test
	void context() {
		Flow flow = CreatorTest.basicFlow();
		Context a = new AbstractContextTest.Cntxt( "a" );
		Context b = new AbstractContextTest.Cntxt( "b" );

		Flow with = Deriver.build( flow, f -> f
				.context( a )
				.meta( data -> data.description( "with" ) ) );
		assertEquals( "with", with.meta().description() );
		assertEquals( "[Cntxt[a]]", with.context().collect( toList() ).toString(),
				"Context has been added" );

		Flow without = Deriver.build( with, f -> f
				.context( Cntxt.class, null )
				.meta( data -> data.description( "without" ) ) );
		assertEquals( "without", without.meta().description() );
		assertEquals( "[]", without.context().collect( toList() ).toString(),
				"Inherited context has been removed" );

		// only one instance of a context class may be present
		Flow replaced = Deriver.build( with, f -> f
				.context( b )
				.meta( data -> data.description( "replaced" ) ) );
		assertEquals( "replaced", replaced.meta().description() );
		assertEquals( "[Cntxt[b]]", replaced.context().collect( toList() ).toString(),
				"Inherited context has been replaced" );

		Flow updated = Deriver.build( with, f -> f
				.context( Cntxt.class, c -> c.value( "c" ) )
				.meta( data -> data.description( "updated" ) ) );
		assertEquals( "updated", updated.meta().description() );
		assertEquals( "[Cntxt[c]]", updated.context().collect( toList() ).toString(),
				"Inherited context has been updated" );

		// update was made to a child - the original contexts have not been changed
		assertEquals( "Cntxt[a]", a.toString() );
		assertEquals( "Cntxt[b]", b.toString() );
	}

	/**
	 * Exercises manipulating {@link Residue}s
	 */
	@Test
	void residue() {
		Flow flow = CreatorTest.basicFlow();
		Residue a = new Rsd().value( "a" );
		Residue b = new Rsd().value( "b" );

		Flow with = Deriver.build( flow, f -> f
				.residue( a )
				.meta( data -> data.description( "with" ) ) );
		assertEquals( "with", with.meta().description() );
		assertEquals( "[Rsd[a]]", with.residue().collect( toList() ).toString(),
				"Residue has been added" );

		Flow without = Deriver.build( with, f -> f
				.residue( Rsd.class, null )
				.meta( data -> data.description( "without" ) ) );
		assertEquals( "without", without.meta().description() );
		assertEquals( "[]", without.residue().collect( toList() ).toString(),
				"Inherited residue has been removed" );

		// only one instance of a residue class may be present
		Flow replaced = Deriver.build( with, f -> f
				.residue( b )
				.meta( data -> data.description( "replaced" ) ) );
		assertEquals( "replaced", replaced.meta().description() );
		assertEquals( "[Rsd[b]]", replaced.residue().collect( toList() ).toString(),
				"Inherited residue has been replaced" );

		Flow updated = Deriver.build( with, f -> f
				.residue( Rsd.class, c -> c.value( "c" ) )
				.meta( data -> data.description( "updated" ) ) );
		assertEquals( "updated", updated.meta().description() );
		assertEquals( "[Rsd[c]]", updated.residue().collect( toList() ).toString(),
				"Inherited residue has been updated" );

		// update was made to a child - the original contexts have not been changed
		assertEquals( "Rsd[a]", a.toString() );
		assertEquals( "Rsd[b]", b.toString() );
	}

	/**
	 * Shows that a subset of an existing flow can be extracted
	 */
	@Test
	void subset() {
		Flow full = CreatorTest.nestedFlow();
		assertStructure( full, "Showing full structure",
				"AVA->BEN Msg[Child^1 of 'Give me brie please'] []",
				"  BEN->CHE Msg[Child^1 of 'Ava wants some brie'] []",
				"    CHE->DAN Msg[Child^1 of 'Is it ok if I give some brie to Ben?'] []",
				"    DAN->CHE Msg[Child^1 of 'No, give them cheddar instead'] []",
				"  CHE->BEN Msg[Child^1 of 'I haven't got any brie, but here is some cheddar'] []",
				"  BEN->EFA Msg[Child^1 of 'Please swap this cheddar for brie'] []",
				"  EFA->BEN Msg[Child^1 of 'OK, here is some brie'] []",
				"BEN->AVA Msg[Child^1 of 'Here is your brie'] []" );

		Flow subset = Deriver.build( full, flw -> flw
				.subset( i -> i.responder() == Actrs.CHE )
				.meta( data -> data
						.description( "subset" ) ) );
		assertStructure( subset, "We've extracted a subset",
				"BEN->CHE Msg[Child^3 of 'Ava wants some brie'] []",
				"  CHE->DAN Msg[Child^3 of 'Is it ok if I give some brie to Ben?'] []",
				"  DAN->CHE Msg[Child^3 of 'No, give them cheddar instead'] []",
				"CHE->BEN Msg[Child^3 of 'I haven't got any brie, but here is some cheddar'] []" );
		// note that we've got an unnecessary level of message inheritance: once when
		// the deriver is built, once when we build the new root interaction. It looks
		// messy here but the actual impact is negligible

		Flow noop = Deriver.build( subset, flw -> flw.subset( i -> i.responder() == Actrs.CHE ) );
		assertStructure( noop, "Pointless subsetting avoided",
				"BEN->CHE Msg[Child^4 of 'Ava wants some brie'] []",
				"  CHE->DAN Msg[Child^4 of 'Is it ok if I give some brie to Ben?'] []",
				"  DAN->CHE Msg[Child^4 of 'No, give them cheddar instead'] []",
				"CHE->BEN Msg[Child^4 of 'I haven't got any brie, but here is some cheddar'] []" );
		// note only 1 new level of childing has been added
	}

	/**
	 * Shows that a new root cause can be grafted to an existing flow
	 */
	@Test
	void superset() {
		Flow initial = CreatorTest.basicFlow();
		assertStructure( initial, "Showing initial structure",
				"AVA->BEN Msg[Child^1 of 'Hi! My name is Ava. I have a voracious appetite for brie.'] []",
				"BEN->AVA Msg[Child^1 of 'Nice to meet you Ava, my name is Ben. I can provide brie.'] []" );

		Flow superset = Deriver.build( initial, flw -> flw
				.superset( EFA,
						new Msg( "Please find someone else to talk to about cheese" ),
						new Msg( "His name is Ben and he seems really nice" ) )
				.meta( data -> data
						.description( "superset" ) ) );

		assertStructure( superset, "We've embedded the initial structure in a new context",
				"EFA->AVA Msg[Child^1 of 'Please find someone else to talk to about cheese'] []",
				"  AVA->BEN Msg[Child^2 of 'Hi! My name is Ava. I have a voracious appetite for brie.'] []",
				"  BEN->AVA Msg[Child^2 of 'Nice to meet you Ava, my name is Ben. I can provide brie.'] []",
				"AVA->EFA Msg[Child^1 of 'His name is Ben and he seems really nice'] []" );
	}

	/**
	 * The API makes it pretty difficult to go off-piste when updating interactions,
	 * but here's what happens if you manage it
	 */
	@Test
	void badCall() {

		// sneakily save the references to the builder that we're given
		AtomicReference<MutableInteraction> miref = new AtomicReference<>();
		Deriver.build( CreatorTest.basicFlow(), flw -> flw
				.addCall( i -> true, a -> {
					miref.set( a.to( BEN ).request( null ).response( null ) );
					return miref.get();
				} ) );

		{
			Flow f = CreatorTest.basicFlow();
			Exception e = Assertions.assertThrows(
					IllegalStateException.class, () -> Deriver.build( f, flw -> flw
							.addCall( i -> true, a -> miref.get() ) ) );
			assertEquals( "Failed to return to origin", e.getMessage() );
		}
	}
}
