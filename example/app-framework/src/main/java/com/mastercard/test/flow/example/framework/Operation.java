package com.mastercard.test.flow.example.framework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply this to the interface methods of your application
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Operation {
	/**
	 * @return The HTTP method by which the operation is accessed
	 */
	String method();

	/**
	 * @return The URL path by which the operation is accessed. Path parameter
	 *         elements are prefixed by <code>:</code> (see {@link Path}) annotation
	 *         )
	 */
	String path();

	/**
	 * @return The value of the <code>Content-Type</code> header that will be set on
	 *         HTTP requests on this operation
	 */
	String reqContentType() default "application/json";

	/**
	 * @return The value of the <code>Content-Type</code> header that will be set on
	 *         HTTP responses from this operation
	 */
	String resContentType() default "application/json";

	/**
	 * Apply this to {@link Operation} parameters that are mapped to HTTP query
	 * parameters.
	 */
	@Target(ElementType.PARAMETER)
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Query {
		/**
		 * @return The name of the parameter
		 */
		String value();
	}

	/**
	 * Apply this to {@link Operation} parameters that are mapped to HTTP path
	 * parameters
	 */
	@Target(ElementType.PARAMETER)
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Path {
		/**
		 * @return The name of the parameter
		 */
		String value();
	}

	/**
	 * Apply this to {@link Operation} parameters that are mapped to HTTP header
	 * parameters
	 */
	@Target(ElementType.PARAMETER)
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Header {
		/**
		 * @return The name of the header
		 */
		String value();
	}

	/**
	 * Apply this to the one or zero {@link Operation} parameters that make up the
	 * HTTP request body
	 */
	@Target(ElementType.PARAMETER)
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Body {
		// marker only
	}

}
