package com.mastercard.test.flow.doc.quick;

import static com.mastercard.test.flow.doc.quick.Actors.BEN;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.CorrelatedCapture;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.FlowExecution;
import com.mastercard.test.flow.assrt.junit5.FlowTest;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * The example of {@link AssertionTest}, run with flows in parallel and with
 * system logs attributed to flows by correlation identifier. Concurrent
 * execution needs the standard Jupiter switch
 * (<code>junit.jupiter.execution.parallel.enabled=true</code>) as well as the
 * class annotation; without it the same class runs serially with the same
 * results.
 */
// snippet-start:parallel
@FlowTest
@Execution(ExecutionMode.CONCURRENT)
class ParallelAssertionTest {

	/**
	 * @param execution Supplied by {@link FlowTest}; owns the run and closes the
	 *                  report when the class finishes
	 * @return Test instances, each emitted once the flows it must follow have
	 *         finished
	 */
	@TestFactory
	Stream<DynamicNode> tests( FlowExecution execution ) {
		return execution.flocessor( "Ben behaviour", new Greetings() )
				.system( State.LESS, BEN )
				.reporting( Reporting.QUIETLY )
				.logs( new BenLogs() )
				.behaviour( asrt -> {
					String input = new String( asrt.expected().request().content(), UTF_8 );
					// the system under test is told which flow is calling it...
					String output = BenSys.getGreetingResponse( input, asrt.correlation().id() );
					asrt.actual()
							.request( input.getBytes( UTF_8 ) )
							.response( output.getBytes( UTF_8 ) );
				} )
				.tests();
	}

	/**
	 * ... and it puts that identifier on every log event, so they can be routed to
	 * the right flow's report entry however the flows interleave
	 */
	private static class BenLogs implements CorrelatedCapture {
		@Override
		public void open( Collector collector ) {
			BenSys.listen( ( correlation, message ) -> collector.accept( correlation,
					new LogEvent( Instant.now().toString(), "INFO", BenSys.class.getName(), message ) ) );
		}

		@Override
		public void close() {
			BenSys.listen( null );
		}
	}
}
// snippet-end:parallel
