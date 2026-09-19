package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Demonstrates the execution {@link MotivationCustomizer}
 */
class MotivationCustomizerTest {

	/**
	 * Update motivation in the report even when flow is not processed due to some
	 * exception
	 */
	@Test
	void motivationNoBehaviour() {
		try( TestFlocessor tf = new TestFlocessor( "motivation without behaviour", TestModel.abc() )
				.motivation( ( motivation, assertion ) -> motivation + "Common motivation" )
				.reporting( Reporting.QUIETLY )
				.system( AbstractFlocessor.State.LESS, TestModel.Actors.B ) ) {

			tf.execute();

			assertEquals( "abc [] error No test behaviour specified", tf.events() );

			// This is also recorded to the report
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			FlowData fd = r.detail( ie );
			assertEquals( "Common motivation", fd.motivation );
		}
	}

	/**
	 * Motivation can be updated in the report after the flow is processed
	 */
	@Test
	void motivation() {
		try( TestFlocessor tf = new TestFlocessor( "motivation", TestModel.abc() )
				.motivation( ( motivation, assertion ) -> {
					String baseUrl = "https://www.google.com/search?q=";
					String queryToken = new String( assertion.expected().request().content() ).substring( 0,
							1 );
					queryToken += new String( assertion.actual().response() ).substring( 0, 1 );
					String logLink = baseUrl + queryToken;
					return motivation + "\n\n[View Logs](" + logLink + ")";
				} )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} )
				.reporting( Reporting.QUIETLY )
				.system( AbstractFlocessor.State.LESS, TestModel.Actors.B ) ) {

			tf.execute();

			// This is also recorded to the report
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			FlowData fd = r.detail( ie );
			assertEquals( "\n\n[View Logs](https://www.google.com/search?q=AB)", fd.motivation );
		}
	}

	/**
	 * When a later interaction's behaviour throws, the assertions that were already
	 * compared are still customised, in order, before the failing one
	 */
	@Test
	void earlierAssertionsAreCustomisedOnError() {
		try( TestFlocessor tf = new TestFlocessor( "customise on error", TestModel.abcde() )
				.motivation( ( motivation, assertion ) -> motivation + " "
						+ assertion.expected().responder().name() )
				.behaviour( assrt -> {
					if( assrt.expected().responder() == TestModel.Actors.D ) {
						throw new IllegalStateException( "D is down" );
					}
					assrt.actual().response( assrt.expected().response().content() );
				} )
				.reporting( Reporting.QUIETLY )
				.system( AbstractFlocessor.State.LESS, TestModel.Actors.B, TestModel.Actors.D ) ) {

			tf.execute();

			assertEquals( "abcde [] ERROR", tf.results() );
			Reader r = new Reader( tf.report() );
			FlowData fd = r.detail( r.read().entries.get( 0 ) );
			assertEquals( " B D", fd.motivation );
		}
	}

	/**
	 * The customizer decorates the report, so it is not invoked when no report is
	 * written
	 */
	@Test
	void customizerRequiresReport() {
		try( TestFlocessor tf = new TestFlocessor( "customizer without report", TestModel.abc() )
				.motivation( ( motivation, assertion ) -> {
					throw new IllegalStateException( "customised without a report" );
				} )
				.behaviour( assrt -> assrt.actual().response( assrt.expected().response().content() ) )
				.system( AbstractFlocessor.State.LESS, TestModel.Actors.B ) ) {
			tf.execute();
			assertEquals( "abc [] SUCCESS", tf.results() );
			assertNull( tf.report() );
		}
	}

	/**
	 * A customizer fault is reduced to a diagnostic only when the report is
	 * final-only and the fault is an ordinary runtime exception. The flow then
	 * passes and its entry is published with the original motivation. Otherwise the
	 * fault is the caller's to see and pre-empts the flow's entry, but never
	 * latches the report.
	 *
	 * @param finalOnly Whether the report is published once, on completion
	 * @param ordinary  Whether the fault is an ordinary runtime exception
	 * @param directory Isolated artifact directory
	 */
	@ParameterizedTest
	@CsvSource({ "true,true", "true,false", "false,true" })
	void customizerFault( boolean finalOnly, boolean ordinary, @TempDir Path directory ) {
		Throwable fault = ordinary
				? new IllegalStateException( "decoration failed" )
				: new AssertionError( "decoration failed" );
		try( Diagnostics diagnostic = new Diagnostics( Faults.class );
				Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( directory.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "decorated" );
				TestFlocessor tf = new TestFlocessor( "customizer fault", TestModel.abc() )
						.motivation( ( motivation, assertion ) -> {
							if( fault instanceof Error error ) {
								throw error;
							}
							throw (RuntimeException) fault;
						} )
						.behaviour( assrt -> assrt.actual().response( assrt.expected().response().content() ) )
						.reporting( Reporting.QUIETLY )
						.system( AbstractFlocessor.State.LESS, TestModel.Actors.B ) ) {
			if( finalOnly ) {
				tf.finalOnlyReporting();
			}
			Flow flow = tf.flows().findFirst().orElseThrow();

			if( finalOnly && ordinary ) {
				tf.process( flow );
				assertEquals( List.of(
						"Motivation customisation failed for abc []: java.lang.IllegalStateException" ),
						diagnostic.messages() );
			}
			else {
				assertSame( fault, assertThrows( Throwable.class, () -> tf.process( flow ) ) );
				assertEquals( List.of(), diagnostic.messages() );
			}

			tf.completeProcessing();
			if( finalOnly ) {
				Reader r = new Reader( tf.report() );
				Index index = r.read();
				assertEquals( ordinary ? 1 : 0, index.entries.size() );
				if( ordinary ) {
					assertEquals( "", r.detail( index.entries.get( 0 ) ).motivation );
				}
			}
			else {
				assertNull( tf.report(), "the fault pre-empted the only report update" );
			}
		}
	}

}
