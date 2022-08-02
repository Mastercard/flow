package com.mastercard.test.flow.builder;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.util.Tags;

/**
 * Exercises {@link Chain}
 */
@SuppressWarnings("static-method")
class ChainTest {

	/**
	 * PIT appears to run tests multiple times from the same classloader when
	 * computing test coverage, so we need to clear the static state that generated
	 * chain IDs depend on
	 *
	 * @throws Exception field access failure
	 */
	@BeforeEach
	void clearClassCounts() throws Exception {
		Field ccf = Chain.class.getDeclaredField( "classCounts" );
		ccf.setAccessible( true );
		((Map<?, ?>) ccf.get( null )).clear();
	}

	/**
	 * Chain tags are not inherited
	 */
	@Test
	void inheritance() {
		Flow base = Creator.build( new Chain( "melody" ) );
		Flow unchained = Deriver.build( base );

		Assertions.assertEquals( "[]",
				unchained.meta().tags().toString() );
	}

	/**
	 * An explicitly-defined chain id
	 */
	@Test
	void explicit() {
		Chain chain = new Chain( "of_fools" );

		Flow chained = Creator.build( chain );
		Flow derived = Deriver.build( chained, chain );

		Assertions.assertEquals( "[chain:of_fools]",
				chained.meta().tags().toString() );
		Assertions.assertEquals( "[chain:of_fools]",
				derived.meta().tags().toString() );
	}

	/**
	 * You can't be on multiple chains
	 */
	@Test
	void overwrite() {
		Chain a = new Chain( "for_five_long_years" );
		Chain b = new Chain( "i_thought_you_were_my_man" );

		Flow flow = Creator.build( flw -> flw.meta( data -> data
				.tags( Tags.add( "normal_tag" ) ) ),
				a, b );

		Assertions.assertEquals( "[chain:i_thought_you_were_my_man, normal_tag]",
				flow.meta().tags().toString() );
	}

	/**
	 * Generated chain ids
	 */
	@Test
	void generated() {
		List<String> expect = Arrays.asList( "fe49295f", "fe492960", "fe492961" );
		for( String expected : expect ) {
			Chain chain = new Chain();
			Flow chained = Creator.build( chain );
			Assertions.assertEquals( "[chain:" + expected + "]",
					chained.meta().tags().toString() );
		}
	}

	/**
	 * Exercising chain removal utility
	 */
	@Test
	void unlink() {
		Flow unchained = Creator.build( new Chain( "unwanted" ), Chain.unlink() );

		Assertions.assertEquals( "[]",
				unchained.meta().tags().toString() );
	}
}
