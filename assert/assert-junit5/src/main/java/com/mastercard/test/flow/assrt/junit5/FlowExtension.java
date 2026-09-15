package com.mastercard.test.flow.assrt.junit5;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.function.Executable;
import org.junit.platform.commons.support.AnnotationSupport;

/** Internal registration support; use {@link FlowTest}. */
public final class FlowExtension implements ParameterResolver, InvocationInterceptor {
	private static final ThreadLocal<ExtensionContext> INVOCATION = new ThreadLocal<>();
	private static final ThreadLocal<Executable> EXECUTABLE = new ThreadLocal<>();
	private static final ExtensionContext.Namespace OWNERS = ExtensionContext.Namespace
			.create( FlowExtension.class );

	@Override
	public boolean supportsParameter( ParameterContext parameter, ExtensionContext context ) {
		return parameter.getParameter().getType() == FlowExecution.class;
	}

	@Override
	public Object resolveParameter( ParameterContext parameter, ExtensionContext context ) {
		checkMode( context );
		Method factory = context.getRequiredTestMethod();
		if( !factory.equals( parameter.getDeclaringExecutable() )
				|| !AnnotationSupport.isAnnotated( factory, TestFactory.class )
				|| Arrays.stream( factory.getParameterTypes() ).filter( t -> t == FlowExecution.class )
						.count() != 1 ) {
			throw new IllegalStateException(
					"Exactly one FlowExecution parameter is required on the Flow factory" );
		}
		ExtensionContext classContext = context;
		while( classContext.getTestMethod().isPresent() ) {
			classContext = classContext.getParent()
					.orElseThrow( () -> new IllegalStateException( "Missing Flow class owner" ) );
		}
		ExtensionContext.Store store = classContext.getStore( OWNERS );
		String key = context.getUniqueId();
		FlowExecution handle = new FlowExecution( () -> store.remove( key ) );
		store.put( key, new Owner( handle ) );
		if( parallel( context ) ) {
			handle.attachParallel( context );
		}
		return handle;
	}

	/** Distinguishes actual store teardown from an explicit public handle close. */
	@SuppressWarnings("deprecation")
	private record Owner(FlowExecution execution)
			implements ExtensionContext.Store.CloseableResource, AutoCloseable {
		@Override
		public void close() {
			execution.backstop();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T interceptTestFactoryMethod( Invocation<T> invocation,
			ReflectiveInvocationContext<Method> method, ExtensionContext context ) throws Throwable {
		checkMode( context );
		List<FlowExecution> handles = method.getArguments().stream()
				.filter( FlowExecution.class::isInstance )
				.map( FlowExecution.class::cast ).collect( java.util.stream.Collectors.toList() );
		if( handles.size() != 1 ) {
			throw new IllegalStateException(
					"Exactly one FlowExecution parameter is required on the Flow factory" );
		}
		FlowExecution handle = handles.get( 0 );
		handle.checkFactory( context );
		handle.enterFactory();
		T original;
		try {
			original = invocation.proceed();
			handle.reportResourceFallback( context );
		}
		catch( Throwable failure ) {
			try {
				handle.stop( failure );
			}
			catch( Throwable cleanup ) {
				if( cleanup != failure )
					failure.addSuppressed( cleanup );
			}
			throw failure;
		}
		finally {
			handle.leaveFactory();
		}
		return (T) handle.consume( original );
	}

	private static boolean parallel( ExtensionContext context ) {
		return FlowMethodOrderer.parallel( context.getConfigurationParameter(
				FlowMethodOrderer.PARALLEL_PROPERTY ).orElse( "false" ) );
	}

	private static void checkMode( ExtensionContext context ) {
		if( !parallel( context ) && context.getExecutionMode() != ExecutionMode.SAME_THREAD ) {
			throw new IllegalStateException( "Flow serial execution requires native SAME_THREAD; "
					+ "remove conflicting @Execution or method-orderer configuration" );
		}
	}

	/**
	 * Jupiter 6 removed the old default method; its three-argument entry delegates
	 * to the same guard. No executable is reconstructed from the invocation
	 * context.
	 *
	 * @param invocation The native invocation to proceed with
	 * @param dynamic    The context exposing the original executable
	 * @param context    The native leaf context
	 * @throws Throwable If ownership validation or invocation fails
	 */
	@Override
	public void interceptDynamicTest( Invocation<Void> invocation,
			DynamicTestInvocationContext dynamic, ExtensionContext context ) throws Throwable {
		Executable previous = EXECUTABLE.get();
		try {
			EXECUTABLE.set( dynamic.getExecutable() );
			interceptDynamicTest( invocation, context );
		}
		finally {
			if( previous == null ) {
				EXECUTABLE.remove();
			}
			else {
				EXECUTABLE.set( previous );
			}
		}
	}

	/**
	 * Guards the original two-argument Jupiter 5.10 interception entry and restores
	 * the previous worker context after invocation.
	 *
	 * @param invocation The native invocation to proceed with
	 * @param context    The native leaf context
	 * @throws Throwable If ownership validation or invocation fails
	 */
	@SuppressWarnings("deprecation")
	public void interceptDynamicTest( Invocation<Void> invocation, ExtensionContext context )
			throws Throwable {
		ExtensionContext factory = null;
		// Some native leaf contexts inherit the factory method. Find the actual
		// factory owner using the class-local stored handle, never a guessed UID.
		FlowExecution owner = null;
		for( ExtensionContext candidate = context; candidate != null;
				candidate = candidate.getParent().orElse( null ) ) {
			for( ExtensionContext ancestor = candidate; ancestor != null;
					ancestor = ancestor.getParent().orElse( null ) ) {
				Owner found = ancestor.getStore( OWNERS )
						.get( candidate.getUniqueId(), Owner.class );
				if( found != null ) {
					owner = found.execution();
					factory = candidate;
					break;
				}
			}
			if( owner != null ) {
				break;
			}
		}
		ExtensionContext previous = INVOCATION.get();
		try {
			if( owner != null && owner.parallel() ) {
				owner.checkNativeOwner( factory );
				INVOCATION.set( context );
			}
			invocation.proceed();
		}
		finally {
			if( previous == null ) {
				INVOCATION.remove();
			}
			else {
				INVOCATION.set( previous );
			}
		}
	}

	/** @return The current guarded parallel leaf context, or null outside one */
	static ExtensionContext invocationContext() {
		return INVOCATION.get();
	}

	/** @return The original executable exposed by native interception, or null */
	static Executable invocationExecutable() {
		return EXECUTABLE.get();
	}
}
