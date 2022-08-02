package com.mastercard.test.flow.builder;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply this to methods, types and packages that should be skipped over when
 * walking up the stack to find an effective call location
 *
 * @see Trace#trace()
 */
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.PACKAGE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SkipTrace {
	// marker annotation
}
