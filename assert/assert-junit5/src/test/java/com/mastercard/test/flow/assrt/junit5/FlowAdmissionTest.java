package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;

/** Exercises producer-owned flow admission independently of Jupiter nodes. */
@SuppressWarnings("static-method")
class FlowAdmissionTest {

	/** Only completion events can make a withheld successor eligible. */
	@Test
	void completionReleasesAdmission() throws Exception {
		Flow source = flow( "source" );
		Flow dependent = flow( "dependent", source );
		Flow independent = flow( "independent" );
		FlowAdmission admission = new FlowAdmission( List.of( source, dependent, independent ), false );
		List<Integer> emitted = new ArrayList<>();

		assertEquals( 3, admission.getExactSizeIfKnown() );
		assertTrue( admission.tryAdvance( emitted::add ) );
		assertTrue( admission.tryAdvance( emitted::add ) );
		assertEquals( List.of( 0, 2 ), emitted );
		assertEquals( 1, admission.getExactSizeIfKnown() );

		ExecutorService producer = Executors.newSingleThreadExecutor();
		try {
			Future<Boolean> next = producer.submit( () -> admission.tryAdvance( emitted::add ) );
			assertThrows( TimeoutException.class, () -> next.get( 100, TimeUnit.MILLISECONDS ) );

			admission.finished( 2 );
			assertThrows( TimeoutException.class, () -> next.get( 100, TimeUnit.MILLISECONDS ) );

			admission.finished( 0 );
			assertTrue( next.get( 10, TimeUnit.SECONDS ) );
			assertEquals( List.of( 0, 2, 1 ), emitted );
			assertEquals( 0, admission.getExactSizeIfKnown() );
			assertFalse( admission.tryAdvance( emitted::add ) );
		}
		finally {
			producer.shutdownNow();
			assertTrue( producer.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/** Queued completions still release simultaneously-ready flows canonically. */
	@Test
	void completionOrderDoesNotChangeCanonicalEmission() {
		Flow firstSource = flow( "firstSource" );
		Flow secondSource = flow( "secondSource" );
		Flow firstDependent = flow( "firstDependent", firstSource );
		Flow secondDependent = flow( "secondDependent", secondSource );
		FlowAdmission admission = new FlowAdmission(
				List.of( firstSource, secondSource, firstDependent, secondDependent ), false );
		List<Integer> emitted = new ArrayList<>();

		admission.tryAdvance( emitted::add );
		admission.tryAdvance( emitted::add );
		admission.finished( 1 );
		admission.finished( 0 );
		admission.tryAdvance( emitted::add );
		admission.tryAdvance( emitted::add );

		assertEquals( List.of( 0, 1, 2, 3 ), emitted );
	}

	private static Flow flow( String name, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}
}
