package com.mastercard.test.flow;

/**
 * <p>
 * Represents those components of a system that are unattractive to reliably
 * predict. Examples include:
 * </p>
 * <dl>
 * <dt>Random number generators</dt>
 * <dd>UUIDs, cryptographic content</dd>
 * <dt>Clocks</dt>
 * <dd>Dates, times</dd>
 * <dt>Human input</dt>
 * <dd>We are a malicious species</dd>
 * <dt>System state</dt>
 * <dd>Incrementing counters, historic data, IP addresses</dd>
 * <dt>Boring stuff</dt>
 * <dd>Things like message length headers that would be tedious to accurately
 * model and that we have good confidence won't break in difficult-to-diagnose
 * ways</dd>
 * </dl>
 * <p>
 * The sources of unpredictability can be supplied to {@link Message} instances
 * when assertable content is requested. The {@link Message} implementation thus
 * has the opportunity to mask out those fields that cannot be asserted on.
 *
 * @see Message#assertable(Unpredictable...)
 */
public interface Unpredictable {
	/**
	 * Defines a unique human-readable name for the source of unpredictability
	 *
	 * @return The name of this source of unpredictable content
	 */
	String name();
}
