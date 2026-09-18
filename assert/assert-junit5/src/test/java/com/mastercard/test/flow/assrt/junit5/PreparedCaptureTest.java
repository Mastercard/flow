package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.CorrelatedCapture;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.log.CorrelatedTail;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.LogEvent;
import com.mastercard.test.flow.util.Option.Temporary;

/** Capture lifecycle in an actual provider-free native serial invocation. */
@SuppressWarnings("static-method")
class PreparedCaptureTest {
	@Test
	void sourceFailureIsNonfatalAndClosedBeforeDecorationAndNativeCompletion() {
		CaptureFactory.events.clear();
		CaptureFactory.runner = null;
		List<Throwable> failures = new ArrayList<>();
		List<String> leaves = new ArrayList<>();
		FlowExecutionTest.execute( CaptureFactory.class, false, new TestExecutionListener() {
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

	/**
	 * Two flows execute concurrently on the native pool. A shared push source emits
	 * by correlation identifier only; the final report attributes each event to
	 * exactly the flow whose identifier it carried, regardless of which worker or
	 * moment produced it.
	 */
	@Test
	void parallelFlowsAreAttributedByCorrelationOnly( @TempDir Path dir ) throws Exception {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "correlated" ) ) {
			CorrelatedFactory.reset();
			List<Throwable> failures = new ArrayList<>();
			List<String> leaves = Collections.synchronizedList( new ArrayList<>() );
			FlowExecutionTest.execute( CorrelatedFactory.class, true, new TestExecutionListener() {
				@Override
				public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
					result.getThrowable().ifPresent( failures::add );
					if( id.isTest() ) {
						leaves.add( id.getDisplayName() + ":" + result.getStatus() );
					}
				}
			} );
			assertEquals( List.of(), failures );
			assertEquals( 2, leaves.size(), leaves::toString );
			assertTrue( leaves.stream().allMatch( l -> l.endsWith( ":SUCCESSFUL" ) ), leaves::toString );
			assertEquals( List.of( "open", "flush", "flush", "flush", "close" ),
					CorrelatedFactory.source.lifecycle );

			// Each flow's report holds exactly the events that carried its identifier,
			// including one that arrived after it had ended. An event for a flow not
			// yet begun does not attach to the flow that happened to be running.
			Reader reader = new Reader( dir.resolve( "correlated" ) );
			Map<String, List<String>> logs = new TreeMap<>();
			for( Entry entry : reader.read().entries ) {
				logs.put( entry.description, reader.detail( entry ).logs.stream()
						.map( e -> e.message ).collect( Collectors.toList() ) );
			}
			for( String flow : List.of( "first", "second" ) ) {
				String other = flow.equals( "first" ) ? "second" : "first";
				switch( CorrelatedFactory.outcomes.get( other ) ) {
					case ACCEPTED, LATE -> assertEquals( List.of( flow + "-1", flow + "-2" ),
							logs.get( flow ) );
					case UNATTRIBUTED -> assertEquals( List.of( flow + "-1" ), logs.get( flow ) );
					default -> throw new AssertionError( CorrelatedFactory.outcomes.toString() );
				}
			}
		}
	}

	/**
	 * Concurrent flows log interleaved lines to one file; each report entry gets
	 * only the lines carrying its identifier, including one written after the flow
	 * finished.
	 *
	 * @param dir Isolated artifact directory
	 * @throws Exception On filesystem failure
	 */
	@Test
	void interleavedFileLinesAreAttributedByIdentifier( @TempDir Path dir ) throws Exception {
		Path log = dir.resolve( "sut.log" );
		Files.writeString( log, "0 [-] INFO boot started before the run\n" );
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "tailed" ) ) {
			TailFactory.log = log;
			FlowExecutionTest.Run run = FlowExecutionTest.execute( TailFactory.class, true );
			assertEquals( List.of(), run.failures, run.failures::toString );
			assertEquals( 2, run.results.size(), run.results::toString );
			Reader reader = new Reader( dir.resolve( "tailed" ) );
			Map<String, Set<String>> logs = new TreeMap<>();
			for( Entry entry : reader.read().entries ) {
				// the tail prefixes content with the header text it did not capture
				logs.put( entry.description, reader.detail( entry ).logs.stream()
						.map( e -> e.message.replaceFirst( "^\\[\\]\\s+", "" ) )
						.collect( Collectors.toSet() ) );
			}
			assertEquals( Set.of( "handling first", "more for first", "late for first" ),
					logs.get( "first" ) );
			assertEquals( Set.of( "handling second", "more for second", "late for second" ),
					logs.get( "second" ) );
		}
	}

	/** A push source shared by concurrently-executing flows */
	static class PushSource implements CorrelatedCapture {
		final List<String> lifecycle = Collections.synchronizedList( new ArrayList<>() );
		volatile Collector collector;

		@Override
		public void open( Collector c ) {
			lifecycle.add( "open" );
			collector = c;
		}

		@Override
		public void flush() {
			lifecycle.add( "flush" );
		}

		@Override
		public void close() {
			lifecycle.add( "close" );
		}
	}
}

/** Parallel native factory; top-level as the native profile requires. */
@FlowTest
class CorrelatedFactory {
	static PreparedCaptureTest.PushSource source;
	static Set<Thread> threads;
	static Map<String, CorrelatedCapture.Outcome> outcomes;
	static CountDownLatch bothEntered;

	static void reset() {
		source = new PreparedCaptureTest.PushSource();
		threads = ConcurrentHashMap.newKeySet();
		outcomes = new ConcurrentHashMap<>();
		bothEntered = new CountDownLatch( 2 );
	}

	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		Flow first = Creator.build( f -> f.meta( m -> m.description( "first" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "req" ) ).response( new Msg( "rsp" ) ) ) );
		Flow second = Creator.build( f -> f.meta( m -> m.description( "second" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "req" ) ).response( new Msg( "rsp" ) ) ) );
		return execution.flocessor( "correlated parallel", PreparedFlowLifecycleTest.model(
				first, second ) )
				.system( State.LESS, Actrs.BEN ).reporting( Reporting.QUIETLY )
				.logs( source )
				.correlation( f -> f.meta().description() )
				.behaviour( a -> {
					threads.add( Thread.currentThread() );
					String me = a.correlation().id();
					String other = me.equals( "first" ) ? "second" : "first";
					// the "system" logs for both flows from this worker: while both are
					// active the other's event is accepted, otherwise it is late-labelled
					source.collector.accept( me, event( me + "-1" ) );
					bothEntered.countDown();
					try {
						bothEntered.await( 2, TimeUnit.SECONDS );
					}
					catch( InterruptedException e ) {
						throw new IllegalStateException( e );
					}
					outcomes.put( me, source.collector.accept( other, event( other + "-2" ) ) );
					source.collector.accept( "stranger", event( "nobody's" ) );
					a.actual().response( a.expected().response().content() );
				} ).tests();
	}

	private static LogEvent event( String message ) {
		return new LogEvent( "time", "INFO", "sut", message );
	}
}

/** Parallel factory whose flows log interleaved lines to one shared file. */
@FlowTest
class TailFactory {
	static Path log;

	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		Flow first = Creator.build( f -> f.meta( m -> m.description( "first" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "req" ) ).response( new Msg( "rsp" ) ) ) );
		Flow second = Creator.build( f -> f.meta( m -> m.description( "second" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "req" ) ).response( new Msg( "rsp" ) ) ) );
		CountDownLatch bothLogged = new CountDownLatch( 2 );
		return execution.flocessor( "tailed", PreparedFlowLifecycleTest.model( first, second ) )
				.system( State.LESS, Actrs.BEN ).reporting( Reporting.QUIETLY )
				.logs( new CorrelatedTail( log,
						"^(?<time>\\d+) \\[(?<correlation>[^\\]]*)\\] (?<level>[A-Z]+) (?<source>\\S+) " ) )
				.correlation( f -> f.meta().description() )
				.behaviour( a -> {
					String me = a.correlation().id();
					String other = me.equals( "first" ) ? "second" : "first";
					// the "system" interleaves its output for both flows, and keeps writing
					// about the other flow after this one has returned
					append( "1 [" + me + "] INFO sut handling " + me,
							"2 [" + other + "] INFO sut more for " + other );
					bothLogged.countDown();
					try {
						bothLogged.await( 2, TimeUnit.SECONDS );
					}
					catch( InterruptedException e ) {
						throw new IllegalStateException( e );
					}
					a.actual().response( a.expected().response().content() );
					append( "3 [" + other + "] INFO sut late for " + other );
				} ).tests();
	}

	private static synchronized void append( String... lines ) {
		try {
			Files.writeString( log, String.join( "\n", lines ) + "\n",
					java.nio.file.StandardOpenOption.APPEND );
		}
		catch( java.io.IOException e ) {
			throw new java.io.UncheckedIOException( e );
		}
	}
}
