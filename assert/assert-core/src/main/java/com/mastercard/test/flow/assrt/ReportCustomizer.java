package com.mastercard.test.flow.assrt;

import com.mastercard.test.flow.report.data.FlowData;

/**
 * Interface for customizing the report data before it is written to storage.
 * Implementations of this interface can modify the {@link FlowData#motivation}
 * and assertion to add additional information, such as links to external logs.
 */
public interface ReportCustomizer {
	/**
	 * Customizes the report data. Called after the flow has been processed and
	 * before the report is written to storage.
	 * 
	 * @param flowData  The data of the flow being reported.
	 * @param assertion The assertion related to the flow.
	 */
	default void customizeReport( FlowData flowData, Assertion assertion ) {
		// no-op
	}

}
