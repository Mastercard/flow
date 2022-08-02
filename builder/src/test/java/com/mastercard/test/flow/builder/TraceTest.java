package com.mastercard.test.flow.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.builder.skipped.TypeInSkippedPackage;

/**
 * Exercises {@link Trace} behaviour
 */
@SuppressWarnings("static-method")
class TraceTest {

	/**
	 * Location is <i>right there!</i>
	 */
	@Test
	void simple() {
		Assertions.assertEquals( "com.mastercard.test.flow.builder.TraceTest.simple(TraceTest.java:29)",
				Trace.trace() );
	}

	/**
	 * Location is in another method
	 */
	@Test
	void method() {
		Assertions.assertEquals( "com.mastercard.test.flow.builder.TraceTest.traced(TraceTest.java:42)",
				traced() );
	}

	private String traced() {
		return Trace.trace();
	}

	/**
	 * Location is in another method that is being skipped
	 */
	@Test
	void skippedMethod() {
		Assertions.assertEquals(
				"com.mastercard.test.flow.builder.TraceTest.skippedMethod(TraceTest.java:52)",
				skipped() );
	}

	@SkipTrace
	private String skipped() {
		return Trace.trace();
	}

	/**
	 * Methods are skipped if they have the same name as a {@link SkipTrace} method,
	 * even if their signatures are distinct
	 *
	 * @throws Exception if we fail to purge static data
	 */
	@Test
	void skipMethodSameName() throws Exception {
		Assertions.assertEquals(
				"com.mastercard.test.flow.builder.TraceTest.skipMethodSameName(TraceTest.java:70)",
				skipped( true ) );

		clearSkippedMethods();

		Assertions.assertEquals(
				"com.mastercard.test.flow.builder.TraceTest.skipMethodSameName(TraceTest.java:76)",
				skipped( true ) );
	}

	private String skipped( @SuppressWarnings("unused") boolean sameNameAsSkippedMethod ) {
		return Trace.trace();
	}

	/**
	 * Location is in another type that is being skipped
	 */
	@Test
	void skippedType() {
		Assertions.assertEquals(
				"com.mastercard.test.flow.builder.TraceTest.skippedType(TraceTest.java:90)",
				SkippedType.trace() );
	}

	@SkipTrace
	private static class SkippedType {
		public static String trace() {
			return Trace.trace();
		}
	}

	/**
	 * Location is in another package that is being skipped
	 */
	@Test
	void skippedPackage() {
		Assertions.assertEquals(
				"com.mastercard.test.flow.builder.TraceTest.skippedPackage(TraceTest.java:107)",
				TypeInSkippedPackage.trace() );
	}

	/**
	 * Bumps pitest coverage
	 *
	 * @throws Exception reflection failure
	 */
	@Test
	void constructor() throws Exception {
		// pitest complains that we don't exercise the private constructor, so...
		Constructor<Trace> c = Trace.class.getDeclaredConstructor();
		assertTrue( Modifier.isPrivate( c.getModifiers() ) );
		c.setAccessible( true );
		c.newInstance();
		c.setAccessible( false );
	}

	/**
	 * Shows what happens when the stacktrace contains a non-existent class. Not
	 * quite sure how that's supposed to happen in reality - perhaps mockito's dark
	 * magics?
	 */
	@Test
	void badClass() {
		try {
			Trace.tracer( () -> new StackTraceElement[] {
					new StackTraceElement( "NonExistentClass", "method", "file", 1 ) } );
			String msg = assertThrows( IllegalStateException.class, Trace::trace ).getMessage();
			assertEquals( "Failed to load class for NonExistentClass.method(file:1)", msg );
		}
		finally {
			Trace.tracer( null );
		}
	}

	/**
	 * Shows what happens when all elements of the stacktrace are skipped over
	 */
	@Test
	void skippedTrace() {
		try {
			Trace.tracer( () -> new StackTraceElement[] {
					new StackTraceElement( "java.lang.Thread", "getStackTrace", "file", 1 ) } );
			String msg = assertThrows( IllegalStateException.class, Trace::trace ).getMessage();
			assertEquals( ""
					+ "Failed to find non-skipped stack element in\n"
					+ "  java.lang.Thread.getStackTrace(file:1)", msg );
		}
		finally {
			Trace.tracer( null );
		}
	}

	/**
	 * PIT minions, surprisingly, appear to share a classloader. Hence one minion
	 * can populate the skipped method set, then another minion can complain that
	 * the code that achieves that can be mutated but the test still passes as the
	 * set is already populated
	 *
	 * @throws Exception field access failure
	 */
	static void clearSkippedMethods() throws Exception {
		Field smf = Trace.class.getDeclaredField( "skippedMethods" );
		smf.setAccessible( true );
		((Set<?>) smf.get( null )).clear();
	}
}
