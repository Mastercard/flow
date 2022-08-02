package com.mastercard.test.flow.validation.junit5;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

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
				+ "Test 'accept' result 'SUCCESSFUL'\n"
				+ "Test 'fail' result 'FAILED'\n"
				+ "Test 'cmp fail' result 'FAILED'\n"
				+ "Test 'pass' result 'SUCCESSFUL'",
				results.entrySet().stream()
						.map( e -> String.format(
								"Test '%s' result '%s'",
								e.getKey().getDisplayName(),
								e.getValue().getStatus() ) )
						.collect( joining( "\n" ) ) );
	}

}
