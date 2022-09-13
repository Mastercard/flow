package com.mastercard.test.flow.assrt.filter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.util.Option;

/**
 * Options for {@link Flow} selection behaviour
 */
public enum FilterOptions implements Option {

	/**
	 * Populates {@link Filter#excludedTags(java.util.Set)}
	 */
	EXCLUDE_TAGS(b -> b
			.property( "mctf.filter.exclude" )
			.description( "A comma-separated list of tags values that flows must not have" )),

	/**
	 * Populates {@link Filter#includedTags(java.util.Set)}
	 */
	INCLUDE_TAGS(b -> b
			.property( "mctf.filter.include" )
			.description( "A comma-separated list of tags values that flows must have" )),

	/**
	 * Populates {@link Filter#indices(java.util.Set)}
	 */
	INDICES(b -> b
			.property( "mctf.filter.indices" )
			.description( "A comma-separated list of indices and index ranges for flows to process" )),

	/**
	 * Determines if the flow {@link Filter} CLI is shown
	 */
	FILTER_UPDATE(b -> b
			.property( "mctf.filter.update" )
			.description( ""
					+ "Supply `true` to update filter values at runtime in the most appropriate interface."
					+ "Supply `cli` to force use of the command-line interface "
					+ "or `gui` to force use of the graphical interface" )),

	/**
	 * Determines if the previous run's {@link Filter} configuration is restored
	 */
	FILTER_REPEAT(b -> b
			.property( "mctf.filter.repeat" )
			.description( "Supply `true` to use the previous filters again" )),

	/**
	 * Determines if the filters should be configured according to historic
	 * assertion results
	 */
	FILTER_FAILS(b -> b
			.property( "mctf.filter.fails" )
			.description(
					"Configures filters to repeat flows that did not pass assertion in a previous run. "
							+ "Supply the location of a report from which to extract results,"
							+ " or `" + Filter.LATEST + "` to extract from the most recent local report" )),

	/**
	 * Controls the directory where execution reports and filter configuration is
	 * written
	 */
	ARTIFACT_DIR(b -> b
			.property( "mctf.dir" )
			.description( "The path to the dir where assertion artifacts are saved" )
			.defaultValue( buildDir() )),

	/**
	 * Controls the width of the filter CLI
	 */
	CLI_MIN_WIDTH(b -> b
			.property( "mctf.filter.cli.min_width" )
			.description( "The minimum width of the command-line interface" )
			.defaultValue( "80" )),

	;

	private final Option delegate;

	FilterOptions( Consumer<Builder> def ) {
		Builder b = new Builder();
		def.accept( b );
		delegate = b;
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

	private static String buildDir() {
		// look for maven or gradle style
		return Stream.of( "build", "target" )
				.filter( s -> Files.exists( Paths.get( s ) ) )
				.findFirst()
				.map( s -> s + "/mctf" )
				// oh well, assume maven
				.orElse( "target/mctf" );
	}
}
