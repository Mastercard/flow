package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static com.mastercard.test.flow.assrt.TestModel.Actors.C;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.mock.AltTestContext;
import com.mastercard.test.flow.assrt.mock.Mdl;
import com.mastercard.test.flow.assrt.mock.TestContext;

/**
 * Exercises behaviour related to {@link Context}s and {@link Applicator}s
 */
@SuppressWarnings("static-method")
class ApplicatorTest {

	/**
	 * A record of context switches made by {@link #APPLICATOR}
	 */
	static final List<String> ctxSwitchLog = new ArrayList<>();

	/**
	 * Records context switches to {@link #ctxSwitchLog}
	 */
	static final Applicator<
			TestContext> APPLICATOR = new Applicator<TestContext>( TestContext.class, 0 ) {

				@Override
				public void transition( TestContext from, TestContext to ) {
					ctxSwitchLog.add( String.format( "switch from %s to %s", from, to ) );
				}

				@Override
				public Comparator<TestContext> order() {
					return Comparator.comparing( System::identityHashCode );
				}
			};

	/**
	 * Records context switches to {@link #ctxSwitchLog}
	 */
	static final Applicator<
			AltTestContext> ALT_APPLICATOR = new Applicator<AltTestContext>( AltTestContext.class, 0 ) {

				@Override
				public void transition( AltTestContext from, AltTestContext to ) {
					ctxSwitchLog.add( String.format( "switch from %s to %s", from, to ) );
				}

				@Override
				public Comparator<AltTestContext> order() {
					return Comparator.comparing( System::identityHashCode );
				}
			};

	/**
	 * Clears the context switch log
	 */
	@BeforeEach
	void clearLog() {
		ctxSwitchLog.clear();
	}

	/**
	 * Illustrates context switch behaviour
	 */
	@Test
	void contextSwitch() {

		TestFlocessor tf = new TestFlocessor( "contextSwitch", TestModel.withContext() )
				.system( State.FUL, B )
				.applicators( APPLICATOR, ALT_APPLICATOR )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.withContext(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |",
				"",
				"COMPARE def []",
				"com.mastercard.test.flow.assrt.TestModel.withContext(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ) );
		assertEquals( copypasta(
				"switch from null to AltTestContext[alt ctx]",
				"switch from null to TestContext[first ctx]",
				"switch from AltTestContext[alt ctx] to null",
				"switch from TestContext[first ctx] to TestContext[second ctx]" ),
				copypasta( ctxSwitchLog ) );
	}

	/**
	 * Under concurrent context handling a context-free flow leaves the applied
	 * state alone rather than removing it; serial handling removes it.
	 *
	 * @param concurrent Whether context-free flows may run alongside context flows
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void contextFreeFlowsUnderConcurrentContexts( boolean concurrent ) {
		Flow contextual = TestModel.withContext().flows().findFirst().orElseThrow();
		Flow free = TestModel.abc().flows().findFirst().orElseThrow();
		TestFlocessor tf = new TestFlocessor( "concurrent contexts",
				new Mdl().withFlows( contextual, free ) )
						.system( State.FUL, B )
						.applicators( APPLICATOR, ALT_APPLICATOR )
						.behaviour( assrt -> assrt.actual().response( assrt.expected().response().content() ) );
		if( concurrent ) {
			tf.concurrentContexts();
		}
		assertEquals( 2, tf.flows().count() );
		tf.process( contextual );
		tf.process( free );
		// orphaned context types are removed in hash order, so compare sorted
		assertEquals( copypasta( concurrent
				? List.of( "switch from null to AltTestContext[alt ctx]",
						"switch from null to TestContext[first ctx]" )
				: List.of( "switch from AltTestContext[alt ctx] to null",
						"switch from TestContext[first ctx] to null",
						"switch from null to AltTestContext[alt ctx]",
						"switch from null to TestContext[first ctx]" ) ),
				copypasta( ctxSwitchLog.stream().sorted() ) );
	}

	/**
	 * Shows that flows are failed if there is no applicator for their state
	 */
	@Test
	void noApplicator() {

		TestFlocessor tf = new TestFlocessor( "contextSwitch", TestModel.withContext() )
				.system( State.FUL, B )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		tf.execute();

		assertEquals( copypasta(
				"abc [] error No applicator for context type class com.mastercard.test.flow.assrt.mock.AltTestContext",
				"def [] error No applicator for context type class com.mastercard.test.flow.assrt.mock.TestContext" ),
				copypasta( tf.events() ) );
		assertEquals( copypasta( "" ),
				copypasta( ctxSwitchLog ) );
	}

	/**
	 * Shows that you don't need an applicator if the context domain does not
	 * include the system under test
	 */
	@Test
	void outOfContext() {

		TestFlocessor tf = new TestFlocessor( "contextSwitch", TestModel.withContext() )
				.system( State.FUL, C )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.withContext(TestModel.java:_) B->C [] response",
				" | C response to B | C response to B |",
				"",
				"COMPARE def []",
				"com.mastercard.test.flow.assrt.TestModel.withContext(TestModel.java:_) B->C [] response",
				" | C response to B | C response to B |" ),
				copypasta( tf.events() ) );
		assertEquals( copypasta( "" ),
				copypasta( ctxSwitchLog ) );
	}
}
