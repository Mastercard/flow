package com.mastercard.test.flow.assrt;

/**
 * Finite limits on retained correlated log capture. The first events accepted
 * are retained; excess is dropped and counted visibly. The run limits cover
 * everything the run retains, including late and unattributed diagnostics, and
 * are not reset when a flow ends: verbose early flows can exhaust the budget
 * for later ones.
 * <p>
 * Bytes are approximated as the total character count of an event's fields. The
 * defaults are deliberate finite choices, not measured recommendations for any
 * particular workload.
 *
 * @param flowEvents  Maximum events retained per flow
 * @param flowBytes   Maximum bytes retained per flow
 * @param runEvents   Maximum events retained across the run
 * @param runBytes    Maximum bytes retained across the run
 * @param recordBytes Maximum size of one event's message; longer messages are
 *                    truncated with a visible marker
 */
public record CaptureBudget(int flowEvents, long flowBytes, int runEvents, long runBytes,
		int recordBytes) {

	/**
	 * 2,000 events / 1 MiB per flow; 50,000 events / 64 MiB per run; 64 KiB records
	 */
	public static final CaptureBudget DEFAULT = new CaptureBudget(
			2_000, 1L << 20, 50_000, 64L << 20, 64 << 10 );

	/**
	 * @param flowEvents  Maximum events retained per flow
	 * @param flowBytes   Maximum bytes retained per flow
	 * @param runEvents   Maximum events retained across the run
	 * @param runBytes    Maximum bytes retained across the run
	 * @param recordBytes Maximum size of one event's message
	 */
	public CaptureBudget {
		if( flowEvents < 1 || flowBytes < 1 || runEvents < 1 || runBytes < 1 || recordBytes < 1 ) {
			throw new IllegalArgumentException( "Capture budgets must be positive" );
		}
	}
}
