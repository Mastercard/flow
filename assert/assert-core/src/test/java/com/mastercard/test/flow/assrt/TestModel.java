package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.builder.Sets.set;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.mock.Flw;
import com.mastercard.test.flow.assrt.mock.Mdl;
import com.mastercard.test.flow.assrt.mock.TestContext;
import com.mastercard.test.flow.assrt.mock.TestResidue;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.msg.txt.Text;

/**
 * A collection of fully-fledged {@link Flow} instances, useful for more
 * involved testing of assert components than {@link Flw}
 */
public class TestModel {

	/***/
	public enum Actors implements Actor {
		/***/
		A,
		/***/
		B,
		/***/
		C,
		/***/
		D,
		/***/
		E;
	}

	/**
	 * @return A model with a single flow, calling from A to B to C
	 */
	public static Model abc() {
		Flow abc = Creator.build( flow -> flow
				.meta( data -> data
						.description( "abc" ) )
				.call( a -> a
						.from( Actors.A )
						.to( Actors.B )
						.request( new Text( "A request to B" ) )
						.call( b -> b
								.to( Actors.C )
								.request( new Text( "B request to C" ) )
								.response( new Text( "C response to B" ) ) )
						.response( new Text( "B response to A" ) ) ) );

		return new Mdl().withFlows( abc );
	}

	/**
	 * @return A model with a single flow, calling from A to B to C
	 */
	public static Model abcWithChild() {
		Flow abc = Creator.build( flow -> flow
				.meta( data -> data
						.description( "abc" ) )
				.call( a -> a
						.from( Actors.A )
						.to( Actors.B )
						.request( new Text( "A request to B" ) )
						.call( b -> b
								.to( Actors.C )
								.request( new Text( "B request to C" ) )
								.response( new Text( "C response to B" ) ) )
						.response( new Text( "B response to A" ) ) ) );

		Flow child = Deriver.build( abc, flow -> flow
				.meta( data -> data
						.description( "child" ) ) );

		return new Mdl().withFlows( abc, child );
	}

	/**
	 * @return A model with a single flow, calling from A to B to C and an implicit
	 *         dependency on D
	 */
	public static Model abcWithImplicit() {
		Flow abc = Creator.build( flow -> flow
				.meta( data -> data
						.description( "abc" ) )
				.implicit( set( Actors.D ) )
				.call( a -> a
						.from( Actors.A )
						.to( Actors.B )
						.request( new Text( "A request to B" ) )
						.call( b -> b
								.to( Actors.C )
								.request( new Text( "B request to C" ) )
								.response( new Text( "C response to B" ) ) )
						.response( new Text( "B response to A" ) ) ) );

		return new Mdl().withFlows( abc );
	}

	/**
	 * @return A model with two flows with a dependency between them, calling from A
	 *         to B to C
	 */
	public static Model abcWithDependency() {
		Flow dependency = Creator.build( flow -> flow
				.meta( data -> data
						.description( "dependency" ) )
				.call( a -> a
						.from( Actors.A )
						.to( Actors.B )
						.request( new Text( "A request to B" ) )
						.call( b -> b
								.to( Actors.C )
								.request( new Text( "B request to C" ) )
								.response( new Text( "C response to B" ) ) )
						.response( new Text( "B response to A" ) ) ) );

		Flow dependent = Deriver.build( dependency, flow -> flow
				.meta( data -> data
						.description( "dependent" ) )
				.prerequisite( dependency ) );

		return new Mdl().withFlows( dependency, dependent );
	}

	/**
	 * @return A model with two flows, calling from A to B to C with associated
	 *         context
	 */
	public static Model withContext() {
		Flow abc = Creator.build( flow -> flow
				.meta( data -> data
						.description( "abc" ) )
				.context( new TestContext().value( "first ctx" ) )
				.call( a -> a
						.from( Actors.A )
						.to( Actors.B )
						.request( new Text( "A request to B" ) )
						.call( b -> b
								.to( Actors.C )
								.request( new Text( "B request to C" ) )
								.response( new Text( "C response to B" ) ) )
						.response( new Text( "B response to A" ) ) ) );

		Flow def = Deriver.build( abc, flow -> flow
				.meta( data -> data
						.description( "def" ) )
				.context( TestContext.class, c -> c.value( "second ctx" ) ) );

		return new Mdl().withFlows( abc, def );
	}

	/**
	 * @return A model with two flows, calling from A to B to C with associated
	 *         residue
	 */
	public static Model withResidue() {
		Flow abc = Creator.build( flow -> flow
				.meta( data -> data
						.description( "abc" ) )
				.residue( new TestResidue().value( "1st residue" ) )
				.call( a -> a
						.from( Actors.A )
						.to( Actors.B )
						.request( new Text( "A request to B" ) )
						.call( b -> b
								.to( Actors.C )
								.request( new Text( "B request to C" ) )
								.response( new Text( "C response to B" ) ) )
						.response( new Text( "B response to A" ) ) ) );

		Flow def = Deriver.build( abc, flow -> flow
				.meta( data -> data
						.description( "def" ) )
				.residue( TestResidue.class, r -> r.value( "2nd residue" ) ) );

		return new Mdl().withFlows( abc, def );
	}

}
