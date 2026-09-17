package com.mastercard.test.flow.assrt.junit5;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.opentest4j.AssertionFailedError;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.LogCapture;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.builder.Deriver;
import com.mastercard.test.flow.report.LocalBrowse;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.util.Option.Temporary;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;

/** Public caller/Launcher regression seam for the restricted native tracer. */
class FlowParallelBindingTest {
	/**
	 * Completed chain failures retain the real dependent and derived-flow oracle.
	 */
	@Test
	void chainsRetainHistoryErrorsDerivedSuppressionsAndNativeDurations() {
		List<String> expected = List.of( "error [chain:error]:FAILED",
				"errorChild [chain:error]:FAILED", "errorDependent [chain:error]:ABORTED",
				"failure [chain:failure]:FAILED", "failureChild [chain:failure]:ABORTED",
				"failureDependent [chain:failure]:FAILED", "success [chain:success]:SUCCESSFUL",
				"successChild [chain:success]:SUCCESSFUL", "successDependent [chain:success]:SUCCESSFUL" );
		for( String mode : List.of( "false", "true" ) ) {
			Evidence e = execute( "oracle-chain", mode, 12 );
			assertEquals( expected, e.results.stream().sorted().toList(), e.failures::toString );
			assertEquals( 9, e.starts.size() );
			assertEquals( 7, e.events.stream().filter( s -> s.startsWith( "body:" ) ).count() );
			assertEquals( 9, e.durations.size() );
			assertTrue( e.durations.values().stream().allMatch( n -> n > 0 ) );
			assertEquals( 9, e.sources.get() );
			assertDoesNotThrow( e.handle::close );
		}
	}

	/** Both direct successors can finish before an unrelated slow root. */
	@Test
	void dependencyForkProgressesWithoutAnUnrelatedLevelBarrier() {
		Evidence e = execute( "fork", "true", 12 );
		assertEquals( List.of(), e.failures );
		assertEquals( 4, e.starts.size() );
		assertEquals( 4, e.results.size() );
		assertTrue( e.events.indexOf( "finish:B []" ) < e.events.indexOf( "end:C" ) );
		assertTrue( e.events.indexOf( "finish:D []" ) < e.events.indexOf( "end:C" ) );
		assertEquals( "published", e.bound );
		assertDoesNotThrow( e.handle::close );
	}

	/**
	 * Actual native evidence may be redelivered, but cannot change after delivery.
	 */
	@Test
	void realNativeDuplicateIsIdempotentAndConflictFailsTheBackstop() {
		for( String scenario : List.of( "duplicate", "duplicate-conflict" ) ) {
			Evidence e = execute( scenario, "true", 12 );
			assertEquals( List.of( "A []:SUCCESSFUL" ), e.results );
			assertEquals( 1, e.starts.size() );
			assertEquals( 1, e.events.stream().filter( s -> s.startsWith( "body:" ) ).count() );
			if( scenario.equals( "duplicate" ) ) {
				assertEquals( List.of(), e.failures );
				assertDoesNotThrow( e.handle::close );
			}
			else {
				assertFalse( e.failures.isEmpty(),
						"latched listener fault must fail outside the callback" );
				assertThrows( IllegalStateException.class, e.handle::close );
			}
		}
	}

	/**
	 * External suppression cannot turn an actual Flow error into processed data.
	 */
	@Test
	void suppressedErrorAndActualTimeoutKeepDependentAbortAndIndependentProgress() {
		for( String scenario : List.of( "swallow-error", "timeout" ) ) {
			Evidence e = execute( scenario, "true", 12 );
			assertEquals( List.of( "A []:" + (scenario.equals( "timeout" ) ? "FAILED" : "SUCCESSFUL"),
					"B []:ABORTED", "C []:SUCCESSFUL" ), e.results.stream().sorted().toList() );
			assertEquals( 3, e.starts.size() );
			assertFalse( e.events.contains( "body:B" ) );
			assertTrue( e.events.contains( "body:C" ) );
			if( scenario.equals( "timeout" ) ) {
				assertTrue(
						e.failures.stream().anyMatch(
								failure -> failure.getCause() instanceof java.net.SocketTimeoutException ),
						e.failures::toString );
			}
			assertDoesNotThrow( e.handle::close );
		}
	}

	/**
	 * Selected ancestors constrain History without auto-selecting missing bases.
	 */
	@Test
	void basisOnlyAndAbsentIntermediateDoNotExpandTheSelectedWorkload() {
		for( String scenario : List.of( "basis-only", "basis-gap" ) ) {
			for( String mode : List.of( "false", "true" ) ) {
				Evidence e = execute( scenario, mode, 12 );
				assertEquals( scenario.equals( "basis-only" ) ? List.of( "D []:SUCCESSFUL" )
						: List.of( "A []:FAILED", "D []:ABORTED" ), e.results.stream().sorted().toList() );
				assertEquals( scenario.equals( "basis-only" ) ? 1 : 2, e.starts.size() );
				assertDoesNotThrow( e.handle::close );
			}
		}
	}

	/** A basis gap can legitimately put the derived flow first in serial order. */
	@Test
	void laterCanonicalAncestorCannotChangeEarlierDerivedEligibility() {
		for( String mode : List.of( "false", "true" ) ) {
			Evidence e = execute( "basis-inverted", mode, 12 );
			assertEquals( List.of( "register:A []", "register:Z []" ),
					e.events.stream().filter( s -> s.startsWith( "register:" ) ).toList(),
					"the absent intermediate basis must not be selected" );
			assertEquals( List.of( "A []:SUCCESSFUL", "Z []:FAILED" ),
					e.results.stream().sorted().toList(), e.failures::toString );
			assertTrue( e.events.containsAll( List.of( "body:A", "body:Z" ) ) );
			assertTrue( e.events.indexOf( "finish:A []" ) < e.events.indexOf( "body:Z" ) );
			assertDoesNotThrow( e.handle::close );
		}
	}

	/** Selected bases retain the existing serial error/assertion/abort oracle. */
	@Test
	void selectedBasesPreserveSerialHistoryWithoutBlockingUnrelatedWork() {
		List<String> expected = List.of( "error []:FAILED", "errorChild []:FAILED",
				"errorDependent []:ABORTED", "failure []:FAILED", "failureChild []:ABORTED",
				"failureDependent []:FAILED", "success []:SUCCESSFUL", "successChild []:SUCCESSFUL",
				"successDependent []:SUCCESSFUL" );
		for( int target : List.of( 1, 2, 5, 12 ) ) {
			Evidence e = execute( "oracle", target == 12 ? "true" : "false", target );
			assertEquals( expected, e.results.stream().sorted().toList(), e.failures::toString );
			assertEquals( 9, e.starts.size() );
			assertEquals( 7, e.events.stream().filter( s -> s.startsWith( "body:" ) ).count() );
			assertDoesNotThrow( e.handle::close );
		}
	}

	/** A synchronous assertion timeout retains the unexpected-assertion oracle. */
	@Test
	void assertionTimeoutPermitsDependentSutEntryButSuppressesDerivedFlows() {
		List<String> expected = List.of( "error []:FAILED", "errorChild []:FAILED",
				"errorDependent []:ABORTED", "failure []:FAILED", "failureChild []:ABORTED",
				"failureDependent []:FAILED", "success []:SUCCESSFUL", "successChild []:SUCCESSFUL",
				"successDependent []:SUCCESSFUL" );
		Evidence serial = execute( "oracle-timeout", "false", 12 );
		Evidence parallel = execute( "oracle-timeout", "true", 12 );
		for( Evidence e : List.of( serial, parallel ) ) {
			assertEquals( expected, e.results.stream().sorted().toList(), e.failures::toString );
			assertEquals( 9, e.starts.size() );
			assertEquals( 6, e.failures.size(), e.failures::toString );
			assertEquals( List.of( "body:error", "body:errorChild", "body:failure",
					"body:failureDependent", "body:success", "body:successChild", "body:successDependent" ),
					e.events.stream().filter( s -> s.startsWith( "body:" ) ).sorted().toList() );
			assertTrue( e.events.contains( "timeout:completed" ),
					"the timed work completed on the original invocation thread" );
			assertNotNull( e.timeoutAssertion, "JUnit itself must have raised the timeout assertion" );
			assertNotNull( e.timeoutResult );
			assertSame( e.timeoutAssertion, e.timeoutResult.getThrowable().orElseThrow(),
					"native failure must retain the original timeout assertion, not a replacement" );
			assertTrue(
					e.events.indexOf( "timeout:completed" ) < e.events.indexOf( "body:failureDependent" ) );
			assertTrue( e.events.indexOf( "finish:failure []" ) < e.events.indexOf( "finish:success []" ),
					"independent work must progress after the native timeout failure" );
			assertDoesNotThrow( e.handle::close );
		}
		assertEquals( serial.results.stream().sorted().toList(),
				parallel.results.stream().sorted().toList() );
	}

	/**
	 * Explicit History policy still controls eligibility, not the readiness edge.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "basis", "dependency", "stateless" })
	void suppressionAndStatelessnessPreserveTheSerialOracle( String policy ) {
		List<String> expected = List.of( "error []:FAILED", "errorChild []:FAILED",
				"errorDependent []:" + (policy.equals( "basis" ) ? "ABORTED" : "FAILED"),
				"failure []:FAILED", "failureChild []:" + (policy.equals( "basis" ) ? "FAILED" : "ABORTED"),
				"failureDependent []:FAILED", "success []:SUCCESSFUL", "successChild []:SUCCESSFUL",
				"successDependent []:SUCCESSFUL" );
		try( var basis = AssertionOptions.SUPPRESS_BASIS_CHECK
				.temporarily( "" + policy.equals( "basis" ) );
				var dependency = AssertionOptions.SUPPRESS_DEPENDENCY_CHECK
						.temporarily( "" + policy.equals( "dependency" ) ) ) {
			for( String mode : List.of( "false", "true" ) ) {
				Evidence e = execute( "oracle-" + policy, mode, 12 );
				assertEquals( expected, e.results.stream().sorted().toList(), e.failures::toString );
				assertEquals( 9, e.starts.size() );
				assertEquals( 8, e.events.stream().filter( s -> s.startsWith( "body:" ) ).count() );
				assertDoesNotThrow( e.handle::close );
			}
		}
	}

	/** A fatal processing Error is neither an ordinary failure nor a success. */
	@Test
	void fatalProcessingErrorStopsDependentAdmissionAndPreservesItsCause() {
		Evidence e = execute( "fatal", "true", 12 );
		assertTrue( e.results.contains( "A []:FAILED" ), e.results::toString );
		assertFalse( e.events.contains( "body:B" ) );
		assertTrue( e.failures.stream().anyMatch( t -> t instanceof LinkageError
				&& t.getMessage().equals( "fatal SUT linkage" ) ), e.failures::toString );
		assertThrows( IllegalStateException.class, e.handle::close );
	}

	/**
	 * Missing early provenance rejects parameter resolution before factory entry.
	 */
	@Test
	void missingEarlyHookRejectsBeforeTheOriginalFactory() {
		Evidence e = execute( "nohook", "true", 3 );
		assertNull( e.factory );
		assertTrue( e.failures.stream().anyMatch( t -> t.toString().contains( "receiver" ) ),
				e.failures::toString );
		assertTrue( e.events.isEmpty() );
	}

	/**
	 * Exceptional backstop closure prevents dependent body entry and reports
	 * incompleteness.
	 */
	@Test
	void exceptionalStopClosesBodyEntryWithoutInventingCompletion() {
		Evidence e = execute( "stop", "true", 3 );
		assertFalse( e.events.contains( "body:A" ) );
		assertFalse( e.events.contains( "body:B" ) );
		assertTrue( e.failures.stream().anyMatch( t -> t.toString().contains( "Incomplete" ) ),
				e.failures::toString );
	}

	/**
	 * Native predecessor completion publishes binding and releases B without
	 * waiting for C.
	 */
	@Test
	void bindingAndSuccessorFinishWhileIndependentCRemainsActive() {
		Evidence e = execute( "overlap", "true", 4 );
		assertEquals( List.of(), e.failures );
		assertEquals( List.of( "A []:SUCCESSFUL", "B []:SUCCESSFUL", "C []:SUCCESSFUL" ), e.results );
		assertTrue( e.events.indexOf( "finish:A []" ) < e.events.indexOf( "body:B" ) );
		assertTrue( e.events.indexOf( "finish:B []" ) < e.events.indexOf( "end:C" ) );
		assertEquals( "published", e.bound );
		assertEquals( 3, e.durations.size() );
		assertTrue( e.durations.values().stream().allMatch( n -> n > 0 ) );
		assertEquals( 3, e.restored.get() );
		assertEquals( 3, e.sources.get() );
		assertDoesNotThrow( e.handle::close, "normal native completion detached the class backstop" );
	}

	/**
	 * The supplied larger pool preserves binding, independent progress and context
	 * restoration.
	 */
	@Test
	void suppliedTwelveTargetTwentyCapPreservesRealFlowOwnership() {
		Evidence evidence = execute( "overlap", "true", 12 );
		assertEquals( List.of(), evidence.failures );
		assertEquals( 3, evidence.results.size() );
		assertEquals( "published", evidence.bound );
		assertTrue( evidence.events.indexOf( "finish:B []" ) < evidence.events.indexOf( "end:C" ) );
		assertEquals( 3, evidence.restored.get() );
	}

	/**
	 * A processor error leaves the real dependent aborted before its SUT callback.
	 */
	@Test
	void processingErrorAbortsRealDependentWithoutCallingItsSut() {
		Evidence e = execute( "error", "true", 3 );
		assertTrue( e.results.contains( "A []:FAILED" ), e.results::toString );
		assertTrue( e.results.contains( "B []:ABORTED" ), e.results::toString );
		assertFalse( e.events.contains( "body:B" ) );
		assertTrue(
				e.failures.stream().anyMatch( f -> f.getMessage().contains( "Missing dependency" ) ) );
	}

	/**
	 * A post-processing interceptor failure does not invalidate successful
	 * dependency publication.
	 */
	@Test
	void externalNativeFailureDoesNotRewriteSuccessfulHistory() {
		Evidence e = execute( "external", "true", 3 );
		assertTrue( e.results.contains( "A []:FAILED" ), e.results::toString );
		assertTrue( e.results.contains( "B []:SUCCESSFUL" ), e.results::toString );
		assertEquals( "published", e.bound );
	}

	/** Comparison failure still publishes the actual response to the dependent. */
	@Test
	void ordinaryComparisonFailureIsNotHistoryError() {
		Evidence e = execute( "comparison", "true", 3 );
		assertTrue( e.results.contains( "A []:FAILED" ), e.results::toString );
		assertTrue( e.results.contains( "B []:SUCCESSFUL" ), e.results::toString );
		assertEquals( "unexpected", e.bound );
	}

	/**
	 * Unsupported model surfaces and transformed descriptions fail before any SUT
	 * callback.
	 */
	@Test
	void allPreparationMustBeAuditedBeforeAnySutUse() {
		for( String mode : List.of( "capture",
				"transform" ) ) {
			Evidence e = execute( mode, "true", 3 );
			assertFalse( e.failures.isEmpty(), mode );
			assertEquals( 0, e.starts.size(), mode );
			assertFalse( e.events.stream().anyMatch( s -> s.startsWith( "body:" ) ), mode );
		}
	}

	/**
	 * Browser-opening modes are completion-owned too: the report is published once
	 * after native completion and only then presented. Opening itself is suppressed
	 * here by the standard property, so the observable is the single publication.
	 *
	 * @param dir Isolated report artifact directory
	 */
	@Test
	void parallelOpeningModesPublishOnceAtCompletion( @TempDir Path dir ) {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "prepared-open" );
				Temporary suppressed = LocalBrowse.SUPPRESS.temporarily( "true" ) ) {
			Evidence e = execute( "report-open", "true", 3 );
			assertEquals( List.of(), e.failures );
			assertEquals( 3, e.results.size() );
			assertEquals( 3, e.visibleReportDirectories.get() );
			assertEquals( 0, e.visibleReportIndexes.get(), "no index while bodies run" );
			assertEquals( 3, new Reader( dir.resolve( "prepared-open" ) ).read().entries.size() );
		}
	}

	/**
	 * A normal prepared run publishes one readable report after native completion,
	 * never while bodies are still running, in either execution mode.
	 *
	 * @param dir Isolated report artifact directory
	 */
	@Test
	void preparedQuietReportingPublishesAtCompletion( @TempDir Path dir ) {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "prepared-final" ) ) {
			for( String mode : List.of( "false", "true" ) ) {
				for( int run = 0; run < 3; run++ ) {
					Evidence e = execute( "report", mode, 3 );
					assertEquals( List.of(), e.failures );
					assertEquals( 3, e.results.size() );
					assertEquals( 3, e.visibleReportDirectories.get() );
					assertEquals( 0, e.visibleReportIndexes.get() );
					assertEquals( 3,
							new Reader( dir.resolve( "prepared-final" ) ).read().entries.size() );
					String diagnostic = assertDoesNotThrow( () -> Files.readString(
							dir.resolve( "prepared-final" ).resolve( Writer.DIAGNOSTICS_FILE_NAME ) ) );
					assertTrue( diagnostic.contains( "Execution: completed and drained" ), diagnostic );
					assertTrue( diagnostic.contains( "Final index: pending atomic publication" ),
							diagnostic );
				}
			}
		}
	}

	/**
	 * An enabled empty parallel run publishes a readable report with no fake leaf.
	 */
	@Test
	void parallelEmptyRunPublishesEmptyReport( @TempDir Path dir ) {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "parallel-empty" ) ) {
			Evidence e = execute( "report-empty", "true", 3 );
			assertEquals( List.of(), e.failures );
			assertEquals( List.of(), e.results );
			assertEquals( 0, new Reader( dir.resolve( "parallel-empty" ) ).read().entries.size() );
			assertTrue( Files.exists(
					dir.resolve( "parallel-empty" ).resolve( Writer.DIAGNOSTICS_FILE_NAME ) ) );
		}
	}

	/**
	 * Report creation faults are diagnosed once without changing passing results.
	 */
	@Test
	void parallelReportFailureIsVisibleAndNonFatal( @TempDir Path dir ) throws Exception {
		Path blocked = Files.writeString( dir.resolve( "not-a-directory" ), "blocked" );
		ByteArrayOutputStream diagnostic = new ByteArrayOutputStream();
		PrintStream original = System.err;
		Evidence e;
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( blocked.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "parallel-failed" );
				PrintStream captured = new PrintStream( diagnostic, true, UTF_8 ) ) {
			System.setErr( captured );
			try {
				e = execute( "report-fault", "true", 3 );
			}
			finally {
				System.setErr( original );
			}
		}
		assertEquals( List.of(), e.failures );
		assertEquals( 3, e.results.size() );
		assertEquals( 3, e.events.stream().filter( event -> event.startsWith( "body:" ) ).count() );
		assertEquals( "published", e.bound );
		assertEquals( 1, diagnostic.toString( UTF_8 ).lines()
				.filter( line -> line.startsWith( "Flow report failed: " ) ).count(),
				diagnostic::toString );
		assertFalse( Files.exists( blocked.resolve( "parallel-failed" ) ) );
	}

	/**
	 * A report fault cannot replace or suppress genuine native failure outcomes.
	 */
	@Test
	void parallelReportFailurePreservesMixedOutcomes( @TempDir Path dir ) throws Exception {
		Path blocked = Files.writeString( dir.resolve( "not-a-directory" ), "blocked" );
		ByteArrayOutputStream diagnostic = new ByteArrayOutputStream();
		PrintStream original = System.err;
		Evidence e;
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( blocked.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "parallel-mixed-failed" );
				PrintStream captured = new PrintStream( diagnostic, true, UTF_8 ) ) {
			System.setErr( captured );
			try {
				e = execute( "oracle-report-fault", "true", 12 );
			}
			finally {
				System.setErr( original );
			}
		}
		assertEquals( 6, e.failures.size(), e.failures::toString );
		assertEquals( 9, e.results.size() );
		assertEquals( 7, e.events.stream().filter( event -> event.startsWith( "body:" ) ).count() );
		assertEquals( 1, diagnostic.toString( UTF_8 ).lines()
				.filter( line -> line.startsWith( "Flow report failed: " ) ).count(),
				diagnostic::toString );
	}

	/** Final companion/index faults remain non-fatal and are never advertised. */
	@ParameterizedTest
	@ValueSource(strings = { "report-diagnostic-fault", "report-close-fault" })
	void parallelFinalPublicationFailureIsVisibleAndNonFatal( String scenario,
			@TempDir Path dir ) {
		ByteArrayOutputStream diagnostic = new ByteArrayOutputStream();
		PrintStream original = System.err;
		Evidence e;
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "parallel-close-failed" );
				PrintStream captured = new PrintStream( diagnostic, true, UTF_8 ) ) {
			System.setErr( captured );
			try {
				e = execute( scenario, "true", 3 );
			}
			finally {
				System.setErr( original );
			}
		}
		assertEquals( List.of(), e.failures );
		assertEquals( 3, e.results.size() );
		assertEquals( 1, diagnostic.toString( UTF_8 ).lines()
				.filter( line -> line.startsWith( "Flow report failed: " ) ).count(),
				diagnostic::toString );
		String blocked = scenario.equals( "report-diagnostic-fault" )
				? Writer.DIAGNOSTICS_FILE_NAME
				: Writer.INDEX_FILE_NAME;
		assertTrue( Files.isDirectory( dir.resolve( "parallel-close-failed" ).resolve( blocked ) ) );
	}

	/**
	 * Genuine mixed outcomes remain native failures and are all present in output.
	 */
	@Test
	void parallelMixedOutcomesPublishTheirRealReportTags( @TempDir Path dir ) {
		try( Temporary artifact = AssertionOptions.ARTIFACT_DIR.temporarily( dir.toString() );
				Temporary name = AssertionOptions.REPORT_NAME.temporarily( "parallel-mixed" ) ) {
			Evidence e = execute( "oracle-report", "true", 12 );
			assertEquals( 6, e.failures.size(), e.failures::toString );
			List<Entry> entries = new Reader( dir.resolve( "parallel-mixed" ) ).read().entries;
			assertEquals( 9, entries.size() );
			assertEquals( 3, entries.stream().filter( entry -> entry.tags.contains( Writer.PASS_TAG ) )
					.count() );
			assertEquals( 2, entries.stream().filter( entry -> entry.tags.contains( Writer.FAIL_TAG ) )
					.count() );
			assertEquals( 2, entries.stream().filter( entry -> entry.tags.contains( Writer.ERROR_TAG ) )
					.count() );
			assertEquals( 2, entries.stream().filter( entry -> entry.tags.contains( Writer.SKIP_TAG ) )
					.count() );
		}
	}

	/** Comparable source evidence from a substituted native leaf remains fatal. */
	@Test
	void substitutedNativeSourceIsRejectedBeforeSutUse() {
		Evidence e = execute( "source-transform", "true", 3 );
		assertFalse( e.failures.isEmpty() );
		assertFalse( e.events.stream().anyMatch( event -> event.startsWith( "body:" ) ) );
	}

	/**
	 * Audited fan-in and identical messages retain every real invocation.
	 *
	 * @param scenario Fan-in or shared-message model
	 */
	@ParameterizedTest
	@ValueSource(strings = { "fanin", "alias", "chain" })
	void auditedPublicationPreservesEveryNativeInvocation( String scenario ) {
		Evidence e = execute( scenario, "true", 12 );
		assertEquals( List.of(), e.failures );
		assertEquals( 3, e.starts.size() );
		assertEquals( 3, e.results.size() );
		assertEquals( "published", e.bound );
		assertDoesNotThrow( e.handle::close );
	}

	/**
	 * Self bindings publish the request before the response comparison, without a
	 * self-wait.
	 */
	@Test
	void intraFlowPublicationIsSynchronousOnTheNativeCaller() {
		for( String mode : List.of( "false", "true" ) ) {
			Evidence e = execute( "intraflow", mode, 12 );
			assertEquals( List.of(), e.failures );
			assertEquals( List.of( "A []:SUCCESSFUL" ), e.results );
			assertEquals( 1, e.starts.size() );
			assertEquals( 1, e.events.stream().filter( s -> s.equals( "mutate:request" ) ).count() );
			assertEquals( "published", e.bound );
			assertDoesNotThrow( e.handle::close );
		}
	}

	/** Missing declarations serialize through global exclusivity, not rejection. */
	@Test
	void unknownAndPartialAuditsStillExecuteEverySelectedFlow() {
		for( String mode : List.of( "unknown", "partial" ) ) {
			Evidence e = execute( mode, "true", 3 );
			assertEquals( List.of(), e.failures, mode );
			assertEquals( 3, e.results.size(), mode );
			assertEquals( 3, e.starts.size(), mode );
			assertEquals( "published", e.bound );
		}
	}

	/**
	 * Default and explicit serial modes preserve binding on one thread without the
	 * early hook.
	 */
	@Test
	void sameModelAndCallerRemainProviderFreeForAbsentAndFalse() {
		for( String mode : List.of( "absent", "false" ) ) {
			Evidence e = execute( "serial", mode, 2 );
			assertEquals( List.of(), e.failures );
			assertEquals( "published", e.bound );
			assertEquals( 1, e.threads.size() );
		}
	}

	/**
	 * A saturated two-worker pool makes inline progress without leaking invocation
	 * context.
	 */
	@Test
	void busyTargetTwoMakesRealInlineProgressAndRestoresContextOnReuse() {
		for( int repeat = 0; repeat < 20; repeat++ )
			for( String scenario : List.of( "busy", "busy-chain" ) ) {
				Evidence e = execute( scenario, "true", 2 );
				assertEquals( List.of(), e.failures );
				assertEquals( 80, e.results.size() );
				assertTrue( e.inline.get() > 0,
						"real native body on the still-enumerating factory worker" );
				assertEquals( 80, e.restored.get() );
				assertEquals( 80, e.durations.size() );
				assertTrue( e.durations.values().stream().allMatch( n -> n > 0 ) );
				assertEquals( 2, e.threads.size() );
				if( scenario.equals( "busy-chain" ) )
					for( int i = 0; i < 80; i += 2 )
						assertTrue( e.events.indexOf( String.format( "finish:%03d [chain:pair-%d]", i,
								i / 2 ) ) < e.events.indexOf( String.format( "body:%03d", i + 1 ) ) );
			}
	}

	/**
	 * Whole-chain ownership does not make a one-worker readiness waiter supported.
	 */
	@Test
	void nativeParallelOneStillRejectsChainsBeforeFactoryOrSut() {
		Evidence e = execute( "chain", "true", 1 );
		assertNull( e.factory );
		assertEquals( 0, e.starts.size() );
		assertFalse( e.failures.isEmpty() );
	}

	/**
	 * Runs the shared fixture through a real Launcher with controlled native
	 * settings.
	 *
	 * @param scenario The fixture behavior to exercise
	 * @param parallel The Flow mode, or {@code absent} to omit its setting
	 * @param target   The fixed native parallelism
	 * @return Observations collected during the completed Launcher call
	 */
	static Evidence execute( String scenario, String parallel, int target ) {
		Evidence e = new Evidence( scenario, "true".equals( parallel ) );
		ParallelBindingFixture.evidence = e;
		String hook = "junit.platform.launcher.interceptors.enabled";
		String previous = System.getProperty( hook );
		System.setProperty( hook, Boolean.toString( e.parallel && !scenario.equals( "nohook" )
				&& !scenario.startsWith( "duplicate" ) ) );
		try {
			var request = LauncherDiscoveryRequestBuilder.request()
					.selectors( selectClass( ParallelBindingFixture.class ) )
					.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
					.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism",
							"" + target )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
							"" + (target == 12 ? 20 : target) );
			if( !"absent".equals( parallel ) ) {
				request.configurationParameter( "flow.parallel", parallel );
			}
			var launcher = LauncherFactory.create( LauncherConfig.builder()
					.enableTestExecutionListenerAutoRegistration( false )
					.enableLauncherSessionListenerAutoRegistration( false ).build() );
			var actual = request.build();
			if( scenario.startsWith( "duplicate" ) ) {
				// Exercise the existing call-local receiver seam with real native IDs,
				// not a fake executable or a private Jupiter descriptor.
				try( FlowNativeCall call = new FlowNativeCall( actual ) ) {
					launcher.execute( actual, call, e, new TestExecutionListener() {
						@Override
						public void dynamicTestRegistered( TestIdentifier id ) {
							call.dynamicTestRegistered( id );
						}

						@Override
						public void executionStarted( TestIdentifier id ) {
							if( id.isTest() )
								call.executionStarted( id );
						}

						@Override
						public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
							if( id.isTest() )
								call.executionFinished( id, scenario.equals( "duplicate" ) ? result
										: TestExecutionResult
												.failed( new IllegalStateException( "conflicting evidence" ) ) );
						}
					} );
				}
			}
			else {
				launcher.execute( actual, e );
			}
		}
		finally {
			while( e.release.getCount() != 0 )
				e.release.countDown();
			if( previous == null )
				System.clearProperty( hook );
			else
				System.setProperty( hook, previous );
		}
		return e;
	}

	/**
	 * Cross-worker observations and bounded coordination for one fixture execution.
	 */
	static class Evidence implements TestExecutionListener {
		private final CountDownLatch laterAncestorFinished = new CountDownLatch( 1 );
		/** Behavior selected by the enclosing test. */
		final String scenario;
		/** Whether guarded native parallel execution is requested. */
		final boolean parallel;
		/** Signals entry of C, or the deliberately blocking busy-case body. */
		final CountDownLatch cEntered = new CountDownLatch( 1 );
		/** Releases the deliberately held independent body. */
		final CountDownLatch release;
		/** Ordered body and native-terminal observations. */
		final List<String> events = new CopyOnWriteArrayList<>();
		/** Native leaf display names paired with terminal statuses. */
		final List<String> results = new CopyOnWriteArrayList<>();
		/** Failures reported by native execution callbacks. */
		final List<Throwable> failures = new CopyOnWriteArrayList<>();
		/** Monotonic start times keyed by native leaf ID. */
		final java.util.Map<String, Long> starts = new ConcurrentHashMap<>();
		/** Measured native leaf durations in nanoseconds. */
		final java.util.Map<String, Long> durations = new ConcurrentHashMap<>();
		/** Workers that actually enter SUT callbacks. */
		final Set<Thread> threads = ConcurrentHashMap.newKeySet();
		/** Invocations whose worker context was verified restored. */
		final AtomicInteger restored = new AtomicInteger();
		/** Bodies executed inline on the factory worker in the busy scenario. */
		final AtomicInteger inline = new AtomicInteger();
		/** Report indexes visible before the normal native completion boundary. */
		final AtomicInteger visibleReportIndexes = new AtomicInteger();
		/** Run-owned report directories visible before native bodies. */
		final AtomicInteger visibleReportDirectories = new AtomicInteger();
		/** Completed leaves retaining class-source navigation metadata. */
		final AtomicInteger sources = new AtomicInteger();
		/** Thread that entered the original factory. */
		Thread factory;
		/** Actual injected owner retained for post-run lifecycle assertions. */
		FlowExecution handle;
		/** Request value observed after dependency publication into B. */
		volatile String bound;
		/**
		 * Original JUnit timeout assertion, captured and rethrown by the SUT callback.
		 */
		volatile AssertionFailedError timeoutAssertion;
		/** Actual native result for the flow that raised the timeout assertion. */
		volatile TestExecutionResult timeoutResult;

		/**
		 * Initializes per-execution scenario controls.
		 *
		 * @param scenario The behavior selected by the test
		 * @param parallel Whether parallel ownership is expected
		 */
		Evidence( String scenario, boolean parallel ) {
			this.scenario = scenario;
			this.parallel = parallel;
			release = new CountDownLatch( scenario.equals( "fork" ) ? 2 : 1 );
		}

		@Override
		public void dynamicTestRegistered( TestIdentifier id ) {
			events.add( "register:" + id.getDisplayName() );
		}

		@Override
		public void executionStarted( TestIdentifier id ) {
			if( id.isTest() )
				starts.put( id.getUniqueId(), System.nanoTime() );
		}

		@Override
		public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
			result.getThrowable().ifPresent( failures::add );
			if( id.isTest() ) {
				durations.put( id.getUniqueId(), System.nanoTime() - starts.get( id.getUniqueId() ) );
				results.add( id.getDisplayName() + ":" + result.getStatus() );
				events.add( "finish:" + id.getDisplayName() );
				if( scenario.equals( "oracle-timeout" ) && id.getDisplayName().equals( "failure []" ) ) {
					timeoutResult = result;
					release.countDown();
				}
				if( id.getDisplayName().equals( "Z []" ) )
					laterAncestorFinished.countDown();
				if( id.getSource()
						.filter( org.junit.platform.engine.support.descriptor.ClassSource.class::isInstance )
						.isPresent() )
					sources.incrementAndGet();
				if( id.getDisplayName().equals( "B []" )
						|| scenario.equals( "fork" ) && id.getDisplayName().equals( "D []" ) )
					release.countDown();
			}
		}
	}

	/**
	 * Verifies worker-local context restoration around original native invocations.
	 */
	public static class InvocationContext implements InvocationInterceptor {
		/** Native leaf identity visible only during the fixture invocation. */
		static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

		@Override
		public void interceptDynamicTest( Invocation<Void> invocation,
				DynamicTestInvocationContext dynamic,
				ExtensionContext context ) throws Throwable {
			Evidence e = ParallelBindingFixture.evidence;
			String previous = TOKEN.get();
			assertNull( previous, "worker context leaked from a previous invocation" );
			assertNull( FlowExtension.invocationContext(), "library context leaked" );
			TOKEN.set( context.getUniqueId() );
			try {
				if( e.scenario.equals( "stop" ) && context.getDisplayName().equals( "A []" ) ) {
					e.handle.close();
				}
				try {
					if( e.parallel && e.scenario.equals( "basis-inverted" )
							&& context.getDisplayName().equals( "A []" ) ) {
						// Give an incorrectly admitted Z a bounded opportunity to publish
						// UNEXPECTED before A's actual Flow preconditions, not after its body.
						e.laterAncestorFinished.await( 1, TimeUnit.SECONDS );
					}
					invocation.proceed();
				}
				catch( IllegalArgumentException error ) {
					if( !e.scenario.equals( "swallow-error" ) )
						throw error;
				}
				if( e.scenario.equals( "external" ) && context.getDisplayName().equals( "A []" ) ) {
					throw new IllegalStateException( "external native failure after real processing" );
				}
			}
			finally {
				TOKEN.remove();
				assertNull( FlowExtension.invocationContext(), "library context must be restored" );
				e.restored.incrementAndGet();
			}
		}
	}
}

/**
 * Real dependency-binding fixture with independent work and explicit negative
 * controls.
 */
@ExtendWith(FlowParallelBindingTest.InvocationContext.class)
@FlowTest
class ParallelBindingFixture {
	/** Minimal mutable fixture message; parsing/binding uses the real publisher. */
	static class Text extends Msg {
		private String value;

		/**
		 * Creates an independently mutable message.
		 *
		 * @param value The complete text payload
		 */
		Text( String value ) {
			super( value );
			this.value = value;
		}

		@Override
		public Message child() {
			return new Text( value );
		}

		@Override
		public Message peer( byte[] bytes ) {
			return new Text( new String( bytes, UTF_8 ) );
		}

		@Override
		public byte[] content() {
			return value.getBytes( UTF_8 );
		}

		@Override
		public String assertable( Unpredictable... masks ) {
			return value;
		}

		@Override
		public Set<String> fields() {
			return Set.of( ".+" );
		}

		@Override
		public Object get( String field ) {
			return value;
		}

		@Override
		public Message set( String field, Object replacement ) {
			value = (String) replacement;
			return this;
		}
	}

	/** Controls and observations supplied before each Launcher execution. */
	static FlowParallelBindingTest.Evidence evidence;

	/**
	 * Prepares real mutable messages, dependencies and scenario-specific body
	 * behavior.
	 *
	 * @param execution The actual factory-local owner injected by Flow
	 * @return Owned native descriptions, or a deliberate transformed negative
	 *         control
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		var e = evidence;
		e.factory = Thread.currentThread();
		e.handle = execution;
		Flow a = create( "A", "published" );
		Flow c = e.scenario.equals( "alias" )
				? Creator.build( f -> f.meta( m -> m.description( "C" ) )
						.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
								.request( new Text( "shared" ) {
									@Override
									public Message child() {
										return a.root().request();
									}
								} ).response( new Text( "response" ) ) ) )
				: create( "C", "response" );
		if( e.scenario.equals( "alias" ) )
			assertSame( a.root().request(), c.root().request(), "builder must retain the alias control" );
		Flow b = Creator.build( f -> {
			f.meta( m -> m.description( "B" ) );
			if( e.scenario.equals( "chain" ) )
				f.meta( m -> m.tags( tags -> tags.add( "chain:unchecked" ) ) );
			f.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
					.request( new Text( "pending" ) ).response( new Text( "response" ) ) )
					.dependency( a, d -> d.from( i -> true, RESPONSE, ".+" )
							.to( i -> true, REQUEST, ".+" ) );
			if( e.scenario.equals( "fanin" ) )
				f.prerequisite( c );
		} );
		List<Flow> flows = e.scenario.startsWith( "busy" )
				? IntStream.range( 0, 80 ).mapToObj( n -> Creator.build( f -> {
					f.meta( m -> m.description( String.format( "%03d", n ) ).tags( tags -> {
						if( e.scenario.equals( "busy-chain" ) )
							tags.add( "chain:pair-" + n / 2 );
					} ) ).call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Text( "request" ) )
							.response( new Text( "response" ) ) );
				} ) )
						.toList()
				: e.scenario.startsWith( "oracle" ) ? new Mdl().flows().toList() : List.of( a, b, c );
		if( e.scenario.startsWith( "basis-" ) ) {
			Flow basis = e.scenario.equals( "basis-inverted" ) ? create( "Z", "published" ) : a;
			Flow absent = Deriver.build( basis, f -> f.meta( m -> m.description( "absent" ) ) );
			Flow derived = Deriver.build( absent, f -> f.meta( m -> m.description(
					e.scenario.equals( "basis-inverted" ) ? "A" : "D" ) ) );
			flows = e.scenario.equals( "basis-only" ) ? List.of( derived ) : List.of( basis, derived );
		}
		if( e.scenario.startsWith( "duplicate" ) ) {
			flows = List.of( a );
		}
		if( e.scenario.equals( "intraflow" ) ) {
			flows = List.of( Creator.build( f -> f.meta( m -> m.description( "A" ) )
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
							.request( new Text( "request" ) ).response( new Text( "response" ) ) )
					.dependency( null, d -> d.from( i -> true, REQUEST, ".+" ).mutate( value -> {
						assertTrue( e.threads.contains( Thread.currentThread() ), "original SUT caller" );
						e.events.add( "mutate:request" );
						assertEquals( "request", value );
						e.bound = "published";
						return e.bound;
					} ).to( i -> true, RESPONSE, ".+" ) ) ) );
		}
		if( e.scenario.equals( "report-empty" ) ) {
			flows = List.of();
		}
		if( e.scenario.equals( "fork" ) ) {
			Flow d = Creator.build( f -> f.meta( m -> m.description( "D" ) ).prerequisite( a )
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
							.request( new Text( "request" ) ).response( new Text( "response" ) ) ) );
			flows = List.of( a, b, c, d );
		}
		if( e.scenario.equals( "oracle-chain" ) ) {
			flows = new java.util.ArrayList<>();
			for( String name : List.of( "error", "failure", "success" ) ) {
				Flow root = Creator.build( f -> f.meta( m -> m.description( name )
						.tags( tags -> tags.add( "chain:" + name ) ) )
						.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Text( "request" ) )
								.response( new Text( "response" ) ) ) );
				flows.add( root );
				flows.add( Deriver.build( root, f -> f.meta( m -> m.description( name + "Child" )
						.tags( tags -> tags.add( "chain:" + name ) ) ) ) );
				flows.add( Creator.build( f -> f.meta( m -> m.description( name + "Dependent" )
						.tags( tags -> tags.add( "chain:" + name ) ) ).prerequisite( root )
						.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( new Text( "request" ) )
								.response( new Text( "response" ) ) ) ) );
			}
		}
		List<Flow> selected = flows;
		PreparedFlocessor runner = execution.flocessor( "real binding", new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return selected.stream();
			}
		} ).system( e.scenario.equals( "oracle-stateless" ) ? State.LESS : State.FUL, Actrs.BEN );
		if( !e.scenario.equals( "unknown" ) ) {
			runner.independent( "owned synchronous resources and callbacks; no affinity",
					f -> !e.scenario.equals( "partial" ) || f == a );
		}
		if( e.scenario.equals( "busy-chain" ) )
			runner.isolatedChains( "whole pairs own distinct state and callbacks",
					IntStream.range( 0, 40 ).mapToObj( i -> "pair-" + i ).toArray( String[]::new ) );
		if( e.scenario.equals( "oracle-chain" ) )
			runner.isolatedChains( "three independent scenarios", "error", "failure", "success" );
		if( e.scenario.equals( "report-open" ) )
			runner.reporting( Reporting.ALWAYS );
		else if( e.scenario.contains( "report" ) )
			runner.reporting( Reporting.QUIETLY );
		if( e.scenario.equals( "capture" ) )
			runner.logs( new LogCapture() {
				@Override
				public void start( Flow flow ) {
					fail( "capture must not start" );
				}

				@Override
				public Stream<com.mastercard.test.flow.report.data.LogEvent> end( Flow flow ) {
					return Stream.empty();
				}
			} );
		runner.behaviour( assertion -> {
			String name = assertion.flow().meta().description();
			String token = FlowParallelBindingTest.InvocationContext.TOKEN.get();
			assertNotNull( token );
			if( e.parallel )
				assertEquals( token, FlowExtension.invocationContext().getUniqueId() );
			e.threads.add( Thread.currentThread() );
			e.events.add( "body:" + name );
			if( (e.scenario.equals( "report-diagnostic-fault" )
					|| e.scenario.equals( "report-close-fault" )) && name.equals( "A" ) ) {
				String blocked = e.scenario.equals( "report-diagnostic-fault" )
						? Writer.DIAGNOSTICS_FILE_NAME
						: Writer.INDEX_FILE_NAME;
				assertDoesNotThrow( () -> Files.createDirectory( Path
						.of( AssertionOptions.ARTIFACT_DIR.value(), AssertionOptions.REPORT_NAME.value() )
						.resolve( blocked ) ) );
			}
			if( e.scenario.equals( "intraflow" ) ) {
				assertion.actual().request( "request".getBytes( UTF_8 ) )
						.response( "published".getBytes( UTF_8 ) );
				return;
			}
			if( e.scenario.startsWith( "oracle" ) ) {
				if( e.scenario.equals( "oracle-timeout" ) && name.equals( "failure" ) ) {
					Thread caller = Thread.currentThread();
					try {
						assertTimeout( Duration.ofMillis( 1 ), () -> {
							// Exceed even a coarse millisecond clock's budget without a speed threshold.
							// No preemption, separate body thread, or sleep is involved.
							long start = System.nanoTime();
							while( System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos( 50 ) )
								Thread.onSpinWait();
							assertSame( caller, Thread.currentThread() );
							e.events.add( "timeout:completed" );
						} );
					}
					catch( AssertionFailedError timeout ) {
						e.timeoutAssertion = timeout;
						throw timeout;
					}
				}
				if( e.scenario.equals( "oracle-timeout" ) && name.equals( "success" ) )
					await( e.release );
				if( name.startsWith( "error" ) )
					throw new IllegalArgumentException( "no thanks!" );
				assertion.actual().response( name.startsWith( "failure" )
						? "unexpected content!".getBytes( UTF_8 )
						: assertion.expected().response().content() );
				return;
			}
			if( e.scenario.startsWith( "busy" ) ) {
				if( name.equals( "000" ) ) {
					e.cEntered.countDown();
					await( e.release );
				}
				else if( Thread.currentThread() == e.factory ) {
					e.inline.incrementAndGet();
					e.release.countDown();
				}
			}
			if( e.scenario.equals( "overlap" ) || e.scenario.equals( "fork" ) ) {
				if( name.equals( "C" ) ) {
					e.cEntered.countDown();
					await( e.release );
					e.events.add( "end:C" );
				}
				if( name.equals( "A" ) )
					await( e.cEntered );
			}
			if( name.equals( "A" )
					&& (e.scenario.equals( "error" ) || e.scenario.equals( "swallow-error" )) )
				throw new IllegalArgumentException( "SUT error" );
			if( name.equals( "A" ) && e.scenario.equals( "timeout" ) )
				throw new java.io.UncheckedIOException(
						new java.net.SocketTimeoutException( "SUT timed out" ) );
			if( name.equals( "A" ) && e.scenario.equals( "fatal" ) )
				throw new LinkageError( "fatal SUT linkage" );
			if( name.equals( "B" ) ) {
				e.bound = assertion.expected().request().assertable();
				assertEquals( e.scenario.equals( "comparison" ) ? "unexpected" : "published", e.bound );
			}
			if( e.scenario.startsWith( "report" ) && Files.exists( Path
					.of( AssertionOptions.ARTIFACT_DIR.value(), AssertionOptions.REPORT_NAME.value() ) ) ) {
				Path report = Path.of( AssertionOptions.ARTIFACT_DIR.value(),
						AssertionOptions.REPORT_NAME.value() );
				e.visibleReportDirectories.incrementAndGet();
				if( Files.exists( report.resolve( Writer.INDEX_FILE_NAME ) ) )
					e.visibleReportIndexes.incrementAndGet();
			}
			assertion.actual()
					.response( name.equals( "Z" ) && e.scenario.equals( "basis-inverted" )
							|| name.equals( "A" )
									&& (e.scenario.equals( "comparison" ) || e.scenario.equals( "basis-gap" ))
											? "unexpected".getBytes( UTF_8 )
											: assertion.expected().response().content() );
		} );
		Stream<DynamicNode> tests = runner.tests();
		assertThrows( IllegalStateException.class, () -> runner.independent( "late", f -> true ) );
		if( e.scenario.equals( "transform" ) )
			return tests.skip( 1 );
		if( e.scenario.equals( "source-transform" ) )
			return tests.map( node -> {
				DynamicTest test = (DynamicTest) node;
				return DynamicTest.dynamicTest( test.getDisplayName(),
						URI.create( "class:java.lang.String" ), test.getExecutable() );
			} );
		if( e.scenario.startsWith( "busy" ) )
			return tests.onClose( e.release::countDown );
		return tests;
	}

	/**
	 * Builds a flow with separately owned request and response messages.
	 *
	 * @param name     The flow description
	 * @param response The expected response payload
	 * @return The independent fixture flow
	 */
	static Flow create( String name, String response ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Text( "request" ) ).response( new Text( response ) ) ) );
	}

	/**
	 * Bounds fixture coordination and preserves interruption when it fails.
	 *
	 * @param latch The body-entry or release signal to await
	 */
	static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 5, TimeUnit.SECONDS ), "fixture coordination timed out" );
		}
		catch( InterruptedException ex ) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException( ex );
		}
	}
}
