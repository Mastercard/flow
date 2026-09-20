package com.mastercard.test.flow.assrt.junit5;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.commons.support.AnnotationSupport;

/** Internal registration support; use {@link FlowTest}. */
public final class FlowExtension implements ParameterResolver {
	private static final ExtensionContext.Namespace OWNERS = ExtensionContext.Namespace
			.create( FlowExtension.class );

	@Override
	public boolean supportsParameter( ParameterContext parameter, ExtensionContext context ) {
		return parameter.getParameter().getType() == FlowExecution.class;
	}

	@Override
	public Object resolveParameter( ParameterContext parameter, ExtensionContext context ) {
		Method factory = context.getRequiredTestMethod();
		if( !factory.equals( parameter.getDeclaringExecutable() )
				|| !AnnotationSupport.isAnnotated( factory, TestFactory.class )
				|| Arrays.stream( factory.getParameterTypes() ).filter( t -> t == FlowExecution.class )
						.count() != 1 ) {
			throw new IllegalStateException(
					"Exactly one FlowExecution parameter is required on the Flow factory" );
		}
		// The class store closes the handle after the factory's whole subtree,
		// including concurrent leaves, has finished. A method context always has a
		// class context above it.
		ExtensionContext classContext = context;
		while( classContext.getTestMethod().isPresent() ) {
			classContext = classContext.getParent().get();
		}
		FlowExecution handle = new FlowExecution( concurrent( context ) );
		classContext.getStore( OWNERS ).put( context.getUniqueId(), handle );
		return handle;
	}

	/**
	 * A class can carry {@code @Execution(CONCURRENT)} while the global parallel
	 * switch is off, in which case Jupiter runs it on one thread; both must hold
	 * for the leaves to run concurrently.
	 */
	private static boolean concurrent( ExtensionContext context ) {
		return context.getExecutionMode() == ExecutionMode.CONCURRENT
				&& context.getConfigurationParameter( "junit.jupiter.execution.parallel.enabled" )
						.map( Boolean::parseBoolean ).orElse( false );
	}
}
