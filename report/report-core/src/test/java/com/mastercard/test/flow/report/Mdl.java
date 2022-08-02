package com.mastercard.test.flow.report;

import static com.mastercard.test.flow.builder.Builder.SELF;
import static com.mastercard.test.flow.util.Tags.add;
import static com.mastercard.test.flow.util.Tags.set;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Residue;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.msg.txt.Text;

/**
 * A trivial system model with which to test reports
 */
public class Mdl {

	/**
	 * System actors
	 */
	public enum Actrs implements Actor {
		/***/
		AVA,
		/***/
		BEN,
		/***/
		CHE,
	}

	private static final Text REQ = new Text( "Hello!" );
	private static final Text RES = new Text( "!olleH" );

	/**
	 * A flow that is the basis for another
	 */
	public static final Flow BASIS = Creator
			.build( flow -> flow
					.meta( data -> data
							.description( "basis" )
							.tags( set( "abc", "def" ) ) )
					.call( a -> a
							.from( Actrs.AVA ).to( Actrs.BEN )
							.request( REQ ).response( RES ) ) );

	/**
	 * A flow that is based on another
	 */
	public static final Flow CHILD = Deriver
			.build( BASIS, flow -> flow
					.meta( data -> data
							.description( "child" )
							.tags( add( "ghi" ) ) ) );

	/**
	 * A flow that is a dependency for another
	 */
	public static final Flow DEPENDENCY = Creator
			.build( flow -> flow
					.meta( data -> data
							.description( "dependency" )
							.tags( set( "abc", "ghi", "jkl", "mno" ) ) )
					.call( a -> a
							.from( Actrs.AVA ).to( Actrs.BEN )
							.request( REQ ).response( RES ) ) );

	/**
	 * A flow that is dependent on another
	 */
	public static final Flow DEPENDENT = Creator
			.build( flow -> flow
					.meta( data -> data
							.description( "dependent" )
							.tags( set( "mno", "pqr", "stu" ) ) )
					.prerequisite( DEPENDENCY )
					.prerequisite( DEPENDENCY ) // duplicate dependencies will be collapsed in the report
					.prerequisite( SELF ) // self-deps will also not be in the report
					.context( new Ctx() )
					.residue( new Rsd() )
					.call( a -> a
							.from( Actrs.AVA ).to( Actrs.BEN )
							.request( REQ ).response( RES ) ) );

	/**
	 * A mock {@link Context} implementation to demonstrate serialisation
	 */
	static class Ctx implements Context {

		/**
		 * A data field in the {@link Context}
		 */
		@JsonProperty("field")
		public String field = "context value";

		@Override
		public String name() {
			return "Ctx";
		}

		@Override
		public Set<Actor> domain() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Context child() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * A mock {@link Residue} implementation to demonstrate serialisation
	 */
	static class Rsd implements Residue {

		/**
		 * A data field in the {@link Context}
		 */
		@JsonProperty("field")
		public String field = "residue value";

		@Override
		public String name() {
			return "Rsd";
		}

		@Override
		public Residue child() {
			throw new UnsupportedOperationException();
		}

	}
}
