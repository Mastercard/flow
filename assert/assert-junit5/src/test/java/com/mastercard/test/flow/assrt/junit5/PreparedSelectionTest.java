package com.mastercard.test.flow.assrt.junit5;

import static com.mastercard.test.flow.assrt.junit5.mock.Actrs.AVA;
import static com.mastercard.test.flow.assrt.junit5.mock.Actrs.BEN;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;

import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Listener;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.builder.concrete.ConcreteFlow;
import com.mastercard.test.flow.builder.concrete.ConcreteMetadata;
import com.mastercard.test.flow.builder.concrete.ConcreteRootInteraction;
import com.mastercard.test.flow.builder.mutable.MutableDependency;
import com.mastercard.test.flow.util.Tags;

/** Real provider-free serial selection, binding publication and preparation. */
@SuppressWarnings("static-method")
class PreparedSelectionTest {

	@ParameterizedTest
	@ValueSource(strings = { "hard", "inside-chain", "contracted" })
	void impossibleOrdersFailBeforeNativeLeavesOrSutWork( String kind ) {
		InvalidFactory.kind = kind;
		InvalidFactory.bodies = 0;
		List<String> leaves = new ArrayList<>();
		List<Throwable> failures = new ArrayList<>();
		FlowExecutionTest.execute( InvalidFactory.class, false, new TestExecutionListener() {
			@Override
			public void executionStarted( TestIdentifier id ) {
				if( id.isTest() ) {
					leaves.add( id.getDisplayName() );
				}
			}

			@Override
			public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
				result.getThrowable().ifPresent( failures::add );
			}
		} );
		assertEquals( List.of(), leaves );
		assertEquals( 0, InvalidFactory.bodies );
		assertTrue( failures.stream().anyMatch( f -> f instanceof IllegalArgumentException
				&& f.getMessage().contains( "Hard prerequisite cycle" ) ), failures::toString );
	}

	@FlowTest
	static class InvalidFactory {
		static String kind;
		static int bodies;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			ConcreteFlow a = unfinished( "A1",
					kind.equals( "hard" ) ? new String[0] : new String[] { "chain:A" } );
			ConcreteFlow b = unfinished( "B1",
					kind.equals( "inside-chain" ) ? new String[] { "chain:A" } : new String[0] );
			ConcreteFlow c = unfinished( "A2",
					kind.equals( "hard" ) ? new String[0] : new String[] { "chain:A" } );
			b.with( new MutableDependency().source( s -> s.flow( a ) ).build( b ) );
			c.with( new MutableDependency().source( s -> s.flow( b ) ).build( c ) );
			if( !kind.equals( "contracted" ) ) {
				a.with( new MutableDependency().source( s -> s.flow( c ) ).build( a ) );
			}
			return execution
					.flocessor( "invalid order", model( c.complete(), b.complete(), a.complete() ) )
					.system( State.FUL, BEN ).reporting( Reporting.NEVER )
					.behaviour( assertion -> {
						bodies++;
						assertion.actual().response( assertion.expected().response().content() );
					} ).tests();
		}
	}

	/** Keep construction open until cyclic references have been connected. */
	private static ConcreteFlow unfinished( String name, String... tags ) {
		Flow template = flow( name, tags );
		return new ConcreteFlow( null, (ConcreteMetadata) template.meta(),
				(ConcreteRootInteraction) template.root(), Set.of(), Map.of(), Map.of() );
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void filtersThenExactClosurePreserveAllFieldBindings( boolean parallel ) {
		SelectionFactory.bodies.clear();
		SelectionFactory.mutations.clear();
		SelectionFactory.exercised.clear();
		SelectionFactory.orderings = 0;
		List<String> leaves = new ArrayList<>();
		List<Throwable> failures = new ArrayList<>();
		FlowExecutionTest.execute( SelectionFactory.class, parallel, new TestExecutionListener() {
			@Override
			public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
				result.getThrowable().ifPresent( failures::add );
				if( id.isTest() ) {
					leaves.add( id.getDisplayName() + ":" + result.getStatus() );
				}
			}
		} );
		assertEquals( List.of(), failures );
		assertEquals( List.of( "A [chain:scenario]:SUCCESSFUL",
				"B [chain:scenario, pick]:SUCCESSFUL" ), leaves );
		assertEquals( List.of( "A", "B" ), SelectionFactory.bodies );
		assertEquals( Set.of( "B", "rejected" ), Set.copyOf( SelectionFactory.exercised ) );
		assertEquals( 2, SelectionFactory.exercised.size() );
		assertEquals( List.of( "left", "right" ), SelectionFactory.mutations );
		assertEquals( 1, SelectionFactory.orderings );
	}

	@FlowTest
	static class SelectionFactory {
		static final List<String> bodies = new ArrayList<>();
		static final List<String> mutations = new ArrayList<>();
		static final List<String> exercised = new ArrayList<>();
		static int orderings;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			Flow a = flow( "A", "chain:scenario" );
			Flow basis = flow( "basis" );
			Flow b = Deriver.build( basis, f -> f
					.meta( m -> m.description( "B" )
							.tags( t -> t.addAll( Set.of( "pick", "chain:scenario" ) ) ) )
					.dependency( a, d -> d.from( i -> true, REQUEST, "left" )
							.mutate( value -> {
								mutations.add( "left" );
								return "new-" + value;
							} ).to( i -> true, REQUEST, "left" ) )
					.dependency( a, d -> d.from( i -> true, REQUEST, "right" )
							.mutate( value -> {
								mutations.add( "right" );
								return "new-" + value;
							} ).to( i -> true, REQUEST, "right" ) ) );
			Flow c = Creator.build( f -> f.meta( m -> m.description( "C" ) ).prerequisite( b ) );
			Flow sameChain = flow( "same-chain-only", "chain:scenario" );
			Flow otherChain = flow( "other-chain", "chain:other" );
			Flow rejectedSource = flow( "rejected-source" );
			Flow rejected = Creator
					.build( f -> f.meta( m -> m.description( "rejected" ).tags( t -> t.add( "pick" ) ) )
							.prerequisite( rejectedSource ) );
			Flow[] all = { c, b, a, basis, sameChain, otherChain, rejected, rejectedSource };
			List<String> identities = Stream.of( all ).map( f -> f.meta().id() ).toList();
			PreparedFlocessor runner = execution.flocessor( "minimal selection", model( all ) )
					.system( State.FUL, BEN ).reporting( Reporting.NEVER )
					.filtering( filter -> filter.includedTags( Set.of( "pick" ) ) )
					.exercising( f -> {
						exercised.add( f.meta().description() );
						return f == b;
					}, rejection -> {
					} )
					.listening( new Listener() {
						@Override
						public void ordering() {
							orderings++;
						}
					} )
					.behaviour( assertion -> {
						bodies.add( assertion.flow().meta().description() );
						if( assertion.flow() == b ) {
							assertEquals( "new-left:new-right", assertion.expected().request().assertable() );
						}
						assertion.actual().request( assertion.expected().request().content() )
								.response( assertion.expected().response().content() );
					} );
			Stream<DynamicNode> tests = runner.tests();
			assertTrue( bodies.isEmpty() );
			assertTrue( mutations.isEmpty() );
			assertEquals( identities, Stream.of( all ).map( f -> f.meta().id() ).toList() );
			assertEquals( 1, orderings );
			return tests;
		}
	}

	private static Flow flow( String name, String... tags ) {
		return Creator
				.build( f -> f.meta( m -> m.description( name ).tags( t -> t.addAll( List.of( tags ) ) ) )
						.call( i -> i.from( AVA ).to( BEN ).request( new Fields( "left:right" ) )
								.response( new Msg( "response" ) ) ) );
	}

	private static Model model( Flow... flows ) {
		return new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return Stream.of( flows ).filter( f -> Tags.filter( f.meta().tags(), include, exclude ) );
			}
		};
	}

	/** Two independently addressable fields, with actual parse/get/set behavior. */
	private static class Fields extends Msg {
		private final String[] values;

		Fields( String content ) {
			super( content );
			values = content.split( ":" );
		}

		@Override
		public Message child() {
			return new Fields( assertable() );
		}

		@Override
		public Message peer( byte[] bytes ) {
			return new Fields( new String( bytes, UTF_8 ) );
		}

		@Override
		public String assertable( Unpredictable... masks ) {
			return String.join( ":", values );
		}

		@Override
		public byte[] content() {
			return assertable().getBytes( UTF_8 );
		}

		@Override
		public Object get( String field ) {
			return values[field.equals( "left" ) ? 0 : 1];
		}

		@Override
		public Message set( String field, Object value ) {
			values[field.equals( "left" ) ? 0 : 1] = String.valueOf( value );
			return this;
		}
	}
}
