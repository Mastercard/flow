package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;

/**
 * Demonstrates the execution {@link Listener} API
 */
@SuppressWarnings("static-method")
class ListenerTest {

	/**
	 * Exercising the listener
	 */
	@Test
	void listener() {
		List<String> events = new ArrayList<>();
		TestFlocessor tf = new TestFlocessor( "", TestModel.withBoth() )
				.system( State.LESS, B )
				.applicators( ApplicatorTest.APPLICATOR )
				.checkers( new CheckerTest.TestChecker() )
				.masking( CheckerTest.Nprdct.DIGITS )
				.listening( new Listener() {
					@Override
					public void filtering() {
						events.add( "filtering" );
					}

					@Override
					public void ordering() {
						events.add( "ordering" );
					}

					@Override
					public void flow( Flow flow ) {
						events.add( "flow : " + flow.meta().id() );
					}

					@Override
					public void context( Context context ) {
						events.add( "context : " + context.name() );
					}

					@Override
					public void interaction( Interaction interaction ) {
						events.add( "interaction : " + interaction.requester().name() + " -> "
								+ interaction.responder().name() );
					}

					@Override
					public void before( Residue residue ) {
						events.add( "before : " + residue.name() );
					}

					@Override
					public void after( Residue residue ) {
						events.add( "after : " + residue.name() );
					}

					@Override
					public void flowComplete( Flow flow ) {
						events.add( "flowComplete : " + flow.meta().id() );
					}
				} )
				.behaviour( asrt -> asrt.actual().request( asrt.expected().request().content() ) );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.withBoth(TestModel.java:_) A->B [] request",
				" | A request to B | A request to B |",
				"",
				"COMPARE Residue 'TestResidue'",
				" | ?st residue | ?st residue |" ),
				copypasta( tf.events() ) );

		assertEquals( copypasta(
				"filtering",
				"ordering",
				"flow : abc []",
				"context : TestContext",
				"before : TestResidue",
				"interaction : A -> B",
				"after : TestResidue",
				"flowComplete : abc []" ),
				copypasta( events ) );
	}
}
