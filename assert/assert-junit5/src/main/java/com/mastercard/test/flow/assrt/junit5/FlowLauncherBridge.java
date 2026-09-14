package com.mastercard.test.flow.assrt.junit5;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryListener;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * Exact public call delegation; no engine internals or global request routing.
 */
class FlowLauncherBridge implements InvocationHandler, AutoCloseable {

	/** Original public launcher, including its registered listeners. */
	final Launcher delegate;
	private final Map<TestPlan, FlowNativeCall> previews = new IdentityHashMap<>();
	private boolean closed;

	/**
	 * Creates session-local tracking for approvals made through this launcher.
	 *
	 * @param delegate The launcher constructed in this live session
	 */
	FlowLauncherBridge( Launcher delegate ) {
		this.delegate = delegate;
	}

	private synchronized void checkOpen() {
		if( closed ) {
			throw new IllegalStateException( "Flow launcher session is closed" );
		}
	}

	/**
	 * Registers discovery listeners directly on the native launcher.
	 *
	 * @param listeners Original discovery listeners
	 */
	public void registerLauncherDiscoveryListeners( LauncherDiscoveryListener... listeners ) {
		delegate.registerLauncherDiscoveryListeners( listeners );
	}

	/**
	 * Registers execution listeners directly on the native launcher.
	 *
	 * @param listeners Original execution listeners
	 */
	public void registerTestExecutionListeners( TestExecutionListener... listeners ) {
		delegate.registerTestExecutionListeners( listeners );
	}

	/**
	 * Discovers without replacing the request and retains parallel preview
	 * approval.
	 *
	 * @param request The original discovery request
	 * @return The identical native preview, owned only by this live wrapper
	 */
	public TestPlan discover( LauncherDiscoveryRequest request ) {
		FlowNativeCall approval = requestCall( request );
		if( approval == null ) {
			return delegate.discover( request );
		}
		try {
			TestPlan plan = delegate.discover( request );
			synchronized( this ) {
				checkOpen();
				if( previews.size() >= 32 || previews.containsKey( plan ) ) {
					throw new IllegalStateException( "Flow preview capacity or identity conflict" );
				}
				previews.put( plan, approval );
			}
			return plan;
		}
		catch( Throwable failure ) {
			approval.close();
			throw failure;
		}
	}

	/**
	 * Validates parallel selection and allocates its exact-call receiver.
	 *
	 * @param request The actual execute request, never a replacement discovery
	 * @return A call-local receiver, or null for transparent serial execution
	 */
	final FlowNativeCall requestCall( LauncherDiscoveryRequest request ) {
		if( !FlowMethodOrderer.parallel( request.getConfigurationParameters()
				.get( FlowMethodOrderer.PARALLEL_PROPERTY ).orElse( "false" ) ) ) {
			return null;
		}
		checkOpen();
		return new FlowNativeCall( request );
	}

	/**
	 * Consumes a parallel preview's approval from this live wrapper only.
	 *
	 * @param plan The exact caller-supplied preview
	 * @return Its consumed approval, or null for a serial plan
	 */
	final synchronized FlowNativeCall planCall( TestPlan plan ) {
		if( !FlowMethodOrderer.parallel( plan.getConfigurationParameters()
				.get( FlowMethodOrderer.PARALLEL_PROPERTY ).orElse( "false" ) ) ) {
			return null;
		}
		checkOpen();
		FlowNativeCall approval = previews.remove( plan );
		if( approval == null ) {
			throw new IllegalArgumentException( "Unknown/imported/consumed Flow plan identity" );
		}
		return approval;
	}

	/**
	 * Delegates direct execution with a call-local receiver for parallel work.
	 *
	 * @param request   Original request
	 * @param listeners Existing call listeners, preserved in order
	 */
	public void execute( LauncherDiscoveryRequest request, TestExecutionListener... listeners ) {
		try( FlowNativeCall call = requestCall( request ) ) {
			delegate.execute( request, listeners( call, listeners ) );
		}
	}

	/**
	 * Executes an approved preview once and closes its receiver after delegation.
	 *
	 * @param plan      Original plan
	 * @param listeners Existing call listeners, preserved in order
	 */
	public void execute( TestPlan plan, TestExecutionListener... listeners ) {
		try( FlowNativeCall call = planCall( plan ) ) {
			delegate.execute( plan, listeners( call, listeners ) );
		}
	}

	/**
	 * Appends the optional receiver without changing existing listener order.
	 *
	 * @param call     Optional exact-call listener
	 * @param original Original listener array
	 * @return An augmented copy, or the unchanged serial array
	 */
	static TestExecutionListener[] listeners( FlowNativeCall call,
			TestExecutionListener[] original ) {
		if( call == null ) {
			return original;
		}
		TestExecutionListener[] result = Arrays.copyOf( original, original.length + 1 );
		result[original.length] = call;
		return result;
	}

	@Override
	public Object invoke( Object proxy, Method method, Object[] args ) {
		switch( method.getName() ) {
			case "discover":
				return discover( (LauncherDiscoveryRequest) args[0] );
			case "execute":
				if( args[0] instanceof TestPlan ) {
					execute( (TestPlan) args[0], (TestExecutionListener[]) args[1] );
				}
				else {
					execute( (LauncherDiscoveryRequest) args[0], (TestExecutionListener[]) args[1] );
				}
				return null;
			case "registerLauncherDiscoveryListeners":
				registerLauncherDiscoveryListeners( (LauncherDiscoveryListener[]) args[0] );
				return null;
			case "registerTestExecutionListeners":
				registerTestExecutionListeners( (TestExecutionListener[]) args[0] );
				return null;
			case "equals":
				return proxy == args[0];
			case "hashCode":
				return System.identityHashCode( proxy );
			case "toString":
				return "Flow public Launcher decorator";
			default:
				throw new UnsupportedOperationException( method.toString() );
		}
	}

	/**
	 * Discards unused preview approvals; active calls retain their own cleanup
	 * boundary.
	 */
	@Override
	public synchronized void close() {
		closed = true;
		previews.values().forEach( FlowNativeCall::close );
		previews.clear();
		// Active calls close in their own finally, not on session shutdown.
	}
}
