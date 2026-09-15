package consumer;

import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Reporting;
import com.mastercard.test.flow.assrt.junit5.FlowExecution;
import com.mastercard.test.flow.assrt.junit5.FlowTest;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.model.EagerModel;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Standalone consumer: only published public APIs and native Launcher evidence.
 */
class PackagedConsumerTest {
	private static final String HOOK = "junit.platform.launcher.interceptors.enabled";

	/**
	 * Checks consumer-selected runtime versions and installed artifact provenance,
	 * including the packaged service entry rather than a reactor test substitute.
	 *
	 * @throws Exception If an artifact cannot be read or hashed
	 */
	@Test
	void artifactsAndConsumerSelectedVersions() throws Exception {
		assertEquals( 17, Runtime.version().feature() );
		for( Class<?> type : List.of( FlowExecution.class, Creator.class, EagerModel.class,
				Text.class ) ) {
			Path path = jar( type );
			assertTrue( path.getFileName().toString().endsWith(
					"-" + System.getProperty( "consumer.flow" ) + ".jar" ), path::toString );
		}
		TestEngine jupiter = ServiceLoader.load( TestEngine.class ).stream()
				.map( ServiceLoader.Provider::get )
				.filter( engine -> engine.getId().equals( "junit-jupiter" ) )
				.findFirst().orElseThrow();
		for( Class<?> type : List.of( Test.class, jupiter.getClass() ) ) {
			assertTrue( jar( type ).toString()
					.endsWith( "-" + System.getProperty( "consumer.junit" ) + ".jar" ) );
		}
		for( Class<?> type : List.of( Launcher.class, TestExecutionResult.class,
				org.junit.platform.commons.JUnitException.class ) ) {
			assertTrue( jar( type ).toString()
					.endsWith( "-" + System.getProperty( "consumer.platform" ) + ".jar" ) );
		}
		try( JarFile artifact = new JarFile( jar( FlowExecution.class ).toFile() ) ) {
			var service = artifact
					.getJarEntry( "META-INF/services/org.junit.platform.launcher.LauncherInterceptor" );
			assertNotNull( service );
			try( var input = artifact.getInputStream( service ) ) {
				assertEquals( "com.mastercard.test.flow.assrt.junit5.FlowLauncherInterceptor",
						new String( input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8 ).trim() );
			}
			assertNotNull(
					artifact.getJarEntry( "com/mastercard/test/flow/assrt/junit5/FlowLauncherSix.class" ) );
		}
		for( String entry : System.getProperty( "surefire.test.class.path" )
				.split( java.io.File.pathSeparator ) ) {
			if( entry.replace( '\\', '/' ).contains( "/com/mastercard/test/flow/" ) ) {
				Path path = Path.of( entry );
				assertTrue( path.toString().endsWith( ".jar" ), path::toString );
				provenance( path );
				provenance(
						path.resolveSibling( path.getFileName().toString().replace( ".jar", ".pom" ) ) );
			}
		}
		Path group = Path
				.of( FlowExecution.class.getProtectionDomain().getCodeSource().getLocation().toURI() )
				.getParent().getParent().getParent();
		for( String artifact : List.of( "bom", "parent", "assert", "message", "report" ) ) {
			String version = System.getProperty( "consumer.flow" );
			provenance(
					group.resolve( artifact + "/" + version + "/" + artifact + "-" + version + ".pom" ) );
		}
	}

	private static void provenance( Path path ) throws Exception {
		System.out.println( "FLOW-FILE " + path + " SHA256=" + HexFormat.of().formatHex(
				MessageDigest.getInstance( "SHA-256" ).digest( Files.readAllBytes( path ) ) ) );
	}

	private static Path jar( Class<?> type ) throws Exception {
		Path path = Path.of( type.getProtectionDomain().getCodeSource().getLocation().toURI() );
		assertTrue( Files.isRegularFile( path ) && path.toString().endsWith( ".jar" ),
				() -> type + " must resolve from a produced JAR: " + path );
		System.out.println( "ARTIFACT " + type.getName() + " " + path + " SHA256="
				+ HexFormat.of().formatHex(
						MessageDigest.getInstance( "SHA-256" ).digest( Files.readAllBytes( path ) ) ) );
		return path;
	}

	/**
	 * Exercises real binding and native outcomes across modes, then checks repeated
	 * execution, idempotent close and re-entry guards, not heap reachability.
	 */
	@Test
	void realCallerOutcomesAndCleanup() {
		boolean parallel = Boolean.getBoolean( "consumer.parallel" );
		for( String mode : parallel ? List.of( "true" )
				: List.of( "absent", "false", "global-false" ) ) {
			// Same-JVM repetition is a smoke check, not proof of registration disposal.
			for( int repeat = 0; repeat < (parallel ? 34 : 1); repeat++ ) {
				Evidence evidence = execute( mode, "normal" );
				completed( evidence, 3, 0, 0 );
				assertEquals( Map.of( "A []", "SUCCESSFUL", "B []", "SUCCESSFUL", "C []", "SUCCESSFUL" ),
						evidence.outcomes );
				assertEquals( "published", evidence.bound );
				assertTrue(
						evidence.events.indexOf( "factory-return" ) < evidence.events.indexOf( "body:A" ) );
				assertTrue( evidence.events.indexOf( "finish:B []" ) < evidence.events
						.indexOf( "factory-terminal" ) );
				if( parallel ) {
					assertTrue(
							evidence.events.indexOf( "finish:A []" ) < evidence.events.indexOf( "body:B" ) );
					assertTrue(
							evidence.events.indexOf( "finish:B []" ) < evidence.events.indexOf( "end:C" ) );
					assertTrue(
							evidence.events.indexOf( "stream-close" ) < evidence.events.indexOf( "end:C" ) );
				}
				else {
					List<String> nativeEvents = evidence.events.stream()
							.filter( s -> s.startsWith( "start:" ) || s.startsWith( "finish:" ) ).toList();
					assertEquals( List.of( "start:A []", "finish:A []", "start:B []", "finish:B []",
							"start:C []", "finish:C []" ), nativeEvents );
				}
			}
			Evidence error = execute( mode, "error" );
			completed( error, 1, 1, 1 );
			assertEquals( Map.of( "A []", "FAILED", "B []", "ABORTED", "C []", "SUCCESSFUL" ),
					error.outcomes );
			assertFalse( error.events.contains( "body:B" ) );
			assertTrue( error.summary.getSummary().getFailures().stream()
					.anyMatch( f -> f.getException().toString().contains( "controlled SUT error" ) ) );
			completed( execute( mode, "empty" ), 0, 0, 0 );
		}
		if( parallel ) {
			Evidence disabled = execute( "true", "no-provider" );
			assertNull( disabled.factory );
			assertEquals( 0, disabled.summary.getSummary().getTestsStartedCount() );
			assertTrue( disabled.summary.getSummary().getFailures().stream()
					.anyMatch( f -> f.getException().toString().contains( "receiver" ) ) );
			Evidence selector = execute( "true", "method-selector" );
			assertInstanceOf( IllegalArgumentException.class, selector.rejected );
			assertTrue( selector.rejected.getMessage().contains( "sole full-class selector" ) );
			assertNull( selector.factory );
			assertTrue( selector.events.isEmpty() );
		}
	}

	private static void completed( Evidence evidence, int success, int failed, int aborted ) {
		var summary = evidence.summary.getSummary();
		int count = success + failed + aborted;
		assertNull( evidence.rejected );
		assertEquals( count, summary.getTestsFoundCount() );
		assertEquals( count, summary.getTestsStartedCount() );
		assertEquals( success, summary.getTestsSucceededCount() );
		assertEquals( failed, summary.getTestsFailedCount() );
		assertEquals( aborted, summary.getTestsAbortedCount() );
		assertEquals( 0, summary.getContainersFailedCount() );
		assertEquals( 0, summary.getContainersAbortedCount() );
		assertEquals( List.of(), evidence.diagnostics );
		assertEquals( count, evidence.contexts.get() );
		assertEquals( count, evidence.nativeIds.size() );
		assertEquals( count, evidence.durations.size() );
		assertTrue( evidence.durations.values().stream().allMatch( n -> n > 0 ) );
		assertTrue( evidence.events.contains( "stream-close" ) );
		assertTrue( evidence.events.contains( "factory-terminal" ) );
		if( count == 0 )
			assertFalse( evidence.events.stream().anyMatch( s -> s.startsWith( "body:" ) ) );
		assertDoesNotThrow( evidence.handle::close );
		assertDoesNotThrow( evidence.handle::close );
		assertThrows( IllegalStateException.class,
				() -> evidence.handle.flocessor( "reuse", new BindingModel( false ) ) );
		for( DynamicNode node : evidence.descriptions )
			assertThrows( IllegalStateException.class, ((DynamicTest) node).getExecutable() );
	}

	private static Evidence execute( String mode, String scenario ) {
		Evidence evidence = new Evidence( mode.equals( "true" ), scenario );
		BindingFactory.evidence = evidence;
		String previous = System.getProperty( HOOK );
		String previousMode = System.getProperty( "flow.parallel" );
		System.clearProperty( HOOK );
		System.clearProperty( "flow.parallel" );
		if( evidence.parallel && !scenario.equals( "no-provider" ) )
			System.setProperty( HOOK, "true" );
		if( mode.equals( "global-false" ) )
			System.setProperty( "flow.parallel", "false" );
		try {
			if( !evidence.parallel )
				assertNull( System.getProperty( HOOK ) );
			var request = LauncherDiscoveryRequestBuilder.request().selectors(
					scenario.equals( "method-selector" )
							? selectMethod( BindingFactory.class, "flows", FlowExecution.class )
							: selectClass( BindingFactory.class ) )
					.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
					.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism",
							"12" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
							"20" );
			if( !mode.equals( "absent" ) && !mode.equals( "global-false" ) )
				request.configurationParameter( "flow.parallel", mode );
			LauncherFactory.create( LauncherConfig.builder()
					.enableTestExecutionListenerAutoRegistration( false )
					.enableLauncherSessionListenerAutoRegistration( false ).build() )
					.execute( request.build(), evidence.summary, evidence );
		}
		catch( IllegalArgumentException rejected ) {
			evidence.rejected = rejected;
		}
		finally {
			evidence.cEntered.countDown();
			evidence.bFinished.countDown();
			evidence.streamClosed.countDown();
			if( previous == null )
				System.clearProperty( HOOK );
			else
				System.setProperty( HOOK, previous );
			if( previousMode == null )
				System.clearProperty( "flow.parallel" );
			else
				System.setProperty( "flow.parallel", previousMode );
		}
		System.out
				.println( "NATIVE mode=" + mode + " scenario=" + scenario + " outcomes=" + evidence.outcomes
						+ " nativeIds=" + evidence.nativeIds + " events=" + evidence.events
						+ " rejected=" + evidence.rejected );
		// A rejected selector never reaches testPlanExecutionStarted; JUnit 6 throws
		// from getSummary() there, whereas 1.10 returns null.
		if( evidence.rejected == null ) {
			evidence.summary.getSummary().printTo( new PrintWriter( System.out, true ) );
			evidence.summary.getSummary().printFailuresTo( new PrintWriter( System.out, true ) );
		}
		return evidence;
	}

	/** Observations shared by one synchronous Launcher call and its real bodies. */
	static class Evidence implements TestExecutionListener {
		/** Expected native mode, independent of the observed invocation context. */
		final boolean parallel;
		/** Selects normal binding, controlled failure, empty or rejected execution. */
		final String scenario;
		private final SummaryGeneratingListener summary = new SummaryGeneratingListener();
		/** Orders body, stream and native events across invocation threads. */
		final List<String> events = new CopyOnWriteArrayList<>();
		private final List<String> diagnostics = new CopyOnWriteArrayList<>();
		private final Map<String, Long> starts = new ConcurrentHashMap<>();
		private final Map<String, Long> durations = new ConcurrentHashMap<>();
		private final Map<String, String> outcomes = new ConcurrentHashMap<>();
		/**
		 * Display names join listener evidence to flow IDs only in this test oracle.
		 */
		final Map<String, String> nativeIds = new ConcurrentHashMap<>();
		private final AtomicInteger contexts = new AtomicInteger();
		/** Holds A until independent C has entered its body. */
		final CountDownLatch cEntered = new CountDownLatch( 1 );
		/** Keeps C active until B's native finish, not just its body return. */
		final CountDownLatch bFinished = new CountDownLatch( 1 );
		/** Keeps C active past description-stream closure. */
		final CountDownLatch streamClosed = new CountDownLatch( 1 );
		/** Retained executable descriptions for the post-run re-entry check. */
		List<DynamicNode> descriptions;
		/** Factory-owned handle retained for idempotent close and reuse rejection. */
		FlowExecution handle;
		/** Establishes the serial body/listener thread identity oracle. */
		Thread factory;
		/** B's actual request value after dependency publication. */
		volatile String bound;
		private Throwable rejected;

		private Evidence( boolean parallel, String scenario ) {
			this.parallel = parallel;
			this.scenario = scenario;
		}

		/**
		 * Records the native leaf identity before its Flow callback can run.
		 *
		 * @param id The identifier supplied by the executing Launcher
		 */
		@Override
		public void executionStarted( TestIdentifier id ) {
			if( id.isTest() ) {
				starts.put( id.getUniqueId(), System.nanoTime() );
				if( nativeIds.putIfAbsent( id.getDisplayName(), id.getUniqueId() ) != null )
					diagnostics.add( "Duplicate native display name: " + id );
				events.add( "start:" + id.getDisplayName() );
				if( !parallel && Thread.currentThread() != factory )
					diagnostics.add( "Not native SAME_THREAD: " + id );
			}
		}

		/**
		 * Checks native duration/source evidence and releases C only after B finishes.
		 * Listener diagnostics are asserted outside the Launcher, which can swallow
		 * listener exceptions.
		 *
		 * @param id     The finished native leaf or container
		 * @param result Its actual native outcome
		 */
		@Override
		public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
			if( id.isTest() ) {
				durations.put( id.getUniqueId(), System.nanoTime() - starts.get( id.getUniqueId() ) );
				events.add( "finish:" + id.getDisplayName() );
				outcomes.put( id.getDisplayName(), result.getStatus().name() );
				if( id.getDisplayName().equals( "B []" ) )
					bFinished.countDown();
				if( !(id.getSource().orElse( null )instanceof ClassSource source)
						|| !source.getClassName().equals( BindingModel.class.getName() )
						|| source.getPosition().isEmpty() || source.getPosition().get().getLine() <= 0 )
					diagnostics.add( "Missing navigation source: " + id );
			}
			else if( id.getDisplayName().startsWith( "flows(" ) )
				events.add( "factory-terminal" );
		}
	}

	/** Checks the actual native execution context without substituting a body. */
	public static class ContextCheck implements InvocationInterceptor {
		/** Native invocation UID exposed only to the synchronous Flow callback. */
		static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

		/**
		 * Preserves the original invocation on its Jupiter thread while exposing its
		 * context identity for comparison with independent listener evidence.
		 *
		 * @param invocation The original native executable
		 * @param dynamic    The native dynamic-test description
		 * @param context    The actual leaf invocation context
		 * @throws Throwable If the invocation or its context checks fail
		 */
		@Override
		public void interceptDynamicTest( Invocation<Void> invocation,
				DynamicTestInvocationContext dynamic,
				ExtensionContext context ) throws Throwable {
			var evidence = BindingFactory.evidence;
			assertNull( CURRENT.get() );
			assertEquals( evidence.parallel ? ExecutionMode.CONCURRENT : ExecutionMode.SAME_THREAD,
					context.getExecutionMode() );
			CURRENT.set( context.getUniqueId() );
			try {
				invocation.proceed();
			}
			finally {
				CURRENT.remove();
				evidence.contexts.incrementAndGet();
			}
		}
	}

	/**
	 * Bounds a controlled overlap wait without treating elapsed time as progress.
	 *
	 * @param latch The native/body event that must occur before continuing
	 */
	static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 10, TimeUnit.SECONDS ), "controlled overlap timed out" );
		}
		catch( InterruptedException interrupted ) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException( interrupted );
		}
	}
}

/**
 * The same composed registration and late-model handle is used for every run.
 */
@FlowTest
@ExtendWith(PackagedConsumerTest.ContextCheck.class)
class BindingFactory {
	/**
	 * Installed before each Launcher call; parallel bodies share that call's state.
	 */
	static PackagedConsumerTest.Evidence evidence;

	/**
	 * Builds descriptions without executing the SUT, retaining the real handle and
	 * executable nodes so completion guards can be checked after Launcher return.
	 *
	 * @param execution The late-model handle supplied to this native factory
	 * @return Owned descriptions whose stream close does not signal body drainage
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		var e = evidence;
		e.handle = execution;
		e.factory = Thread.currentThread();
		Stream<DynamicNode> tests = execution
				.flocessor( "packaged binding", new BindingModel( e.scenario.equals( "empty" ) ) )
				.system( State.FUL, BindingModel.Actors.SYSTEM )
				.reporting( Reporting.NEVER )
				.independent( "fixture-owned synchronous messages; no shared SUT resources or affinity",
						f -> true )
				.behaviour( assertion -> {
					String name = assertion.flow().meta().description();
					e.events.add( "body:" + name );
					String nativeId = e.nativeIds.get( assertion.flow().meta().id() );
					assertNotNull( nativeId, "Missing native executionStarted for " + name );
					assertEquals( nativeId, PackagedConsumerTest.ContextCheck.CURRENT.get(),
							"Native UID for " + name );
					if( !e.parallel )
						assertSame( e.factory, Thread.currentThread() );
					if( e.parallel && e.scenario.equals( "normal" ) ) {
						if( name.equals( "A" ) )
							PackagedConsumerTest.await( e.cEntered );
						if( name.equals( "C" ) ) {
							e.cEntered.countDown();
							PackagedConsumerTest.await( e.bFinished );
							PackagedConsumerTest.await( e.streamClosed );
							e.events.add( "end:C" );
						}
					}
					if( name.equals( "A" ) && e.scenario.equals( "error" ) )
						throw new IllegalArgumentException( "controlled SUT error" );
					if( name.equals( "B" ) ) {
						e.bound = assertion.expected().request().assertable();
						assertEquals( "published", e.bound );
					}
					assertion.actual().response( assertion.expected().response().content() );
				} ).tests();
		e.descriptions = tests.toList();
		assertFalse( e.events.stream().anyMatch( s -> s.startsWith( "body:" ) ) );
		e.events.add( "factory-return" );
		return e.descriptions.stream().onClose( () -> {
			e.events.add( "stream-close" );
			e.streamClosed.countDown();
		} );
	}
}

/** Real builder/model/text artifacts, not copies of reactor test helpers. */
class BindingModel extends EagerModel {
	/**
	 * The client supplies requests; only the system is exercised by the fixture.
	 */
	enum Actors implements Actor {
		/** Source of modeled requests. */
		CLIENT,
		/** Sole actor exercised by the synchronous callback. */
		SYSTEM
	}

	/**
	 * Creates A-to-B message binding and independent C using the published builder.
	 *
	 * @param empty Whether to omit all members for the empty-factory control
	 */
	BindingModel( boolean empty ) {
		super( "packaged model", new TaggedGroup() );
		Flow a = flow( "A", "published" );
		Flow b = Creator.build( f -> f.meta( m -> m.description( "B" ) )
				.call( i -> i.from( Actors.CLIENT ).to( Actors.SYSTEM )
						.request( new Text( "pending" ) ).response( new Text( "response" ) ) )
				.dependency( a, d -> d.from( i -> true, RESPONSE, ".+" ).to( i -> true, REQUEST, ".+" ) ) );
		members( empty ? List.of() : List.of( a, b, flow( "C", "response" ) ) );
	}

	private static Flow flow( String name, String response ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( Actors.CLIENT ).to( Actors.SYSTEM )
						.request( new Text( "request" ) ).response( new Text( response ) ) ) );
	}
}
