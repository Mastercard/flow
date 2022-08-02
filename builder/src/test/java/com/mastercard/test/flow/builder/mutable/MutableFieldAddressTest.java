package com.mastercard.test.flow.builder.mutable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.builder.CreatorTest;
import com.mastercard.test.flow.builder.concrete.ConcreteFieldAddress;
import com.mastercard.test.flow.builder.mock.Msg;

/**
 * Exercises {@link MutableFieldAddress}
 */
@SuppressWarnings("static-method")
class MutableFieldAddressTest {

	/**
	 * Exercising API
	 */
	@Test
	void fields() {
		MutableFieldAddress addr = new MutableFieldAddress();

		Flow flow = CreatorTest.metadataFlow();
		Interaction ntr = new MutableRootInteraction();
		Message msg = new Msg( "hi!" );

		MutableFieldAddress ret = addr
				.flow( flow )
				.interaction( f -> ntr )
				.message( i -> msg )
				.field( "field" );

		assertSame( addr, ret );

		ConcreteFieldAddress cfa = addr.build( null );
		assertSame( flow, cfa.flow() );
		assertSame( ntr, cfa.interaction().apply( null ) );
		assertSame( msg, cfa.message().apply( null ) );
		assertEquals( "field", cfa.field() );
	}

	/**
	 * Exercising value inheritance
	 */
	@Test
	void inheritance() {
		MutableFieldAddress addr = new MutableFieldAddress();

		Flow flow = CreatorTest.metadataFlow();
		Interaction ntr = new MutableRootInteraction();
		Message msg = new Msg( "hi!" );

		MutableFieldAddress ret = addr
				.flow( flow )
				.interaction( f -> ntr )
				.message( i -> msg )
				.field( "field" );

		assertSame( addr, ret );

		ConcreteFieldAddress cfa = addr.build( null );

		MutableFieldAddress child = new MutableFieldAddress( cfa );
		child.field( "child field" );

		cfa = child.build( null );
		assertSame( flow, cfa.flow() );
		assertSame( ntr, cfa.interaction().apply( null ) );
		assertSame( msg, cfa.message().apply( null ) );
		assertEquals( "child field", cfa.field() );
	}
}
