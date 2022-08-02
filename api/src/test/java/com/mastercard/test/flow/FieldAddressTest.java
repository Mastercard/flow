package com.mastercard.test.flow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Exercises the field access default method implementations
 */
@SuppressWarnings("static-method")
class FieldAddressTest {

	private static FieldAddress buildMocks() {
		Message req = Mockito.mock( Message.class );
		Interaction ntr = Mockito.mock( Interaction.class );
		Flow flw = Mockito.mock( Flow.class );

		Mockito.when( flw.root() ).thenReturn( ntr );
		Mockito.when( ntr.request() ).thenReturn( req );
		Mockito.when( req.get( "field" ) ).thenReturn( "value" );

		FieldAddress fa = Mockito.mock( FieldAddress.class );

		Mockito.when( fa.getFlow() ).thenCallRealMethod();
		Mockito.when( fa.getInteraction() ).thenCallRealMethod();
		Mockito.when( fa.getMessage() ).thenCallRealMethod();
		Mockito.when( fa.getValue() ).thenCallRealMethod();
		Mockito.when( fa.isComplete() ).thenCallRealMethod();

		Mockito.when( fa.flow() ).thenReturn( flw );
		Mockito.when( fa.interaction() ).thenReturn( Flow::root );
		Mockito.when( fa.message() ).thenReturn( Interaction::request );
		Mockito.when( fa.field() ).thenReturn( "field" );

		return fa;
	}

	/**
	 * Happy path - the address is complete
	 */
	@Test
	void getValue() {
		FieldAddress fa = buildMocks();
		Assertions.assertEquals( "value", fa.getValue().get() );
	}

	/**
	 * Incomplete address - no field
	 */
	@Test
	void noField() {
		FieldAddress fa = buildMocks();
		Mockito.when( fa.field() ).thenReturn( null );
		Assertions.assertFalse( fa.getValue().isPresent() );
	}

	/**
	 * Incomplete address - no message accessor
	 */
	@Test
	void noMessage() {
		FieldAddress fa = buildMocks();
		Mockito.when( fa.message() ).thenReturn( null );
		Assertions.assertFalse( fa.getValue().isPresent() );
	}

	/**
	 * Incomplete address - no interaction accessor
	 */
	@Test
	void noInteraction() {
		FieldAddress fa = buildMocks();
		Mockito.when( fa.interaction() ).thenReturn( null );
		Assertions.assertFalse( fa.getValue().isPresent() );
	}

	/**
	 * Incomplete address - no flow
	 */
	@Test
	void noFlow() {
		FieldAddress fa = buildMocks();
		Mockito.when( fa.flow() ).thenReturn( null );
		Assertions.assertFalse( fa.getValue().isPresent() );
	}

	/**
	 * Execrises address completeness
	 */
	@Test
	void isComplete() {
		{
			FieldAddress fa = buildMocks();
			Assertions.assertTrue( fa.isComplete() );
		}
		{
			FieldAddress fa = buildMocks();
			Mockito.when( fa.flow() ).thenReturn( null );
			Assertions.assertFalse( fa.isComplete() );
		}
		{
			FieldAddress fa = buildMocks();
			Mockito.when( fa.interaction() ).thenReturn( null );
			Assertions.assertFalse( fa.isComplete() );
		}
		{
			FieldAddress fa = buildMocks();
			Mockito.when( fa.message() ).thenReturn( null );
			Assertions.assertFalse( fa.isComplete() );
		}
		{
			FieldAddress fa = buildMocks();
			Mockito.when( fa.field() ).thenReturn( null );
			Assertions.assertFalse( fa.isComplete() );
		}
	}
}
