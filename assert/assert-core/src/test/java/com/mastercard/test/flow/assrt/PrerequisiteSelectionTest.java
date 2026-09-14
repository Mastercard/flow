package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.TestModel.Actors.A;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static com.mastercard.test.flow.builder.Builder.SELF;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.assrt.mock.Mdl;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.mutable.MutableDependency;
import com.mastercard.test.flow.msg.txt.Text;

/** Selection through the shared caller, with counters on the model API only. */
@SuppressWarnings("static-method")
class PrerequisiteSelectionTest {

	@Test
	void reconvergentAncestorsAreScannedInBoundedPasses() {
		CountedFlow a = new CountedFlow( "a" );
		CountedFlow b = new CountedFlow( "b", a );
		CountedFlow c = new CountedFlow( "c", a );
		CountedFlow d = new CountedFlow( "d", b, c );
		CountedFlow e = new CountedFlow( "e", b, c );
		CountedFlow f = new CountedFlow( "f", d, e, d );
		List<CountedFlow> model = List.of( a, b, c, d, e, f );
		TestFlocessor runner = new TestFlocessor( "reconvergent",
				new Mdl().withFlows( model.toArray( Flow[]::new ) ) )
						.exercising( flow -> flow == f, rejection -> {
						} );

		assertEquals( List.of( a, b, c, d, e, f ), runner.flows().toList() );
		for( CountedFlow flow : model ) {
			assertTrue( flow.visits <= 3, flow + " dependency scans: " + flow.visits );
		}
		assertEquals( 27, model.stream().mapToInt( flow -> flow.edges ).sum(),
				"Nine binding edges, in at most three preparation passes" );
	}

	@Test
	void chainContractionDoesNotRescanModelDependencies() {
		CountedFlow a = new CountedFlow( "a [chain:scenario]" );
		CountedFlow b = new CountedFlow( "b [chain:scenario]", a, a );
		CountedFlow c = new CountedFlow( "c", b );
		TestFlocessor runner = new TestFlocessor( "chain",
				new Mdl().withFlows( c, b, a ) );
		List<Flow> selected = runner.flows().toList();
		assertEquals( List.of( a, b, c ), selected );
		assertTrue( a.visits <= 3 && b.visits <= 3 && c.visits <= 3,
				() -> "Dependency scans: " + List.of( a.visits, b.visits, c.visits ) );
		assertEquals( 9, a.edges + b.edges + c.edges );
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 128, 512 })
	void longPathsAndDisconnectedRootsHaveLinearModelReads( int length ) {
		List<CountedFlow> path = new ArrayList<>();
		for( int i = 0; i < length; i++ ) {
			path.add( new CountedFlow( String.format( "flow-%04d", i ),
					path.isEmpty() ? new Flow[0] : new Flow[] { path.get( i - 1 ) } ) );
		}
		CountedFlow root = new CountedFlow( "unrelated" );
		Mdl model = new Mdl().withFlows( path.toArray( Flow[]::new ) ).withFlows( root );
		TestFlocessor runner = new TestFlocessor( "long path", model );
		List<Flow> expected = new ArrayList<>( path );
		expected.add( root );
		assertEquals( expected, runner.flows().toList() );
		assertEquals( 3 * (length + 1), path.stream().mapToInt( f -> f.visits ).sum() + root.visits );
		assertEquals( 3 * (length - 1), path.stream().mapToInt( f -> f.edges ).sum() );
	}

	@Test
	void withinFlowBindingPublishesWithoutSelfWait() {
		List<String> mutations = new ArrayList<>();
		Flow flow = Creator.build( f -> f.meta( m -> m.description( "self" ) )
				.call( i -> i.from( A ).to( B ).request( new Text( "value" ) )
						.response( new Text( "value" ) ) )
				.dependency( SELF, d -> d.from( i -> true, REQUEST, ".+" )
						.mutate( value -> {
							mutations.add( String.valueOf( value ) );
							return value + "-bound";
						} ).to( i -> true, RESPONSE, ".+" ) ) );
		List<Flow> bodies = new ArrayList<>();
		TestFlocessor runner = new TestFlocessor( "self binding", new Mdl().withFlows( flow ) )
				.system( State.FUL, B ).reporting( Reporting.NEVER )
				.behaviour( a -> {
					bodies.add( a.flow() );
					a.actual().request( "value".getBytes( UTF_8 ) )
							.response( "value-bound".getBytes( UTF_8 ) );
				} );
		runner.execute();
		assertEquals( "self [] SUCCESS", runner.results() );
		assertEquals( List.of( flow ), bodies );
		assertEquals( List.of( "value" ), mutations );
		assertEquals( "value-bound", flow.root().response().assertable() );
	}

	private static class CountedFlow extends Flw {
		private final List<Dependency> bindings = new ArrayList<>();
		private int visits;
		private int edges;

		CountedFlow( String name, Flow... prerequisites ) {
			super( name.contains( "[" ) ? name : name + " []" );
			for( Flow prerequisite : prerequisites ) {
				bindings.add( new MutableDependency().source( s -> s.flow( prerequisite ) ).build( null ) );
			}
		}

		@Override
		public Stream<Dependency> dependencies() {
			visits++;
			return bindings.stream().peek( binding -> edges++ );
		}
	}
}
