package com.mastercard.test.flow.assrt;

import static java.time.Instant.now;
import static java.time.ZoneId.systemDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.duct.Duct;

/**
 * The execution report of one run: created on first use, updated as flows are
 * processed, presented when configured to be, and closed once. Under final-only
 * reporting an ordinary fault latches the report rather than failing the test;
 * otherwise faults propagate to the flow being processed, as they always have.
 */
final class ReportLifecycle {

	private static final Supplier<String> RUN_DATETIME = () -> DateTimeFormatter
			.ofPattern( "yyMMdd-HHmmss" )
			.format( now().atZone( systemDefault() ) );

	/** Configuration as it stands when the report is used */
	private final Supplier<FlowConfiguration> config;
	private Writer report;
	private RuntimeException failure;
	private boolean error;
	private boolean closed;

	/**
	 * @param config Supplies the current configuration
	 */
	ReportLifecycle( Supplier<FlowConfiguration> config ) {
		this.config = config;
	}

	/**
	 * Applies an update to the report, creating it if necessary
	 *
	 * @param data  The update
	 * @param error Whether the update records an error condition, which may warrant
	 *              presenting the report
	 */
	void update( Consumer<Writer> data, boolean error ) {
		FlowConfiguration cfg = config.get();
		if( !cfg.reporting.writing() || failed() )
			return;
		try {
			apply( cfg, data, error );
		}
		catch( RuntimeException e ) {
			if( !cfg.finalOnlyReporting || !Faults.ordinary( e ) )
				throw e;
			failed( e );
		}
	}

	private void apply( FlowConfiguration cfg, Consumer<Writer> data, boolean error ) {
		Path reportDir;
		Writer target;
		synchronized( this ) {
			this.error |= error;
			reportDir = null;
			if( report == null ) {
				String testTitle = cfg.title;
				Path testDir = Paths.get( AssertionOptions.ARTIFACT_DIR.value(), cfg.reportPath );

				// work out what the report directory should be called
				String name = AssertionOptions.REPORT_NAME.value();
				if( name == null ) {
					name = RUN_DATETIME.get();
				}
				if( cfg.replay.hasData() ) {
					// reports that have been generated from replaying historic data don't really
					// imply anything about the behaviour of the system under test, so we want them
					// to be really obvious. Hence we're giving them a directory name suffix and an
					// addendum to the test report title
					name += Replay.REPLAYED_SUFFIX;
					testTitle += " (replay)";
					// The dir name suffix also stops us overwriting the data source when
					// the REPORT_NAME property is the same as the REPLAY property
				}

				reportDir = testDir.resolve( name );
				report = new Writer( cfg.model.title(), testTitle, reportDir,
						cfg.finalOnlyReporting ? Writer.Indexing.FINAL_ONLY : Writer.Indexing.IMMEDIATE );
				if( !"latest".equals( reportDir.getFileName().toString() ) ) {
					linkLatest( testDir.resolve( "latest" ), reportDir );
				}
			}
			target = report;
		}

		data.accept( target );

		if( reportDir != null && !cfg.finalOnlyReporting ) {
			// We've just created a new report: if appropriate, open a browser to it.
			present( cfg, target, error );
		}
	}

	private static void present( FlowConfiguration cfg, Writer target, boolean error ) {
		if( !cfg.reporting.shouldOpen( error ) )
			return;
		try {
			if( AssertionOptions.DUCT.isTrue() ) {
				// if you've traced a ClassNotFoundException or NoClassDefFoundError to here,
				// then you've forgotten to add the duct module to your dependencies.
				Duct.serve( target.path() );
			}
			else {
				target.browse();
			}
		}
		catch( RuntimeException e ) {
			if( !cfg.finalOnlyReporting || !Faults.ordinary( e ) )
				throw e;
			Faults.diagnostic( "Report presentation failed: " + e.getClass().getName() );
		}
	}

	private synchronized boolean failed() {
		return failure != null;
	}

	private synchronized void failed( RuntimeException e ) {
		if( failure == null ) {
			failure = e;
			Faults.diagnostic( "Report failed: " + e.getClass().getName() );
		}
	}

	/** Creates the final-only report so that an empty run still publishes one */
	void initialize() {
		if( config.get().finalOnlyReporting )
			update( ignored -> {
				// no flow data to add
			}, false );
	}

	private static void linkLatest( Path linkPath, Path reportDir ) {
		try {
			boolean shouldLink;
			// Ordinary files and directories at latest may be user-owned.
			if( Files.exists( linkPath, LinkOption.NOFOLLOW_LINKS ) ) {
				if( Files.isSymbolicLink( linkPath ) ) {
					Files.delete( linkPath );
					shouldLink = true;
				}
				else {
					shouldLink = false;
				}
			}
			else {
				shouldLink = true;
			}

			if( shouldLink ) {
				Files.createSymbolicLink( linkPath, linkPath.getParent().relativize( reportDir ) );
			}
		}
		catch( @SuppressWarnings("unused") IOException ioe ) {
			// The symlink to the latest report is a nice-to-have. Some platforms (e.g.:
			// windows) restrict the ability to create symlinks so we can't count on it
			// working.
		}
	}

	/** @return The report path, or null while disabled or after creation failure */
	synchronized Path path() {
		return report == null ? null : report.path();
	}

	/**
	 * Closes the report. A final-only report is presented on its first close; a
	 * latched report is left as it is, so that its failure is not thrown again.
	 */
	void close() {
		FlowConfiguration cfg = config.get();
		Writer closing;
		boolean first;
		boolean errored;
		synchronized( this ) {
			first = !closed;
			closed = true;
			closing = failure == null ? report : null;
			errored = error;
		}
		if( closing == null ) {
			return;
		}
		try {
			closing.close();
			if( first && cfg.finalOnlyReporting )
				present( cfg, closing, errored );
		}
		catch( RuntimeException e ) {
			if( !cfg.finalOnlyReporting || !Faults.ordinary( e ) )
				throw e;
			failed( e );
		}
	}
}
