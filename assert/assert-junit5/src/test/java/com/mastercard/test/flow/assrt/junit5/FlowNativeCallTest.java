package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;

/**
 * Real Launcher tests of the internal attachment boundary, not a parallel-body
 * or packaged-provider acceptance claim. The test extension exercises the seam
 * that the early Launcher/Flow integration will own, without enabling it
 * publicly.
 */
@SuppressWarnings("static-method")
class FlowNativeCallTest {
	@Test
	void missingOrAmbiguousReceiptsNeverEnterTheOriginalFactory() {
		for( int receivers : new int[] { 0, 2 } ) {
			NativeReceiptFixture.events = new Events();
			var request = request( NativeReceiptFixture.class );
			try( FlowNativeCall first = new FlowNativeCall( request );
					FlowNativeCall second = new FlowNativeCall( request ) ) {
				List<Throwable> failures = execute( request, receivers == 0
						? new TestExecutionListener[0]
						: new TestExecutionListener[] { first, second } );
				assertTrue( failures.stream().anyMatch( f -> f.toString().contains( "exactly one" ) ),
						failures::toString );
				assertEquals( 0, NativeReceiptFixture.events.factories );
				assertEquals( 0, NativeReceiptFixture.events.bodies );
			}
		}
	}

	@Test
	void differentClassAndReusedCallCannotAcquireTheSameLookingFactory() {
		var request = request( NativeReceiptFixture.class );
		NativeReceiptFixture.events = new Events();
		try( FlowNativeCall call = new FlowNativeCall( request ) ) {
			List<Throwable> failures = execute( request( OtherReceiptFixture.class ), call );
			assertTrue( !failures.isEmpty() );
			assertEquals( 0, NativeReceiptFixture.events.factories );
			assertThrows( IllegalStateException.class, call::check );
		}
		try( FlowNativeCall call = new FlowNativeCall( request ) ) {
			assertEquals( List.of(), execute( request, call ) );
			NativeReceiptFixture.events = new Events();
			assertTrue( !execute( request, call ).isEmpty() );
			assertEquals( 0, NativeReceiptFixture.events.factories );
			assertThrows( IllegalStateException.class, call::check );
		}
	}

	@Test
	void unsupportedSelectorAndMixedActualPlanAreRejected() {
		assertThrows( IllegalArgumentException.class, () -> new FlowNativeCall(
				LauncherDiscoveryRequestBuilder.request().selectors( selectMethod(
						NativeReceiptFixture.class, "flows", FlowExecution.class.getName() ) ).build() ) );
		NativeReceiptFixture.events = new Events();
		var request = request( MixedReceiptFixture.class );
		try( FlowNativeCall call = new FlowNativeCall( request ) ) {
			assertTrue( !execute( request, call ).isEmpty() );
			assertThrows( IllegalStateException.class, call::check );
			assertEquals( 0, NativeReceiptFixture.events.factories );
		}
	}

	@Test
	void nativeListenerFailureIsLatchedAndCheckedOutsideTheListener() {
		NativeReceiptFixture.events = new Events();
		NativeReceiptFixture.events.rejectRegistration = true;
		var request = request( NativeReceiptFixture.class );
		try( FlowNativeCall call = new FlowNativeCall( request ) ) {
			List<Throwable> failures = execute( request, call );
			assertTrue( !failures.isEmpty() );
			IllegalStateException failure = assertThrows( IllegalStateException.class, call::check );
			assertEquals( "native observer diagnostic", failure.getCause().getMessage() );
			assertEquals( 0, NativeReceiptFixture.events.bodies );
			assertEquals( 1, NativeReceiptFixture.events.factoryTerminals );
		}
	}

	@Test
	void descriptionClosureCannotReleaseAndCallClosureCannotInventDrainage() {
		NativeReceiptFixture.events = new Events();
		NativeReceiptFixture.events.releaseAtFactoryTerminal = false;
		var request = request( NativeReceiptFixture.class );
		FlowNativeCall call = new FlowNativeCall( request );
		try {
			assertEquals( List.of(), execute( request, call ) );
			assertTrue( NativeReceiptFixture.events.earlyReleaseRejected );
			assertEquals( 1, NativeReceiptFixture.events.factoryTerminals );
			call.close();
			IllegalStateException failure = assertThrows( IllegalStateException.class,
					NativeReceiptFixture.events.attachment::check );
			assertTrue( failure.getCause().getMessage().contains( "did not drain" ) );
			assertEquals( 1, NativeReceiptFixture.events.factoryTerminals,
					"Call closure must not synthesize native completion" );
			// A safe late owner can detach; this does not erase the incomplete call.
			NativeReceiptFixture.events.attachment.release();
			NativeReceiptFixture.events.attachment.release();
			assertThrows( IllegalStateException.class, call::check );
		}
		finally {
			call.close();
		}
	}

	private static LauncherDiscoveryRequest request( Class<?> type ) {
		return LauncherDiscoveryRequestBuilder.request().selectors( selectClass( type ) )
				.configurationParameter( "flow.parallel", "false" ).build();
	}

	private static List<Throwable> execute( LauncherDiscoveryRequest request,
			TestExecutionListener... listeners ) {
		List<Throwable> failures = new ArrayList<>();
		List<TestExecutionListener> all = new ArrayList<>( List.of( listeners ) );
		all.add( new TestExecutionListener() {
			@Override
			public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
				result.getThrowable().ifPresent( failures::add );
			}
		} );
		LauncherFactory.create( LauncherConfig.builder()
				.enableLauncherSessionListenerAutoRegistration( false )
				.enableTestExecutionListenerAutoRegistration( false ).build() )
				.execute( request, all.toArray( TestExecutionListener[]::new ) );
		return failures;
	}

	@Test
	void receiptBindsTheActualExecuteCallAndNativeFactory() {
		NativeReceiptFixture.events = new Events();
		var request = LauncherDiscoveryRequestBuilder.request()
				.selectors( selectClass( NativeReceiptFixture.class ) )
				.configurationParameter( "flow.parallel", "false" ).build();
		List<Throwable> failures = new ArrayList<>();
		try( FlowNativeCall call = new FlowNativeCall( request ) ) {
			LauncherFactory.create( LauncherConfig.builder()
					.enableLauncherSessionListenerAutoRegistration( false )
					.enableTestExecutionListenerAutoRegistration( false ).build() )
					.execute( request, call, new TestExecutionListener() {
						@Override
						public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
							result.getThrowable().ifPresent( failures::add );
						}
					} );
			call.check();
		}
		assertEquals( List.of(), failures, failures::toString );
		Events e = NativeReceiptFixture.events;
		assertEquals( 9, e.registered.size() );
		assertEquals( e.registered, e.started );
		assertEquals( e.started, e.finished );
		assertEquals( 9, e.bodies );
		assertEquals( 1, e.factoryTerminals );
		assertEquals( List.of( 32, 45, 61, 27, 41, 55, 22, 37, 49 ), e.lines );
		assertTrue( e.results.stream().allMatch( r -> r == TestExecutionResult.Status.SUCCESSFUL ) );
		assertTrue( e.descriptionClosedBeforeFactoryTerminal );
	}

	static final class Events implements FlowNativeCall.Observer {
		final List<String> registered = new ArrayList<>();
		final List<String> started = new ArrayList<>();
		final List<String> finished = new ArrayList<>();
		final List<Integer> lines = new ArrayList<>();
		final List<TestExecutionResult.Status> results = new ArrayList<>();
		FlowNativeCall.Attachment attachment;
		int factories;
		int bodies;
		int factoryTerminals;
		boolean descriptionClosed;
		boolean descriptionClosedBeforeFactoryTerminal;
		boolean rejectRegistration;
		boolean releaseAtFactoryTerminal = true;
		boolean earlyReleaseRejected;

		@Override
		public void registered( TestIdentifier id ) {
			if( rejectRegistration ) {
				throw new IllegalStateException( "native observer diagnostic" );
			}
			registered.add( id.getUniqueId() );
			ClassSource source = (ClassSource) id.getSource().orElseThrow();
			lines.add( source.getPosition().orElseThrow().getLine() );
		}

		@Override
		public void started( TestIdentifier id ) {
			started.add( id.getUniqueId() );
		}

		@Override
		public void finished( TestIdentifier id, TestExecutionResult result ) {
			finished.add( id.getUniqueId() );
			results.add( result.getStatus() );
		}

		@Override
		public void factoryFinished( TestExecutionResult result ) {
			factoryTerminals++;
			descriptionClosedBeforeFactoryTerminal = descriptionClosed;
			if( releaseAtFactoryTerminal ) {
				attachment.release();
			}
		}
	}

	static final class ReceiptExtension implements InvocationInterceptor {
		@Override
		public <T> T interceptTestFactoryMethod( Invocation<T> invocation,
				ReflectiveInvocationContext<Method> method, ExtensionContext context ) throws Throwable {
			Events events = NativeReceiptFixture.events;
			events.attachment = FlowNativeCall.attach( context, events );
			events.attachment.check();
			return invocation.proceed();
		}
	}
}

@FlowTest
@ExtendWith(FlowNativeCallTest.ReceiptExtension.class)
class NativeReceiptFixture {
	static FlowNativeCallTest.Events events;

	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		events.factories++;
		return execution.flocessor( "native receipt", new Mdl() )
				.system( State.FUL, Actrs.BEN )
				.behaviour( a -> {
					events.attachment.check();
					events.bodies++;
					a.actual().response( a.expected().response().content() );
				} ).tests().onClose( () -> {
					events.descriptionClosed = true;
					try {
						events.attachment.release();
					}
					catch( IllegalStateException expected ) {
						events.earlyReleaseRejected = true;
					}
				} );
	}
}

class OtherReceiptFixture extends NativeReceiptFixture {
	// Same method and model, but not the approved class.
}

class MixedReceiptFixture extends NativeReceiptFixture {
	@Test
	void unrelatedWorkload() {
		// Extra native workload prevents receipt approval before the Flow factory.
	}
}
