package consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.FlowExecution;
import com.mastercard.test.flow.assrt.junit5.FlowTest;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Explicitly selected by verify-report-commands.sh, including intentional
 * failures.
 */
@FlowTest
@ExtendWith(ReportCommand.ModeCheck.class)
class ReportCommand {
	private static final boolean FAULT = Boolean.getBoolean( "consumer.report.fault" );
	private static final boolean MIXED = Boolean.getBoolean( "consumer.report.mixed" );
	private static final AtomicInteger BODIES = new AtomicInteger();
	private static Temporary artifact;
	private static Temporary name;
	private static Path report;
	private static FlowExecution handle;

	/**
	 * A public Launcher host; its real native summary determines the external
	 * process exit.
	 */
	public static void main( String[] args ) {
		var summary = new SummaryGeneratingListener();
		LauncherFactory.create().execute( LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( ReportCommand.class ) ).build(), summary );
		var result = summary.getSummary();
		result.printTo( new PrintWriter( System.out, true ) );
		result.printFailuresTo( new PrintWriter( System.out, true ) );
		System.out.println( "REPORT-NATIVE started=" + result.getTestsStartedCount()
				+ " passed=" + result.getTestsSucceededCount() + " failed=" + result.getTestsFailedCount()
				+ " aborted=" + result.getTestsAbortedCount() + " containerFailed="
				+ result.getContainersFailedCount() );
		System.exit(
				result.getTestsStartedCount() != 3 ? 2 : result.getTotalFailureCount() == 0 ? 0 : 1 );
	}

	/**
	 * Configure a real filesystem fault in this command's isolated output
	 * directory.
	 */
	@BeforeAll
	static void configure() throws Exception {
		Path root = Files.createTempDirectory( Path.of( "target" ), "report-command-" );
		Path output = FAULT ? Files.writeString( root.resolve( "blocked" ), "not a directory" ) : root;
		artifact = AssertionOptions.ARTIFACT_DIR.temporarily( output.toString() );
		name = AssertionOptions.REPORT_NAME.temporarily( "report" );
		report = output.resolve( "report" );
		System.out.println( "REPORT-ROOT " + root.toAbsolutePath() );
	}

	/**
	 * Native failures are deliberately left to Surefire, not caught by an outer
	 * test.
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		handle = execution;
		return execution.flocessor( "report command", new BindingModel( false ) )
				.system( State.FUL, BindingModel.Actors.SYSTEM )
				.reporting( Reporting.QUIETLY )
				.independent( "synchronous command-local messages, no shared fixture state", f -> true )
				.behaviour( assertion -> {
					BODIES.incrementAndGet();
					if( MIXED && assertion.flow().meta().description().equals( "A" ) )
						throw new IllegalArgumentException( "controlled report-command SUT failure" );
					if( assertion.flow().meta().description().equals( "B" ) )
						assertEquals( "published", assertion.expected().request().assertable() );
					assertion.actual().response( assertion.expected().response().content() );
				} ).tests();
	}

	/**
	 * Verify final output separately from the native failure that controls the
	 * process exit.
	 */
	@AfterAll
	static void checkReport() {
		try {
			assertNotNull( handle );
			assertDoesNotThrow( handle::close );
			assertEquals( MIXED ? 2 : 3, BODIES.get() );
			if( FAULT )
				assertFalse( Files.exists( report.resolve( Writer.INDEX_FILE_NAME ) ) );
			else {
				assertTrue( Files.isRegularFile( report.resolve( Writer.INDEX_FILE_NAME ) ) );
				var entries = new Reader( report ).read().entries;
				assertEquals( 3, entries.size() );
				assertEquals( MIXED ? 1 : 3,
						entries.stream().filter( e -> e.tags.contains( Writer.PASS_TAG ) ).count() );
				if( MIXED ) {
					assertEquals( Set.of( Writer.ERROR_TAG ), entries.stream()
							.filter( e -> e.description.equals( "A" ) ).findFirst().orElseThrow().tags );
					assertTrue( entries.stream().filter( e -> e.description.equals( "B" ) )
							.findFirst().orElseThrow().tags.contains( Writer.SKIP_TAG ) );
				}
			}
			System.out.println( "REPORT-CHECK fault=" + FAULT + " mixed=" + MIXED
					+ " bodies=" + BODIES.get() );
		}
		finally {
			if( name != null )
				name.close();
			if( artifact != null )
				artifact.close();
		}
	}

	/** Observe the genuine invocation mode without replacing the dynamic body. */
	public static class ModeCheck implements InvocationInterceptor {
		@Override
		public void interceptDynamicTest( Invocation<Void> invocation,
				DynamicTestInvocationContext invocationContext, ExtensionContext context )
				throws Throwable {
			assertEquals( Boolean.getBoolean( "consumer.parallel" ) ? ExecutionMode.CONCURRENT
					: ExecutionMode.SAME_THREAD, context.getExecutionMode() );
			invocation.proceed();
		}
	}
}
