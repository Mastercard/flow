package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.lang.reflect.Method;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfigurationStrategy;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;

/** Real Launcher protection tests, not parallel caller/gate acceptance. */
@SuppressWarnings("static-method")
class FlowNativeProfileTest {
	/**
	 * Newer provider-only and child-target resource annotations cannot evade the
	 * guard.
	 *
	 * @param output Temporary destination for fixtures compiled against the current
	 *               runtime
	 * @throws Exception If fixture compilation, loading or file access fails
	 */
	@Test
	void rejectsOptionalProviderOnlyAndChildrenResourceMetadata(
			@org.junit.jupiter.api.io.TempDir java.nio.file.Path output ) throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(
				java.util.Arrays.stream( ResourceLock.class.getMethods() )
						.anyMatch( m -> "providers".equals( m.getName() ) ),
				"Resource providers are absent on baseline 5.10" );
		String name = FlowNativeProfileTest.class.getPackageName() + ".FlowProfileProviderGenerated";
		String source = "package " + FlowNativeProfileTest.class.getPackageName() + ";"
				+ "import org.junit.jupiter.api.parallel.*;"
				+ "@ResourceLock(providers=FlowProfileProviderGenerated.Empty.class) "
				+ "class FlowProfileProviderGenerated extends FlowProfileFixture {"
				+ "public static class Empty implements ResourceLocksProvider {} }"
				+ "@ResourceLock(value=\"children\", mode=ResourceAccessMode.READ, target=ResourceLockTarget.CHILDREN) "
				+ "class FlowProfileChildrenGenerated extends FlowProfileFixture {}";
		javax.tools.SimpleJavaFileObject input = new javax.tools.SimpleJavaFileObject(
				java.net.URI.create( "string:///" + name.replace( '.', '/' ) + ".java" ),
				javax.tools.JavaFileObject.Kind.SOURCE ) {
			@Override
			public CharSequence getCharContent( boolean ignoreEncodingErrors ) {
				return source;
			}
		};
		javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		try( javax.tools.StandardJavaFileManager manager = compiler.getStandardFileManager( null, null,
				null ) ) {
			assertTrue( compiler.getTask( null, manager, null,
					java.util.List.of( "--release", "17", "-classpath",
							System.getProperty( "java.class.path" ),
							"-d", output.toString() ),
					null, java.util.List.of( input ) ).call() );
		}
		java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
		java.nio.file.Path directory = output
				.resolve( FlowNativeProfileTest.class.getPackageName().replace( '.', '/' ) );
		lookup.defineClass( java.nio.file.Files
				.readAllBytes( directory.resolve( "FlowProfileProviderGenerated$Empty.class" ) ) );
		for( String fixture : new String[] { "FlowProfileProviderGenerated",
				"FlowProfileChildrenGenerated" } ) {
			Class<?> type = lookup.defineClass(
					java.nio.file.Files.readAllBytes( directory.resolve( fixture + ".class" ) ) );
			rejected( execute( type, Map.of() ), "resource" );
		}
	}

	/** Matching pool dimensions do not authorize a custom native strategy. */
	@Test
	void rejectsCustomStrategyEvenWhenItBuildsTheSameSizedPool() {
		rejected( execute( FlowProfileFixture.class, Map.of(
				"junit.jupiter.execution.parallel.config.strategy", "custom",
				"junit.jupiter.execution.parallel.config.custom.class", FixedLikeCustom.class.getName() ) ),
				"fixed" );
	}

	/** A captured native profile is bound to its original execution context. */
	@Test
	void aPreviousExecutionCannotReuseItsProfile() {
		Events first = execute( FlowProfileFixture.class, Map.of() );
		assertEquals( java.util.List.of(), first.failures );
		FlowProfileFixture.previous = first.profile;
		try {
			rejected( execute( FlowProfileFixture.class, Map.of() ), "context" );
		}
		finally {
			FlowProfileFixture.previous = null;
		}
	}

	/**
	 * Invalid native pool maxima fail construction before factory or body entry.
	 */
	@Test
	void rejectsInvalidFixedMaximumAtNativeConstructionWithoutFlowUse() {
		for( String max : new String[] { "1", "32768", "invalid" } ) {
			Events events = execute( FlowProfileFixture.class, Map.of(
					"junit.jupiter.execution.parallel.config.fixed.max-pool-size", max ) );
			assertTrue( !events.failures.isEmpty() );
			assertEquals( 0, events.factories.get() );
			assertEquals( 0, events.bodies.get() );
		}
	}

	/** Standard default maxima and either saturation setting remain supported. */
	@Test
	void acceptsStandardOmittedMaximumAndBothSaturationSettings() {
		for( String saturation : new String[] { "true", "false" } ) {
			Events events = execute( FlowProfileFixture.class, Map.of(
					"profile.omit-maximum", "true",
					"junit.jupiter.execution.parallel.config.fixed.saturate", saturation ) );
			assertEquals( java.util.List.of(), events.failures );
			assertEquals( 1, events.bodies.get() );
		}
	}

	/**
	 * Public SPI control: the profile must not infer support from equal targets.
	 */
	public static final class FixedLikeCustom implements ParallelExecutionConfigurationStrategy {
		@Override
		public ParallelExecutionConfiguration
				createConfiguration( ConfigurationParameters parameters ) {
			return new ParallelExecutionConfiguration() {
				@Override
				public int getParallelism() {
					return 2;
				}

				@Override
				public int getMinimumRunnable() {
					return 2;
				}

				@Override
				public int getMaxPoolSize() {
					return 2;
				}

				@Override
				public int getCorePoolSize() {
					return 2;
				}

				@Override
				public int getKeepAliveSeconds() {
					return 30;
				}
			};
		}
	}

	/**
	 * Native resource restrictions apply across inheritance, composition and empty
	 * containers.
	 */
	@Test
	void rejectsInheritedMetaRepeatedEmptyAndMethodResourceMetadata() {
		for( Class<?> type : new Class<?>[] { FlowProfileReadFixture.class,
				FlowProfileInheritedReadFixture.class,
				FlowProfileMetaFixture.class, FlowProfileRepeatedFixture.class,
				FlowProfileEmptyLocksFixture.class,
				FlowProfileIsolatedFixture.class, FlowProfileMethodLockFixture.class,
				FlowProfileInterfaceFixture.class } ) {
			rejected( execute( type, Map.of() ), "resource" );
		}
	}

	/**
	 * Conflicting native execution modes and orderers are rejected before Flow use.
	 */
	@Test
	void rejectsConflictingExecutionAndOrdererMetadata() {
		for( Class<?> type : new Class<?>[] { FlowProfileSameFixture.class,
				FlowProfileMethodSameFixture.class,
				FlowProfileOrderFixture.class, FlowProfileMetaModeFixture.class } ) {
			rejected( execute( type, Map.of() ), "mode/orderer" );
		}
	}

	/**
	 * Separate-thread timeout metadata and defaults cannot move Flow off its native
	 * owner.
	 */
	@Test
	void rejectsSeparateThreadFactoryClassAndLifecycleTimeouts() {
		for( Class<?> type : new Class<?>[] { FlowProfileTimeoutFixture.class,
				FlowProfileClassTimeoutFixture.class,
				FlowProfileLifecycleTimeoutFixture.class } ) {
			Events events = execute( type, Map.of() );
			assertTrue( !events.failures.isEmpty(), type::getName );
			assertTrue( events.guards.get() > 0 );
			assertEquals( 0, events.factories.get() );
			assertEquals( 0, events.bodies.get() );
		}
		rejected( execute( FlowProfileFixture.class, Map.of(
				"junit.jupiter.execution.timeout.thread.mode.default", "separate_thread",
				"junit.jupiter.execution.timeout.lifecycle.method.default", "5 s" ) ), "timeout" );
	}

	/**
	 * Per-class lifecycle and same-thread timeouts do not themselves require
	 * physical affinity.
	 */
	@Test
	void acceptsPerClassAndSameThreadTimeoutsWithoutInventedAffinityRequirements() {
		Events events = execute( FlowProfileHarmlessFixture.class, Map.of() );
		assertEquals( java.util.List.of(), events.failures );
		assertEquals( 1, events.bodies.get() );
	}

	/**
	 * Nested factories and mixed test classes violate the sole top-level factory
	 * scope.
	 */
	@Test
	void rejectsNestedAndMixedFactoryScopeBeforeFlowUse() {
		for( Class<?> type : new Class<?>[] { FlowProfileNestedFixture.class,
				FlowProfileMixedFixture.class } ) {
			Events events = execute( type, Map.of() );
			assertTrue(
					events.failures.stream().anyMatch( t -> t.toString().contains( "sole top-level" ) ),
					events.failures::toString );
			assertEquals( 0, events.factories.get() );
			assertEquals( 0, events.bodies.get() );
		}
	}

	/**
	 * Both supported fixed-pool configurations execute a real flow under the
	 * checked profile.
	 */
	@Test
	void acceptsFixedTwoAndSuppliedTwelveTwenty() {
		for( int target : new int[] { 2, 12 } ) {
			Events events = execute( FlowProfileFixture.class, Map.of(
					"junit.jupiter.execution.parallel.config.fixed.parallelism", "" + target,
					"junit.jupiter.execution.parallel.config.fixed.max-pool-size",
					target == 2 ? "2" : "20" ) );
			assertEquals( java.util.List.of(), events.failures );
			assertEquals( 1, events.factories.get() );
			assertEquals( 1, events.bodies.get() );
			assertEquals( 1, events.passed.get() );
		}
	}

	/** Single-worker native execution is rejected before the original factory. */
	@Test
	void rejectsOneWorkerBeforeOriginalFactory() {
		rejected( execute( FlowProfileFixture.class, Map.of(
				"junit.jupiter.execution.parallel.config.fixed.parallelism", "1" ) ), "parallelism" );
	}

	/**
	 * Profile entry and rechecks reject off-pool, foreign-pool and custom-pool
	 * execution.
	 */
	@Test
	void rejectsOffPoolEntryAndForeignPoolAtBothRechecks() {
		for( String probe : new String[] { "off-pool", "foreign-check", "foreign-admission",
				"custom-pool" } ) {
			rejected( execute( FlowProfileFixture.class, Map.of( "profile.probe", probe ) ), "pool" );
		}
	}

	/**
	 * Busy native workers remain valid when real bodies make inline progress on the
	 * factory worker.
	 */
	@Test
	void permitsBusyWorkersQueuedWorkAndActualInlineFlowBodies() {
		Events events = execute( FlowProfileFixture.class, Map.of( "profile.probe", "busy" ) );
		assertEquals( java.util.List.of(), events.failures );
		assertEquals( 80, events.bodies.get() );
		assertEquals( 80, events.passed.get() );
		assertTrue( events.inline.get() > 0,
				"Native bodies must actually run inline on the factory worker" );
		assertTrue( events.busyStarted.getCount() == 0 );
	}

	private static void rejected( Events events, String message ) {
		assertTrue( events.failures.stream().anyMatch( t -> t.toString().contains( message ) ),
				events.failures::toString );
		assertTrue( events.guards.get() > 0, "The profile guard must actually be entered" );
		assertEquals( 0, events.factories.get() );
		assertEquals( 0, events.bodies.get() );
	}

	/**
	 * Dynamic pool configuration is rejected before the original factory or SUT
	 * callback.
	 */
	@Test
	void rejectsDynamicBeforeOriginalFactoryAndFlowUse() {
		Events events = execute( FlowProfileFixture.class,
				Map.of( "junit.jupiter.execution.parallel.config.strategy", "dynamic" ) );
		assertTrue( events.failures.stream().anyMatch( t -> t.getMessage().contains( "fixed" ) ),
				events.failures::toString );
		assertEquals( 0, events.factories.get() );
		assertEquals( 0, events.bodies.get() );
	}

	private static Events execute( Class<?> type, Map<String, String> configuration ) {
		Events events = new Events();
		FlowProfileFixture.events = events;
		events.busy = "busy".equals( configuration.get( "profile.probe" ) );
		var builder = LauncherDiscoveryRequestBuilder.request().selectors( selectClass( type ) )
				.configurationParameter( "flow.parallel", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism", "2" )
				.configurationParameters( configuration );
		if( !configuration.containsKey( "profile.omit-maximum" ) ) {
			builder.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
					configuration.getOrDefault( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
							"2" ) );
		}
		var request = builder.build();
		LauncherFactory.create( LauncherConfig.builder()
				.enableLauncherSessionListenerAutoRegistration( false )
				.enableTestExecutionListenerAutoRegistration( false ).build() )
				.execute( request, new TestExecutionListener() {
					@Override
					public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
						result.getThrowable().ifPresent( events.failures::add );
						if( id.isTest() && result.getStatus() == TestExecutionResult.Status.SUCCESSFUL ) {
							events.passed.incrementAndGet();
						}
					}
				} );
		return events;
	}

	/** Observations asserted only after Launcher returns. */
	static final class Events {
		/** Failures from native execution or bounded busy-worker coordination. */
		final CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();
		/** Original factory entries. */
		final AtomicInteger factories = new AtomicInteger();
		/** Real SUT callback entries. */
		final AtomicInteger bodies = new AtomicInteger();
		/** Successful native leaf terminals. */
		final AtomicInteger passed = new AtomicInteger();
		/** Profile guard entries before the original factory. */
		final AtomicInteger guards = new AtomicInteger();
		/** SUT callbacks executing on the still-enumerating factory worker. */
		final AtomicInteger inline = new AtomicInteger();
		/** Signals that a native worker is held by the busy probe. */
		final CountDownLatch busyStarted = new CountDownLatch( 1 );
		/** Releases the native worker held by the busy probe. */
		final CountDownLatch releaseBusy = new CountDownLatch( 1 );
		/** Whether this execution deliberately occupies a native worker. */
		boolean busy;
		/** Actual original-factory invocation thread. */
		Thread factoryThread;
		/** Checked profile captured for this execution's rechecks. */
		FlowNativeProfile profile;
	}

	/** Test-only wiring of the profile before the original factory. */
	static final class ProbeGuard implements InvocationInterceptor {
		@Override
		public <T> T interceptTestFactoryMethod( Invocation<T> invocation,
				ReflectiveInvocationContext<Method> method, ExtensionContext context ) throws Throwable {
			Events events = FlowProfileFixture.events;
			events.guards.incrementAndGet();
			if( FlowProfileFixture.previous != null ) {
				FlowProfileFixture.previous.check( context );
			}
			events.factoryThread = Thread.currentThread();
			String probe = context.getConfigurationParameter( "profile.probe" ).orElse( "" );
			if( "off-pool".equals( probe ) ) {
				FutureTask<
						FlowNativeProfile> task = new FutureTask<>( () -> FlowNativeProfile.enter( context ) );
				Thread thread = new Thread( task, "profile-off-pool-probe" );
				thread.start();
				try {
					task.get( 5, TimeUnit.SECONDS );
				}
				catch( java.util.concurrent.ExecutionException failure ) {
					throw failure.getCause();
				}
				finally {
					thread.join( 5000 );
				}
			}
			events.profile = FlowNativeProfile.enter( context );
			if( probe.startsWith( "foreign-" ) || "custom-pool".equals( probe ) ) {
				ForkJoinPool other = "custom-pool".equals( probe ) ? new ForkJoinPool( 2 ) {
					// A nonstandard pool must not masquerade as the native profile.
				} : new ForkJoinPool( 2 );
				try {
					other.submit( () -> {
						if( "foreign-check".equals( probe ) ) {
							events.profile.check( context );
						}
						else if( "foreign-admission".equals( probe ) ) {
							events.profile.checkAdmission();
						}
						else {
							FlowNativeProfile.enter( context );
						}
					} ).get( 5, TimeUnit.SECONDS );
				}
				catch( java.util.concurrent.ExecutionException failure ) {
					throw failure.getCause();
				}
				finally {
					other.shutdownNow();
					other.awaitTermination( 5, TimeUnit.SECONDS );
				}
			}
			events.profile.check( context );
			if( events.busy ) {
				ForkJoinTask.getPool().execute( () -> {
					events.busyStarted.countDown();
					try {
						if( !events.releaseBusy.await( 10, TimeUnit.SECONDS ) ) {
							events.failures.add( new IllegalStateException( "busy probe timed out" ) );
						}
					}
					catch( InterruptedException failure ) {
						Thread.currentThread().interrupt();
						events.failures.add( failure );
					}
				} );
				if( !events.busyStarted.await( 5, TimeUnit.SECONDS ) ) {
					throw new IllegalStateException( "busy probe did not start" );
				}
			}
			return invocation.proceed();
		}
	}
}

/**
 * Baseline sole-factory fixture that exercises real Flow bodies under a
 * captured profile.
 */
@TestMethodOrder(FlowMethodOrderer.class)
@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(FlowNativeProfileTest.ProbeGuard.class)
class FlowProfileFixture {
	/** Controls and observations for the current Launcher execution. */
	static FlowNativeProfileTest.Events events;
	/**
	 * Optional prior-run profile used to prove cross-execution reuse is rejected.
	 */
	static FlowNativeProfile previous;

	/**
	 * Prepares real legacy Flow bodies and releases busy-worker coordination on
	 * close.
	 *
	 * @return Native descriptions for one body, or eighty in the busy-worker probe
	 */
	@TestFactory
	Stream<DynamicNode> flows() {
		events.factories.incrementAndGet();
		events.profile.checkAdmission();
		return IntStream.range( 0, events.busy ? 80 : 1 ).boxed()
				.flatMap( n -> oneFlow() ).onClose( events.releaseBusy::countDown );
	}

	private static Stream<DynamicNode> oneFlow() {
		// One real legacy flow: native profile evidence, not a parallel scheduler.
		return new Flocessor( "native profile", new Mdl() )
				.system( State.FUL, Actrs.BEN )
				.exercising( f -> "success".equals( f.meta().description() ), message -> {
					// Expected selection diagnostic.
				} )
				.behaviour( a -> {
					events.profile.checkAdmission();
					if( Thread.currentThread() == events.factoryThread ) {
						events.inline.incrementAndGet();
					}
					events.bodies.incrementAndGet();
					a.actual().response( a.expected().response().content() );
				} ).tests();
	}
}

/** Negative control for a native class-level READ resource lock. */
@ResourceLock(value = "profile-read", mode = ResourceAccessMode.READ)
class FlowProfileReadFixture extends FlowProfileFixture {
	// Native READ locks must not evade the guard.
}

/** Negative control inheriting a native resource lock from its superclass. */
class FlowProfileInheritedReadFixture extends FlowProfileReadFixture {
	// Inherited resource lock.
}

/** Composes an inherited READ lock for class and method metadata probes. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Inherited
@ResourceLock(value = "profile-meta", mode = ResourceAccessMode.READ)
@interface FlowProfileRead {
	// Composed native metadata.
}

/** Negative control receiving its native resource lock through composition. */
@FlowProfileRead
class FlowProfileMetaFixture extends FlowProfileFixture {
	// Meta-annotation.
}

/** Negative control for the repeatable native resource-lock container. */
@ResourceLock(value = "profile-first", mode = ResourceAccessMode.READ)
@ResourceLock("profile-second")
class FlowProfileRepeatedFixture extends FlowProfileFixture {
	// Repeatable container.
}

/**
 * Negative control for native resource metadata with an empty lock container.
 */
@ResourceLocks({})
class FlowProfileEmptyLocksFixture extends FlowProfileFixture {
	// Even an empty native container is outside the approved profile.
}

/**
 * Negative control for class isolation outside the supported native profile.
 */
@Isolated
class FlowProfileIsolatedFixture extends FlowProfileFixture {
	// Native isolation.
}

/**
 * Negative control applying composed resource metadata to the factory method.
 */
class FlowProfileMethodLockFixture extends FlowProfileFixture {
	@Override
	@TestFactory
	@FlowProfileRead
	Stream<DynamicNode> flows() {
		return super.flows();
	}
}

/** Carries composed resource metadata into implementing fixture classes. */
@FlowProfileRead
interface FlowProfileLockedInterface {
	// Interface resource metadata.
}

/**
 * Negative control inheriting resource metadata through an implemented
 * interface.
 */
class FlowProfileInterfaceFixture extends FlowProfileFixture implements FlowProfileLockedInterface {
	// Inherited interface resource metadata.
}

/** Negative control overriding concurrent native execution at class scope. */
@Execution(ExecutionMode.SAME_THREAD)
class FlowProfileSameFixture extends FlowProfileFixture {
	// Actual native mode conflict.
}

/** Negative control overriding concurrent native execution at factory scope. */
class FlowProfileMethodSameFixture extends FlowProfileFixture {
	@Override
	@TestFactory
	@Execution(ExecutionMode.SAME_THREAD)
	Stream<DynamicNode> flows() {
		return super.flows();
	}
}

/** Negative control replacing the required Flow method orderer. */
@TestMethodOrder(MethodOrderer.MethodName.class)
class FlowProfileOrderFixture extends FlowProfileFixture {
	// Explicit concurrent mode does not authorize a conflicting orderer.
}

/** Composes a serial native mode for conflicting-metadata probes. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Execution(ExecutionMode.SAME_THREAD)
@interface FlowProfileSerialMode {
	// Conflicting composed metadata, even with a direct concurrent override.
}

/**
 * Negative control combining explicit concurrent and composed serial execution.
 */
@Execution(ExecutionMode.CONCURRENT)
@FlowProfileSerialMode
class FlowProfileMetaModeFixture extends FlowProfileFixture {
	// Mixed explicit/composed execution metadata.
}

/** Negative control moving the factory to a separate timeout thread. */
class FlowProfileTimeoutFixture extends FlowProfileFixture {
	@Override
	@TestFactory
	@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
	Stream<DynamicNode> flows() {
		return super.flows();
	}
}

/** Negative control applying separate-thread timeouts at class scope. */
@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class FlowProfileClassTimeoutFixture extends FlowProfileFixture {
	// Class timeout applies to factory invocation.
}

/** Negative control applying a separate-thread timeout to lifecycle setup. */
class FlowProfileLifecycleTimeoutFixture extends FlowProfileFixture {
	/** Provides a timed lifecycle method without performing any Flow work. */
	@BeforeEach
	@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
	void setup() {
		// Guard is pre-Flow use, not pre-lifecycle invocation.
	}
}

/** Positive control for per-class instances and same-thread timeouts. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, threadMode = Timeout.ThreadMode.SAME_THREAD)
class FlowProfileHarmlessFixture extends FlowProfileFixture {
	// These baseline-supported annotations do not require physical affinity.
}

/** Negative control hosting a factory in a nested native test class. */
class FlowProfileNestedFixture {
	/**
	 * Supplies a genuine nested factory context rather than a synthetic context.
	 */
	@Nested
	class Inner extends FlowProfileFixture {
		// A real nested native factory context.
	}
}

/** Negative control combining the inherited factory with an ordinary test. */
class FlowProfileMixedFixture extends FlowProfileFixture {
	/** Adds native work outside the sole-factory profile without invoking Flow. */
	@Test
	void anotherTest() {
		// Public plan approval remains the caller's obligation.
	}
}
