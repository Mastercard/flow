package com.mastercard.test.flow.assrt;

import java.util.function.BiFunction;

/**
 * Customizes the motivation text in the report. Under concurrent execution one
 * instance is invoked from several threads at once, so implementations must be
 * thread-safe.
 */
public interface MotivationCustomizer extends BiFunction<String, Assertion, String> {
}
