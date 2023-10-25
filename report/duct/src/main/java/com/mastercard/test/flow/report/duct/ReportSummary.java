package com.mastercard.test.flow.report.duct;

import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.Meta;

/**
 * Summary of content from about a served report
 */
public class ReportSummary {

	/**
	 * The report metadata
	 */
	@JsonProperty("meta")
	public final Meta meta;
	/**
	 * Summary of {@link Flow} assertion outcome
	 */
	@JsonProperty("counts")
	public final Counts counts;
	/**
	 * The path portion of the report's URL
	 */
	@JsonProperty("path")
	public final String path;

	/**
	 * @param meta   The report metadata
	 * @param counts Summary of {@link Flow} assertion outcome
	 * @param path   The path portion of the report's URL
	 */
	public ReportSummary(
			@JsonProperty("meta") Meta meta,
			@JsonProperty("counts") Counts counts,
			@JsonProperty("path") String path ) {
		this.meta = meta;
		this.counts = counts;
		this.path = path;
	}

	/**
	 * @param index The index to summarise
	 * @param path  The path where the index was read from
	 */
	public ReportSummary( Index index, String path ) {
		this( index.meta, new Counts( index ), path );
	}

	/**
	 * Summary of {@link Flow} assertion outcome
	 */
	public static class Counts {
		/**
		 * The count of flows that passed assertion
		 */
		@JsonProperty("pass")
		public final int pass;
		/**
		 * The count of flows that failed assertion
		 */
		@JsonProperty("fail")
		public final int fail;
		/**
		 * The count of flows that were skipped
		 */
		@JsonProperty("skip")
		public final int skip;
		/**
		 * The count of flows that suffered execution errors
		 */
		@JsonProperty("error")
		public final int error;

		/**
		 * @param pass  The count of flows that passed assertion
		 * @param fail  The count of flows that failed assertion
		 * @param skip  The count of flows that were skipped
		 * @param error The count of flows that suffered execution errors
		 */
		public Counts(
				@JsonProperty("pass") int pass,
				@JsonProperty("fail") int fail,
				@JsonProperty("skip") int skip,
				@JsonProperty("error") int error ) {
			this.pass = pass;
			this.fail = fail;
			this.skip = skip;
			this.error = error;
		}

		/**
		 * @param index The index to extract the counts from
		 */
		public Counts( Index index ) {
			Function<String, Integer> counter = tag -> (int) index.entries.stream()
					.filter( e -> e.tags.contains( tag ) ).count();
			pass = counter.apply( Writer.PASS_TAG );
			fail = counter.apply( Writer.FAIL_TAG );
			skip = counter.apply( Writer.SKIP_TAG );
			error = counter.apply( Writer.ERROR_TAG );
		}
	}
}
