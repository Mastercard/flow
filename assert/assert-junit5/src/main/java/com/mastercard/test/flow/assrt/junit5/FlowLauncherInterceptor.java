package com.mastercard.test.flow.assrt.junit5;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherInterceptor;

/**
 * Early public Launcher construction decorator for exact Flow execute-call
 * provenance. Enable {@code junit.platform.launcher.interceptors.enabled=true}
 * before Launcher construction, not in a discovery request. This hook is
 * EXPERIMENTAL at Platform 1.10 and MAINTAINED at 6.0.3, not all-STABLE.
 * <p>
 * Serial calls pass through without receipts or call listeners. Parallel
 * approval does not itself enable processing: the invocation owner must attach
 * and check {@link FlowNativeCall}. Explicit sessions support bounded,
 * single-use previews; LauncherFactory.create uses per-operation sessions, so
 * its previews cannot be carried into a later operation. Session closure
 * discards unused approvals, not body/resource ownership. Concurrent session
 * close/use is not a safe-stop API.
 */
public final class FlowLauncherInterceptor implements LauncherInterceptor {

	private final List<FlowLauncherBridge> bridges = new ArrayList<>();

	/** Creates the service-loaded, session-lifetime construction hook. */
	public FlowLauncherInterceptor() {
		// Public no-argument constructor required by ServiceLoader.
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T intercept( Invocation<T> invocation ) {
		T value = invocation.proceed();
		if( !(value instanceof Launcher) ) {
			return value;
		}
		Launcher delegate = (Launcher) value;
		ClassLoader loader = FlowLauncherInterceptor.class.getClassLoader();
		FlowLauncherBridge bridge;
		Object wrapper;
		try {
			Class.forName( "org.junit.platform.launcher.LauncherExecutionRequest", false, loader );
		}
		catch( ClassNotFoundException absent ) {
			bridge = new FlowLauncherBridge( delegate );
			// The baseline adapter only dispatches the four public 1.10 operations.
			// A concrete implements-Launcher class would require the 6-only abstract
			// overload when compiling the normal repository build against JUnit 6.
			wrapper = Proxy.newProxyInstance( loader, new Class<?>[] { Launcher.class }, bridge );
			synchronized( bridges ) {
				bridges.add( bridge );
			}
			return (T) wrapper;
		}
		try {
			bridge = (FlowLauncherBridge) Class.forName(
					"com.mastercard.test.flow.assrt.junit5.FlowLauncherSix", true, loader )
					.getConstructor( Launcher.class ).newInstance( delegate );
			wrapper = bridge;
		}
		catch( ReflectiveOperationException failure ) {
			throw new IllegalStateException( "Optional JUnit 6 Flow launcher is unavailable", failure );
		}
		synchronized( bridges ) {
			bridges.add( bridge );
		}
		return (T) wrapper;
	}

	@Override
	public void close() {
		synchronized( bridges ) {
			bridges.forEach( FlowLauncherBridge::close );
			bridges.clear();
		}
	}
}
