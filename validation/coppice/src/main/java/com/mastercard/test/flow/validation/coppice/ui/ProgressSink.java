/**
 * Copyright (c) 2020 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.coppice.ui;

/**
 * Interface for objects that can be notified of task progress
 */
public interface ProgressSink {

	/**
	 * @param name    The human-readable name of the task
	 * @param stage   The human-readable name for the current stage of the task
	 * @param current How many stages of the task are complete, or -1 for
	 *                indeterminate
	 * @param total   How many stages exist in the task
	 */
	void update( String name, String stage, int current, int total );
}
