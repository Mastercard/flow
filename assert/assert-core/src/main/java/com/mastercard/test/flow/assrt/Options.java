package com.mastercard.test.flow.assrt;

import java.util.function.Consumer;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.filter.FilterOptions;
import com.mastercard.test.flow.util.Option;

/**
 * Options for {@link Flow} assertion behaviour
 */
public enum Options implements Option {

	/**
	 * Controls where the name under which the execution report is saved
	 */
	REPORT_NAME(b -> b
			.property( "mctf.report.name" )
			.description( "The path from the artifact directory to the report destination" )),

	/**
	 * Controls the directory where execution reports and filter configuration is
	 * written. This is just an alias for {@link FilterOptions#ARTIFACT_DIR}.
	 */
	ARTIFACT_DIR(FilterOptions.ARTIFACT_DIR),

	/**
	 * Controls {@link Replay} parameters
	 */
	REPLAY(b -> b
			.property( "mctf.replay" )
			.description(
					"The location of a report to replay, or `" + Replay.LATEST
							+ "` to replay the most recent local report" )),

	/**
	 * Controls whether we abandon a flow on the first assertion failure or soldier
	 * on to the end
	 */
	SUPPRESS_ASSERTION_FAILURE(b -> b
			.property( "mctf.suppress.assertion" )
			.description( "Set to `true` to continue processing a flow "
					+ "in the face of assertion failure" )),

	/**
	 * Controls whether we skip a flow if the system under test cannot satisfy the
	 * declared system dependencies
	 */
	SUPPRESS_SYSTEM_CHECK(b -> b
			.property( "mctf.suppress.system" )
			.description( "Set to `true` to process when the system "
					+ "under test lacks declared dependencies" )),

	/**
	 * Controls whether we skip a flow if its basis flow has failed
	 */
	SUPPRESS_BASIS_CHECK(b -> b
			.property( "mctf.suppress.basis" )
			.description( "Set to `true` to process flows whose basis "
					+ "flows have suffered assertion failure" )),

	/**
	 * Controls whether we skip a flow if any of its dependencies failed to execute
	 */
	SUPPRESS_DEPENDENCY_CHECK(b -> b
			.property( "mctf.suppress.dependency" )
			.description( "Set to `true` to process flows whose "
					+ "dependency flows have suffered errors" )),

	;

	private final Option delegate;

	Options( Consumer<Builder> def ) {
		Builder b = new Builder();
		def.accept( b );
		delegate = b;
	}

	Options( Option delegate ) {
		this.delegate = delegate;
	}

	@Override
	public String description() {
		return delegate.description();
	}

	@Override
	public String property() {
		return delegate.property();
	}

	@Override
	public String defaultValue() {
		return delegate.defaultValue();
	}

}
