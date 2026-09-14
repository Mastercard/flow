package com.mastercard.test.flow.assrt.junit5;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.ResourceLocks;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

/**
 * Run-owned public observations of the standard native execution profile, not
 * Launcher request/plan approval. The caller must separately prove the sole
 * approved Flow factory, including its handle parameter and selection
 * ownership. No body is moved to another executor and no idle-worker estimate
 * is used.
 * <p>
 * Java 17 exposes pool identity and target parallelism, not its actual maximum,
 * minimum-runnable count or saturation predicate. Frozen standard configuration
 * is not proof of every live constructor setting or arbitrary extension work.
 * Required physical worker affinity must be rejected by the parent resource
 * preparation boundary; no such requirement API is supplied by this profile.
 */
final class FlowNativeProfile {
	private static final String PARALLEL = "junit.jupiter.execution.parallel.";
	private static final String TIMEOUT = "junit.jupiter.execution.timeout.";
	private final ExtensionContext context;
	private final ExtensionContext root;
	private final Class<?> type;
	private final Method method;
	private final String id;
	private final ForkJoinPool pool;
	private final Map<String, String> settings;
	private final int target;

	private FlowNativeProfile( ExtensionContext context ) {
		metadata( context );
		this.context = context;
		root = context.getRoot();
		type = context.getRequiredTestClass();
		method = context.getRequiredTestMethod();
		id = context.getUniqueId();
		settings = configuration( context );
		target = Integer.parseInt( settings.get( "config.fixed.parallelism" ) );
		pool = currentPool();
		checkAdmission();
	}

	/**
	 * Captures checked factory metadata, configuration and the actual native pool.
	 *
	 * @param context The genuine factory context
	 * @return Its checked native profile
	 */
	static FlowNativeProfile enter( ExtensionContext context ) {
		return new FlowNativeProfile( context );
	}

	/**
	 * Revalidates factory identity, metadata, settings and native pool ownership.
	 *
	 * @param context The context immediately before original factory invocation
	 */
	void check( ExtensionContext context ) {
		if( context.getRoot() != root || context.getRequiredTestClass() != type
				|| !context.getRequiredTestMethod().equals( method )
				|| !context.getUniqueId().equals( id ) ) {
			throw new IllegalStateException( "Flow native factory context differs from profile entry" );
		}
		metadata( context );
		if( !settings.equals( configuration( context ) ) ) {
			throw new IllegalStateException(
					"Flow native fixed profile changed before factory invocation" );
		}
		checkAdmission();
	}

	/** Checks the actual native admission thread. */
	void checkAdmission() {
		if( currentPool() != pool || pool.getParallelism() != target || pool.isShutdown() ) {
			throw new IllegalStateException(
					"Flow native admission requires the captured live pool and parallelism" );
		}
		if( !settings.equals( configuration( context ) ) ) {
			throw new IllegalStateException( "Flow native fixed profile changed before admission" );
		}
	}

	private static ForkJoinPool currentPool() {
		ForkJoinPool current = ForkJoinTask.getPool();
		if( !(Thread.currentThread() instanceof ForkJoinWorkerThread) || current == null
				|| current.getClass() != ForkJoinPool.class ) {
			throw new IllegalStateException(
					"Flow native execution requires a standard ForkJoinPool worker pool" );
		}
		return current;
	}

	private static Map<String, String> configuration( ExtensionContext context ) {
		Map<String, String> values = new LinkedHashMap<>();
		for( String key : new String[] { "enabled", "config.strategy", "config.fixed.parallelism",
				"config.fixed.max-pool-size", "config.fixed.saturate" } ) {
			values.put( key, context.getConfigurationParameter( PARALLEL + key ).orElse( "" ) );
		}
		if( !"true".equalsIgnoreCase( values.get( "enabled" ) )
				|| !"fixed".equalsIgnoreCase( values.get( "config.strategy" ) ) ) {
			throw new IllegalStateException(
					"Flow native execution requires the enabled standard fixed strategy" );
		}
		try {
			int parallelism = Integer.parseInt( values.get( "config.fixed.parallelism" ) );
			String maximum = values.get( "config.fixed.max-pool-size" );
			int max = maximum.isEmpty() ? parallelism + 256 : Integer.parseInt( maximum );
			if( parallelism < 2 || parallelism > 32767 || max < parallelism || max > 32767 ) {
				throw new IllegalStateException(
						"Flow native fixed parallelism must be 2..32767 with a valid maximum" );
			}
		}
		catch( NumberFormatException failure ) {
			throw new IllegalStateException( "Flow native fixed parallelism/maximum must be integers",
					failure );
		}
		String saturation = values.get( "config.fixed.saturate" );
		if( !saturation.isEmpty() && !"true".equalsIgnoreCase( saturation )
				&& !"false".equalsIgnoreCase( saturation ) ) {
			throw new IllegalStateException( "Flow native fixed saturation must be true or false" );
		}
		return values;
	}

	private static void metadata( ExtensionContext context ) {
		Class<?> type = context.getRequiredTestClass();
		Method factory = context.getRequiredTestMethod();
		List<Method> tests = ReflectionSupport.findMethods( type,
				m -> AnnotationSupport.isAnnotated( m, Testable.class ), HierarchyTraversalMode.TOP_DOWN );
		if( type.getEnclosingClass() != null || tests.size() != 1 || !tests.get( 0 ).equals( factory )
				|| !AnnotationSupport.isAnnotated( factory, TestFactory.class ) ) {
			throw new IllegalStateException(
					"Flow native profile requires a sole top-level ordinary factory" );
		}
		for( ExtensionContext parent = context; parent != null;
				parent = parent.getParent().orElse( null ) ) {
			if( parent.getTestClass().isPresent() && parent.getRequiredTestClass() != type ) {
				throw new IllegalStateException(
						"Flow native profile requires a sole top-level class context" );
			}
		}
		hierarchy( type, context, new HashSet<>() );
		for( Method method : ReflectionSupport.findMethods( type,
				m -> m.equals( factory ) || lifecycle( m ), HierarchyTraversalMode.TOP_DOWN ) ) {
			annotations( method, context, new HashSet<>() );
		}
		if( context.getExecutionMode() != ExecutionMode.CONCURRENT ) {
			throw new IllegalStateException(
					"Flow native mode/orderer requires actual CONCURRENT execution" );
		}
		Class<?> orderer = AnnotationSupport.findAnnotation( type, TestMethodOrder.class )
				.map( TestMethodOrder::value ).orElse( null );
		if( orderer != FlowMethodOrderer.class ) {
			throw new IllegalStateException( "Flow native mode/orderer requires FlowMethodOrderer" );
		}
		if( "SEPARATE_THREAD".equalsIgnoreCase( timeoutMode( context ) ) ) {
			for( String key : new String[] { "default", "testable.method.default",
					"testfactory.method.default",
					"lifecycle.method.default", "beforeall.method.default", "beforeeach.method.default",
					"aftereach.method.default", "afterall.method.default" } ) {
				if( context.getConfigurationParameter( TIMEOUT + key ).isPresent() ) {
					throw new IllegalStateException(
							"Flow native profile rejects separate-thread timeout configuration" );
				}
			}
		}
	}

	private static boolean lifecycle( Method method ) {
		return AnnotationSupport.isAnnotated( method, BeforeAll.class )
				|| AnnotationSupport.isAnnotated( method, BeforeEach.class )
				|| AnnotationSupport.isAnnotated( method, AfterEach.class )
				|| AnnotationSupport.isAnnotated( method, AfterAll.class );
	}

	private static void hierarchy( Class<?> type, ExtensionContext context, Set<Class<?>> visited ) {
		if( type == null || !visited.add( type ) ) {
			return;
		}
		annotations( type, context, new HashSet<>() );
		for( Class<?> child : type.getDeclaredClasses() ) {
			if( AnnotationSupport.isAnnotated( child, Nested.class ) ) {
				throw new IllegalStateException(
						"Flow native profile requires a sole top-level factory without nested work" );
			}
		}
		for( Class<?> parent : type.getInterfaces() ) {
			hierarchy( parent, context, visited );
		}
		hierarchy( type.getSuperclass(), context, visited );
	}

	private static void annotations( AnnotatedElement element, ExtensionContext context,
			Set<Class<? extends Annotation>> visited ) {
		for( Annotation annotation : element.getDeclaredAnnotations() ) {
			Class<? extends Annotation> kind = annotation.annotationType();
			// Presence rejects READ, empty containers and newer provider-only/CHILDREN
			// forms. Never invoke a provider or refer to post-5.10 annotation members.
			if( kind == ResourceLock.class || kind == ResourceLocks.class || kind == Isolated.class ) {
				throw new IllegalStateException(
						"Flow native profile rejects native resource/isolation metadata: " + element );
			}
			if( annotation instanceof Execution
					&& ((Execution) annotation).value() != ExecutionMode.CONCURRENT
					|| annotation instanceof TestMethodOrder
							&& ((TestMethodOrder) annotation).value() != FlowMethodOrderer.class ) {
				throw new IllegalStateException(
						"Flow native mode/orderer metadata conflicts: " + element );
			}
			if( annotation instanceof Timeout ) {
				Timeout.ThreadMode mode = ((Timeout) annotation).threadMode();
				if( mode == Timeout.ThreadMode.SEPARATE_THREAD
						|| mode == Timeout.ThreadMode.INFERRED
								&& "SEPARATE_THREAD".equalsIgnoreCase( timeoutMode( context ) ) ) {
					throw new IllegalStateException(
							"Flow native profile rejects separate-thread timeout metadata: " + element );
				}
			}
			if( visited.add( kind ) && !kind.getName().startsWith( "java.lang.annotation." ) ) {
				annotations( kind, context, visited );
			}
		}
	}

	private static String timeoutMode( ExtensionContext context ) {
		return context.getConfigurationParameter( TIMEOUT + "thread.mode.default" )
				.orElse( "SAME_THREAD" );
	}
}
