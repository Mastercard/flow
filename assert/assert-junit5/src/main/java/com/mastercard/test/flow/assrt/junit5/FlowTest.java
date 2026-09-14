package com.mastercard.test.flow.assrt.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Registers the discovery-time Flow mode and factory-local
 * {@link FlowExecution}. No Launcher service or test-base inheritance is
 * required for serial execution.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@TestMethodOrder(FlowMethodOrderer.class)
@ExtendWith(FlowExtension.class)
public @interface FlowTest {
	// Composed registration.
}
