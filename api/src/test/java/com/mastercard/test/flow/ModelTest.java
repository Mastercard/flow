/**
 * Copyright (c) 2022 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.util.Tags;

/**
 * Exercises the tiny scrap of implementation in the {@link Model} interface
 */
@SuppressWarnings("static-method")
class ModelTest {

	/**
	 * Asserts that the default implementation of {@link Model#flows()} simply calls
	 * to {@link Model#flows(java.util.Set, java.util.Set)} with the empty tag sets
	 */
	@Test
	void flows() {
		Flow flow = mock( Flow.class );
		Model model = Mockito.mock( Model.class );

		when( model.flows() ).thenCallRealMethod();
		when( model.flows( Tags.empty(), Tags.empty() ) )
				.thenReturn( Stream.of( flow ) );

		assertSame( flow, model.flows().findAny().get() );
	}
}
