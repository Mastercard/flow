package com.mastercard.test.flow.validation.junit4;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * Runs {@link ExampleTest} and assert on on the results
 */
@SuppressWarnings("static-method")
public class MetaTest {

	/**
	 * Runs {@link ExampleTest} and assert on on the results
	 */
	@Test
	public void test() {
		JUnitCore juc = new JUnitCore();
		Map<String, String> results = new LinkedHashMap<>();
		juc.addListener( new RunListener() {
			@Override
			public void testFinished( Description description ) throws Exception {
				// every test hit this method, even if they've failed or skipped
				if( !results.containsKey( description.getDisplayName() ) ) {
					results.put( description.getDisplayName(), "SUCCESSFUL" );
				}
			}

			@Override
			public void testFailure( Failure failure ) throws Exception {
				results.put( failure.getDescription().getDisplayName(), "FAILED" );
			}

			@Override
			public void testAssumptionFailure( Failure failure ) {
				results.put( failure.getDescription().getDisplayName(), "ABORTED" );
			}
		} );

		try {
			ExampleTest.active = true;
			juc.run( ExampleTest.class );
		}
		finally {
			ExampleTest.active = false;
		}

		assertEquals( ""
				+ "Test 'accept' result 'SUCCESSFUL'\n"
				+ "Test 'fail' result 'FAILED'\n"
				+ "Test 'cmp fail' result 'FAILED'\n"
				+ "Test 'pass' result 'SUCCESSFUL'",
				results.entrySet().stream()
						.map( e -> String.format( "Test '%s' result '%s'",
								e.getKey().replaceAll(
										"test\\[(.+)\\]\\(com.mastercard.test.flow.validation.junit4.ExampleTest\\)",
										"$1" ),
								e.getValue() ) )
						.collect( joining( "\n" ) ) );
	}
}
