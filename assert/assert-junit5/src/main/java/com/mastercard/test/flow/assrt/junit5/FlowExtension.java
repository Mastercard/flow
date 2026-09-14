package com.mastercard.test.flow.assrt.junit5;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.commons.support.AnnotationSupport;

/** Internal registration support; use {@link FlowTest}. */
public final class FlowExtension implements ParameterResolver, InvocationInterceptor {
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
		store.put( key, handle );
		return handle;
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
		handle.enterFactory();
		T original;
		try {
			original = invocation.proceed();
		}
		finally {
			handle.leaveFactory();
		}
		return (T) handle.consume( original );
	}

	private static void checkMode( ExtensionContext context ) {
		if( FlowMethodOrderer.parallel( context.getConfigurationParameter(
				FlowMethodOrderer.PARALLEL_PROPERTY ).orElse( "false" ) ) ) {
			throw new UnsupportedOperationException(
					"flow.parallel=true is not implemented yet; native parallel execution requires ticket08. "
							+ "Omit flow.parallel or set it to false for native serial execution." );
		}
		if( context.getExecutionMode() != ExecutionMode.SAME_THREAD ) {
			throw new IllegalStateException( "Flow serial execution requires native SAME_THREAD; "
					+ "remove conflicting @Execution or method-orderer configuration" );
		}
	}
}
