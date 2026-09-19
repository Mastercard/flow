package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.assrt.log.CorrelatedTail;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Correlated log capture from a file in a concurrent Launcher run
 */
@SuppressWarnings("static-method")
class PreparedCaptureTest {

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
		return execution.flocessor( "tailed", FlowExecutionTest.modelOf( List.of( first, second ) ) )
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
			Files.writeString( log, String.join( "\n", lines ) + "\n", StandardOpenOption.APPEND );
		}
		catch( IOException e ) {
			throw new UncheckedIOException( e );
		}
	}
}
