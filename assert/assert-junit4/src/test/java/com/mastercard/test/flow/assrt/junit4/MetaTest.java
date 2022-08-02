package com.mastercard.test.flow.assrt.junit4;

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
				// for cases that throw exceptions...
				+ "Test 'error []' result 'FAILED'\n"
				// failure is assumed to be transient, so we try again with descendants
				+ "Test 'errorChild []' result 'FAILED'\n"
				// but we know that we didn't get any actuals, so dependents are skipped
				+ "Test 'errorDependent []' result 'ABORTED'\n"

				// for cases that get unexpected actuals...
				+ "Test 'failure []' result 'FAILED'\n"
				// children are likely to get the same failure
				+ "Test 'failureChild []' result 'ABORTED'\n"
				// but we did get actuals, so dependencies are given a chance
				+ "Test 'failureDependent []' result 'FAILED'\n"

				// everything works!
				+ "Test 'success []' result 'SUCCESSFUL'\n"
				+ "Test 'successChild []' result 'SUCCESSFUL'\n"
				+ "Test 'successDependent []' result 'SUCCESSFUL'",
				results.entrySet().stream()
						.map( e -> String.format( "Test '%s' result '%s'",
								e.getKey().replaceAll(
										"test\\[(.+)\\]\\(com.mastercard.test.flow.assrt.junit4.ExampleTest\\)", "$1" ),
								e.getValue() ) )
						.collect( joining( "\n" ) ) );
	}
}
