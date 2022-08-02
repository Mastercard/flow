package com.mastercard.test.flow.builder;

import static java.util.stream.Collectors.joining;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;

/**
 * Utility for walking the call stack to find where a {@link Flow} is being
 * created
 *
 * @see SkipTrace
 */
@SkipTrace
public class Trace {

	private static Supplier<StackTraceElement[]> tracer = null;
	private static final Set<String> skippedMethods = new HashSet<>();

	private Trace() {
		// no instances
	}

	/**
	 * @param t How to get the stack trace, or <code>null</code> to revert to
	 *          default behaviour
	 */
	static void tracer( Supplier<StackTraceElement[]> t ) {
		tracer = t;
	}

	/**
	 * Walks up the call stack to find where this method has been called from. Apply
	 * the {@link SkipTrace} annotation to packages, types and methods that should
	 * be ignored when walking the stack
	 *
	 * @return A human-readable identifier for where this method has been called
	 *         from
	 */
	public static String trace() {
		StackTraceElement[] stack = tracer != null
				? tracer.get()
				: Thread.currentThread().getStackTrace();
		for( StackTraceElement ste : stack ) {
			try {
				Class<?> cls = Class.forName( ste.getClassName() );
				if( isThreadDotGetStackTrace( ste )
						|| isClassSkipped( cls )
						|| isPackageSkipped( cls )
						|| isMethodSkipped( ste, cls ) ) {
					// this element is not useful in locating the call location, skip it
					continue;
				}
			}
			catch( ClassNotFoundException e ) {
				throw new IllegalStateException( "Failed to load class for " + ste, e );
			}

			return String.format( "%s.%s(%s:%s)",
					ste.getClassName(),
					ste.getMethodName(),
					ste.getFileName(),
					ste.getLineNumber() );
		}
		throw new IllegalStateException( "Failed to find non-skipped stack element in\n  "
				+ Stream.of( stack ).map( String::valueOf ).collect( joining( "  \n" ) ) );
	}

	private static boolean isThreadDotGetStackTrace( StackTraceElement ste ) {
		return "java.lang.Thread".equals( ste.getClassName() )
				&& "getStackTrace".equals( ste.getMethodName() );
	}

	private static boolean isClassSkipped( Class<?> cls ) {
		return cls.isAnnotationPresent( SkipTrace.class );
	}

	private static boolean isPackageSkipped( Class<?> cls ) {
		return cls.getPackage().isAnnotationPresent( SkipTrace.class );
	}

	private static boolean isMethodSkipped( StackTraceElement ste, Class<?> cls ) {
		/*
		 * A method of that name in that type is tagged to be skipped, so we have to
		 * skip <i>all</i> such methods. It'd be nice if we could distinguish between
		 * overloaded methods, but: <ul> <li>The stack trace element does not tell us
		 * the method arguments</li> <li>Reflecting on the class does not give us the
		 * line numbers</li> </ul>
		 */
		String id = ste.getClassName() + "." + ste.getMethodName();

		// check if we've seen this method before
		if( skippedMethods.contains( id ) ) {
			return true;
		}

		// populate all skipped methods in the class into our cache
		Stream.of( cls.getDeclaredMethods() )
				.filter( m -> m.isAnnotationPresent( SkipTrace.class ) )
				.map( m -> m.getDeclaringClass().getName() + "." + m.getName() )
				.forEach( skippedMethods::add );

		// check the cache again
		return skippedMethods.contains( id );
		// pitest complains that it can do all manner of horrible mutations to the lines
		// above and have the tests still pass, but I can't recreate that manually
	}
}
