package com.mastercard.test.flow.assrt.junit5;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.support.descriptor.UriSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;

/** Native trace/source compatibility through a real parallel Launcher call. */
@SuppressWarnings("static-method")
class FlowTraceSourceCompatibilityTest {
	/**
	 * Real native registration cannot substitute comparable method-source evidence.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "same", "absent", "class", "method", "parameters" })
	void nativeMethodSourceEvidenceMustMatch( String change ) {
		TraceSourceFixture.methodBodies.set( 0 );
		String hook = "junit.platform.launcher.interceptors.enabled";
		String previous = System.getProperty( hook );
		System.setProperty( hook, "false" );
		AtomicInteger checked = new AtomicInteger();
		var request = request();
		try( FlowNativeCall call = new FlowNativeCall( request ) ) {
			LauncherFactory.create( LauncherConfig.builder()
					.enableTestExecutionListenerAutoRegistration( false )
					.enableLauncherSessionListenerAutoRegistration( false ).build() )
					.execute( request, call, new TestExecutionListener() {
						@Override
						public void dynamicTestRegistered( TestIdentifier id ) {
							if( !id.getDisplayName().equals( "method []" ) )
								return;
							checked.incrementAndGet();
							MethodSource actual = assertInstanceOf( MethodSource.class,
									id.getSource().orElseThrow() );
							TestSource source = change.equals( "absent" ) ? null
									: MethodSource.from(
											change.equals( "class" ) ? String.class.getName() : actual.getClassName(),
											change.equals( "method" ) ? "other" : actual.getMethodName(),
											change.equals( "parameters" ) ? "" : actual.getMethodParameterTypes() );
							// Replay the real identity at the existing receiver seam, changing only
							// source evidence. Jupiter still owns the actual descriptor and body.
							var evidence = new AbstractTestDescriptor( UniqueId.parse( id.getUniqueId() ),
									id.getDisplayName(), source ) {
								@Override
								public Type getType() {
									return Type.TEST;
								}
							};
							evidence.setParent( new EngineDescriptor(
									UniqueId.parse( id.getParentId().orElseThrow() ), "factory" ) );
							call.dynamicTestRegistered( TestIdentifier.from( evidence ) );
						}
					} );
			assertEquals( 1, checked.get() );
			if( change.equals( "same" ) || change.equals( "absent" ) ) {
				assertDoesNotThrow( call::check );
				assertEquals( 1, TraceSourceFixture.methodBodies.get() );
			}
			else {
				assertThrows( IllegalStateException.class, call::check );
				assertEquals( 0, TraceSourceFixture.methodBodies.get() );
			}
		}
		finally {
			if( previous == null )
				System.clearProperty( hook );
			else
				System.setProperty( hook, previous );
		}
	}

	/** Existing serial-compatible trace forms remain executable and unchanged. */
	@Test
	void parallelExecutionPreservesExistingTraceAndSourceForms() {
		TraceSourceFixture.bodyCount.set( 0 );
		TraceSourceFixture.traces.clear();
		Map<String, TestSource> sources = new ConcurrentHashMap<>();
		List<String> results = new CopyOnWriteArrayList<>();
		List<Throwable> failures = new CopyOnWriteArrayList<>();
		String hook = "junit.platform.launcher.interceptors.enabled";
		String previous = System.getProperty( hook );
		System.setProperty( hook, "true" );
		try {
			var request = request();
			LauncherFactory.create( LauncherConfig.builder()
					.enableTestExecutionListenerAutoRegistration( false )
					.enableLauncherSessionListenerAutoRegistration( false ).build() )
					.execute( request, new TestExecutionListener() {
						@Override
						public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
							result.getThrowable().ifPresent( failures::add );
							if( id.isTest() ) {
								results.add( id.getDisplayName() + ":" + result.getStatus() );
								id.getSource().ifPresent( source -> sources.put( id.getDisplayName(), source ) );
							}
						}
					} );
		}
		finally {
			if( previous == null )
				System.clearProperty( hook );
			else
				System.setProperty( hook, previous );
		}

		assertEquals( List.of(), failures, failures::toString );
		assertEquals( 7, TraceSourceFixture.bodyCount.get() );
		assertEquals( 7, results.stream().filter( r -> r.endsWith( ":SUCCESSFUL" ) ).count() );

		ClassSource automatic = assertInstanceOf( ClassSource.class, sources.get( "automatic []" ) );
		assertEquals( TraceSourceFixture.class.getName(), automatic.getClassName() );
		ClassSource addenda = assertInstanceOf( ClassSource.class, sources.get( "addenda []" ) );
		assertEquals( TraceSourceFixture.class.getName(), addenda.getClassName() );
		UriSource uri = assertInstanceOf( UriSource.class, sources.get( "uri []" ) );
		assertEquals( URI.create( "https://example.test/native-flow" ), uri.getUri() );
		MethodSource method = assertInstanceOf( MethodSource.class, sources.get( "method []" ) );
		assertEquals( TraceSourceFixture.class.getName(), method.getClassName() );
		assertEquals( "flows", method.getMethodName() );
		assertEquals( FlowExecution.class.getName(), method.getMethodParameterTypes() );
		assertTrue( Stream.of( "arbitrary []", "duplicate-one []", "duplicate-two []" )
				.map( sources::get )
				.noneMatch( source -> source instanceof ClassSource || source instanceof UriSource ),
				sources::toString );

		assertTrue( TraceSourceFixture.traces.get( "automatic" )
				.contains( TraceSourceFixture.class.getName() ) );
		assertTrue( TraceSourceFixture.traces.get( "addenda" ).endsWith( " [context]" ) );
		assertEquals( "arbitrary trace text", TraceSourceFixture.traces.get( "arbitrary" ) );
		assertEquals( "https://example.test/native-flow", TraceSourceFixture.traces.get( "uri" ) );
		assertEquals( "method:" + TraceSourceFixture.class.getName() + "#flows("
				+ FlowExecution.class.getName() + ")", TraceSourceFixture.traces.get( "method" ) );
		assertEquals( "duplicate trace", TraceSourceFixture.traces.get( "duplicate-one" ) );
		assertEquals( "duplicate trace", TraceSourceFixture.traces.get( "duplicate-two" ) );
	}

	private static LauncherDiscoveryRequest request() {
		return LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( TraceSourceFixture.class ) )
				.configurationParameter( "flow.parallel", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
				.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism", "3" )
				.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
						"3" )
				.build();
	}
}

@FlowTest
class TraceSourceFixture {
	/** Number of genuine Flow bodies entered by the Launcher call. */
	static final AtomicInteger bodyCount = new AtomicInteger();
	/** Counts the body whose native method-source evidence is under test. */
	static final AtomicInteger methodBodies = new AtomicInteger();
	/** Original model traces keyed by Flow description. */
	static final Map<String, String> traces = new ConcurrentHashMap<>();

	/**
	 * @param execution The native factory owner
	 * @return The accepted trace forms as real native dynamic tests
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		List<Flow> selected = new ArrayList<>();
		selected.add( flow( "automatic", null ) );
		selected.add( Creator.build( f -> f.meta( m -> m.description( "addenda" )
				.trace( additions -> additions.add( "context" ) ) )
				.call( c -> c.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) ) ) );
		selected.add( flow( "arbitrary", "arbitrary trace text" ) );
		selected.add( flow( "uri", "https://example.test/native-flow" ) );
		selected.add( flow( "method", "method:" + TraceSourceFixture.class.getName() + "#flows("
				+ FlowExecution.class.getName() + ")" ) );
		selected.add( flow( "duplicate-one", "duplicate trace" ) );
		selected.add( flow( "duplicate-two", "duplicate trace" ) );
		selected.forEach( flow -> traces.put( flow.meta().description(), flow.meta().trace() ) );
		return execution.flocessor( "trace compatibility", new Mdl() {
			@Override
			public Stream<Flow> flows( java.util.Set<String> include,
					java.util.Set<String> exclude ) {
				return selected.stream();
			}
		} ).system( State.FUL, Actrs.BEN )
				.independent( "isolated compatibility fixtures", flow -> true )
				.behaviour( assertion -> {
					bodyCount.incrementAndGet();
					if( assertion.flow().meta().description().equals( "method" ) )
						methodBodies.incrementAndGet();
					assertion.actual().response( assertion.expected().response().content() );
				} ).tests();
	}

	private static Flow flow( String description, String trace ) {
		return Creator.build( f -> {
			f.meta( m -> {
				m.description( description );
				if( trace != null )
					m.trace( trace );
			} ).call( c -> c.from( Actrs.AVA ).to( Actrs.BEN )
					.request( new Msg( "request" ) ).response( new Msg( "response" ) ) );
		} );
	}
}
