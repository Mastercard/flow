package com.mastercard.test.flow.builder.mutable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.builder.concrete.ConcreteMetadata;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercising {@link MutableMetadata}
 */
@SuppressWarnings("static-method")
class MutableMetadataTest {

	/**
	 * Exercising builder API
	 */
	@Test
	void fields() {
		MutableMetadata mmd = new MutableMetadata();

		MutableMetadata ret = mmd
				.description( "description" )
				.tags( Tags.add( "tag" ) )
				.motivation( "motivation" )
				.trace( "trace" )
				.trace( s -> s.add( "addendum" ) );

		assertSame( mmd, ret );

		ConcreteMetadata cmd = mmd.build();

		assertEquals( "description", cmd.description() );
		assertEquals( "[tag]", cmd.tags().toString() );
		assertEquals( "motivation", cmd.motivation() );
		assertEquals( "trace [addendum]", cmd.trace() );
	}

	/**
	 * Exercising inheritance
	 */
	@Test
	void basis() {
		MutableMetadata mmd = new MutableMetadata(
				new ConcreteMetadata(
						"desc", Tags.tags( "a", "b" ), "mot", "trace" ) )
								.motivation( "overridden" );

		ConcreteMetadata cmd = mmd.build();

		assertEquals( "desc", cmd.description() );
		assertEquals( "[a, b]", cmd.tags().toString() );
		assertEquals( "overridden", cmd.motivation() );
		assertEquals(
				"com.mastercard.test.flow.builder.mutable.MutableMetadataTest.basis(MutableMetadataTest.java:<line_number>)",
				cmd.trace().replaceAll( ":\\d+\\)$", ":<line_number>)" ),
				"trace is not inherited" );
	}
}
