package com.mastercard.test.flow.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.builder.mock.Actrs;

/**
 * Exercises {@link AbstractContext}
 */
@SuppressWarnings("static-method")
class AbstractContextTest {

	/**
	 * A trivial concrete {@link AbstractContext} subclass
	 */
	public static class Cntxt extends AbstractContext {

		private String value;

		/**
		 * @param value A mutable context value
		 */
		public Cntxt( String value ) {
			super( "Cntxt", Actrs.EFA, Actrs.AVA );
			this.value = value;
		}

		private Cntxt( Cntxt parent ) {
			super( parent );
			value = parent.value;
		}

		@Override
		public Cntxt child() {
			return new Cntxt( this );
		}

		/**
		 * @param v The new value
		 * @return <code>this</code>
		 */
		public Cntxt value( String v ) {
			value = v;
			return this;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + value + "]";
		}
	}

	/**
	 * Name access
	 */
	@Test
	void name() {
		assertEquals( "Cntxt", new Cntxt( "ctx" ).name() );
	}

	/**
	 * Domain access
	 */
	@Test
	void domain() {
		assertEquals( "[AVA, EFA]", new Cntxt( "ctx" ).domain().toString() );
	}

	/**
	 * Copy constructor
	 */
	@Test
	void child() {
		Cntxt ctx = new Cntxt( "ctx" ).child();
		assertEquals( "Cntxt", ctx.name() );
		assertEquals( "[AVA, EFA]", ctx.domain().toString() );
	}
}
