package com.mastercard.test.flow.assrt;

import java.util.function.BiFunction;

/**
 * Customizes the motivation text in the report.
 */
public interface MotivationCustomizer extends BiFunction<String, Assertion, String> {
}
