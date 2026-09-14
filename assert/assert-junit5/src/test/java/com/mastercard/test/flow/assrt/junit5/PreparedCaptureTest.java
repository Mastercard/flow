package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.LogEvent;

/** Capture lifecycle in an actual provider-free native serial invocation. */
@SuppressWarnings("static-method")
class PreparedCaptureTest {
	@Test
	void sourceFailureIsNonfatalAndClosedBeforeDecorationAndNativeCompletion() {
		CaptureFactory.events.clear();
		CaptureFactory.runner = null;
		List<Throwable> failures = new ArrayList<>();
		List<String> leaves = new ArrayList<>();
		FlowExecutionTest.execute( CaptureFactory.class, "false", new TestExecutionListener() {
			@Override
			public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
				result.getThrowable().ifPresent( failures::add );
				if( id.isTest() ) {
					leaves.add( id.getDisplayName() + ":" + result.getStatus() );
					CaptureFactory.events.add( "native finished" );
				}
			}
		} );
		assertEquals( List.of(), failures );
		assertEquals( List.of( "success []:SUCCESSFUL" ), leaves );
		assertEquals( List.of( "begin", "body", "end", "read", "close", "decorate", "native finished" ),
				CaptureFactory.events );
		Reader reader = new Reader( CaptureFactory.runner.report() );
		assertTrue( reader.detail( reader.read().entries.get( 0 ) ).logs.stream()
				.anyMatch( e -> e.message.contains( "Log capture" ) ) );
	}

	@FlowTest
	static class CaptureFactory {
		static final List<String> events = new ArrayList<>();
		static PreparedFlocessor runner;

		@TestFactory
		Stream<DynamicNode> flows( FlowExecution execution ) {
			runner = execution.flocessor( "native capture scope", PreparedFlowLifecycleTest.model(
					new Mdl().flows().findFirst().orElseThrow() ) )
					.system( State.LESS, Actrs.BEN ).reporting( Reporting.QUIETLY )
					.logs( new LogCapture() {
						@Override
						public void start( Flow flow ) {
							events.add( "begin" );
						}

						@Override
						public Stream<LogEvent> end( Flow flow ) {
							events.add( "end" );
							return Stream.of( new LogEvent( "time", "INFO", "source", "live" ) )
									.peek( e -> {
										events.add( "read" );
										throw new IllegalStateException( "source read" );
									} )
									.onClose( () -> events.add( "close" ) );
						}
					} ).behaviour( a -> {
						events.add( "body" );
						a.actual().response( a.expected().response().content() );
					} ).motivation( ( text, a ) -> {
						events.add( "decorate" );
						return text;
					} );
			Stream<DynamicNode> descriptions = runner.tests();
			assertEquals( List.of(), events, "preparation must not begin capture" );
			return descriptions;
		}
	}
}
