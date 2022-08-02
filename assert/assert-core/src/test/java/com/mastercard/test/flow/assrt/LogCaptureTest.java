package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.AbstractFlocessorTest.copypasta;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.LogEvent;

/**
 * Exercises log capture behaviour
 */
@SuppressWarnings("static-method")
class LogCaptureTest {
	/**
	 * A record of context switches made by {@link #CAPTURE}
	 */
	static final List<String> logCaptureLog = new ArrayList<>();

	/**
	 * Records context switches to {@link #logCaptureLog}
	 */
	static final LogCapture CAPTURE = new LogCapture() {

		@Override
		public void start( Flow flow ) {
			logCaptureLog.add( "Starting log capture for " + flow.meta().id() );
		}

		@Override
		public Stream<LogEvent> end( Flow flow ) {
			logCaptureLog.add( "Ending log capture for " + flow.meta().id() );
			return Stream.of( new LogEvent(
					"time", "level", "source", "log for " + flow.meta().id() ) );
		}
	};

	/**
	 * Clears the context switch log
	 */
	@BeforeEach
	void clearLog() {
		logCaptureLog.clear();
	}

	/**
	 * No log capture activity when there is no report
	 */
	@Test
	void noReportLogCapture() {
		TestFlocessor tf = new TestFlocessor( "noReportLogCapture", TestModel.abc() )
				.logs( CAPTURE )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		tf.execute();

		assertEquals( copypasta(),
				copypasta( logCaptureLog ) );
	}

	/**
	 * Log capture activity <i>does</i> happen when there is a report
	 */
	@Test
	void successCapture() {

		TestFlocessor tf = new TestFlocessor( "logCapture", TestModel.abc() )
				.system( State.LESS, B )
				.reporting( Reporting.QUIETLY )
				.logs( CAPTURE )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		tf.execute();

		assertEquals( copypasta(
				"Starting log capture for abc []",
				"Ending log capture for abc []" ),
				copypasta( logCaptureLog ) );

		Reader r = new Reader( tf.report() );
		Index idx = r.read();
		FlowData fd = r.detail( idx.entries.get( 0 ) );
		assertEquals( "time level source log for abc []",
				fd.logs.stream()
						.map( LogEvent::toString )
						.collect( joining( "\n" ) ) );
	}

	/**
	 * Log capture activity <i>does</i> happen when there is a report
	 */
	@Test
	void failCapture() {
		TestFlocessor tf = new TestFlocessor( "logCapture", TestModel.abc() )
				.system( State.LESS, B )
				.reporting( Reporting.QUIETLY )
				.logs( CAPTURE )
				.behaviour( assrt -> {
					assrt.actual().response( "unexpected data!".getBytes( UTF_8 ) );
				} );

		tf.execute();

		assertEquals( copypasta(
				"Starting log capture for abc []",
				"Ending log capture for abc []" ),
				copypasta( logCaptureLog ) );

		Reader r = new Reader( tf.report() );
		Index idx = r.read();
		FlowData fd = r.detail( idx.entries.get( 0 ) );
		assertEquals( "time level source log for abc []",
				fd.logs.stream()
						.map( LogEvent::toString )
						.collect( joining( "\n" ) ) );
	}

	/**
	 * Log capture activity <i>does</i> happen when there is a report
	 */
	@Test
	void errorCapture() {
		TestFlocessor tf = new TestFlocessor( "logCapture", TestModel.abc() )
				.system( State.LESS, B )
				.reporting( Reporting.QUIETLY )
				.logs( CAPTURE )
				.behaviour( assrt -> {
					throw new RuntimeException( "kaboom!" );
				} );

		tf.execute();

		assertEquals( copypasta(
				"Starting log capture for abc []",
				"Ending log capture for abc []" ),
				copypasta( logCaptureLog ) );

		Reader r = new Reader( tf.report() );
		Index idx = r.read();
		FlowData fd = r.detail( idx.entries.get( 0 ) );
		assertEquals( copypasta(
				"log for abc []",
				"Encountered error: java.lang.RuntimeException: kaboom!" ),
				copypasta( fd.logs.stream()
						.map( e -> e.message.replaceAll( "\tat .*", "" ) ) ) );
	}

	/**
	 * Log capture activity <i>does</i> happen when there is a report
	 */
	@Test
	void skipCapture() {
		TestFlocessor tf = new TestFlocessor( "logCapture", TestModel.abc() )
				.system( State.LESS, B )
				.reporting( Reporting.QUIETLY )
				.logs( CAPTURE )
				.behaviour( assrt -> {
					// no assertions
				} );

		tf.execute();

		assertEquals( copypasta(
				"Starting log capture for abc []",
				"Ending log capture for abc []" ),
				copypasta( logCaptureLog ) );

		Reader r = new Reader( tf.report() );
		Index idx = r.read();
		FlowData fd = r.detail( idx.entries.get( 0 ) );
		assertEquals( copypasta(
				"No assertions made",
				"log for abc []" ),
				copypasta( fd.logs.stream()
						.map( e -> e.message ) ) );
	}
}
