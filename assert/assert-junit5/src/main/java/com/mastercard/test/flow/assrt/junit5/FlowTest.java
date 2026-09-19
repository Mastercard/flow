package com.mastercard.test.flow.assrt.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Supplies a factory-local {@link FlowExecution} to a {@code @TestFactory}
 * parameter and closes its report when the class finishes. Enable standard
 * Jupiter parallel execution to run independent flows concurrently; nothing
 * Flow-specific needs configuring.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(FlowExtension.class)
public @interface FlowTest {
	// Composed registration.
}
