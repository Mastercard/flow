package com.mastercard.test.flow.assrt.junit5;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor.Type;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runs {@link ExampleTest} and assert on on the results
 */
@SuppressWarnings("static-method")
class MetaTest {

	/**
	 * Runs {@link ExampleTest} and assert on on the results
	 */
	@Test
	void test() {

		LauncherDiscoveryRequest ldr = LauncherDiscoveryRequestBuilder.request()
				.selectors( DiscoverySelectors.selectClass( ExampleTest.class ) )
				.build();
		Map<TestIdentifier, TestExecutionResult> results = new LinkedHashMap<>();
		try {
			ExampleTest.active = true;
			LauncherFactory.create().execute( ldr, new TestExecutionListener() {
				@Override
				public void executionFinished( TestIdentifier testIdentifier,
						TestExecutionResult testExecutionResult ) {
					if( testIdentifier.getType() == Type.TEST ) {
						results.put( testIdentifier, testExecutionResult );
					}
				}
			} );
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
						.map( e -> String.format(
								"Test '%s' result '%s'",
								e.getKey().getDisplayName(),
								e.getValue().getStatus() ) )
						.collect( joining( "\n" ) ) );

		// check that the test sources look ok
		Pattern src = Pattern.compile( "ClassSource"
				+ " \\[className = 'com.mastercard.test.flow.assrt.junit5.mock.Mdl',"
				+ " filePosition = FilePosition \\[line = (\\d+), column = -1\\]\\]" );
		assertEquals( "32, 45, 61, 27, 41, 55, 22, 37, 49",
				results.keySet().stream()
						.map( ti -> ti.getSource().get().toString() )
						.map( src::matcher )
						.filter( Matcher::matches )
						.map( m -> m.group( 1 ) )
						.collect( Collectors.joining( ", " ) ),
				"Flow definition line numbers" );

	}

}
