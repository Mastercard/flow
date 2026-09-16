package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.UriSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;

/** Native trace/source compatibility through a real parallel Launcher call. */
@SuppressWarnings("static-method")
class FlowTraceSourceCompatibilityTest {
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
			var request = LauncherDiscoveryRequestBuilder.request()
					.selectors( selectClass( TraceSourceFixture.class ) )
					.configurationParameter( "flow.parallel", "true" )
					.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
					.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism",
							"3" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
							"3" )
					.build();
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
		assertEquals( 6, TraceSourceFixture.bodyCount.get() );
		assertEquals( 6, results.stream().filter( r -> r.endsWith( ":SUCCESSFUL" ) ).count() );

		ClassSource automatic = assertInstanceOf( ClassSource.class, sources.get( "automatic []" ) );
		assertEquals( TraceSourceFixture.class.getName(), automatic.getClassName() );
		ClassSource addenda = assertInstanceOf( ClassSource.class, sources.get( "addenda []" ) );
		assertEquals( TraceSourceFixture.class.getName(), addenda.getClassName() );
		UriSource uri = assertInstanceOf( UriSource.class, sources.get( "uri []" ) );
		assertEquals( URI.create( "https://example.test/native-flow" ), uri.getUri() );
		assertTrue( Stream.of( "arbitrary []", "duplicate-one []", "duplicate-two []" )
				.map( sources::get )
				.noneMatch( source -> source instanceof ClassSource || source instanceof UriSource ),
				sources::toString );

		assertTrue( TraceSourceFixture.traces.get( "automatic" )
				.contains( TraceSourceFixture.class.getName() ) );
		assertTrue( TraceSourceFixture.traces.get( "addenda" ).endsWith( " [context]" ) );
		assertEquals( "arbitrary trace text", TraceSourceFixture.traces.get( "arbitrary" ) );
		assertEquals( "https://example.test/native-flow", TraceSourceFixture.traces.get( "uri" ) );
		assertEquals( "duplicate trace", TraceSourceFixture.traces.get( "duplicate-one" ) );
		assertEquals( "duplicate trace", TraceSourceFixture.traces.get( "duplicate-two" ) );
	}
}

@FlowTest
class TraceSourceFixture {
	/** Number of genuine Flow bodies entered by the Launcher call. */
	static final AtomicInteger bodyCount = new AtomicInteger();
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
