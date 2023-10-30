package com.mastercard.test.flow.report.duct;

import static com.mastercard.test.flow.report.FailureSink.SILENT;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.mastercard.test.flow.report.FailureSink;
import com.mastercard.test.flow.report.LocalBrowse;

/**
 * Encapsulates the details of spawning a new JVM in which to run a {@link Duct}
 * instance
 */
class Spawn {

	private Spawn() {
		// no instances
	}

	/**
	 * Launches a new JVM, running {@link Duct#main(String...)}, to which a report
	 * path is supplied
	 *
	 * @param report The report that we wishto new {@link Duct} instance to serve
	 * @param debug  What to do with failure diagnostics
	 */
	static void launchFor( Path report, FailureSink debug ) {
		// we'll have to spawn our own instance
		ProcessBuilder pb = new ProcessBuilder( Stream.of(
				"java",
				// Immediately after launch duct will try to open a browser, so we need to clone
				// these browser-opening property in the new JVM
				LocalBrowse.SUPPRESS.commandLineArgument(),
				LocalBrowse.XDG_OPEN_FALLBACK.commandLineArgument(),
				// re-use the current JVM's classpath. It's running this class, so it should
				// also have the dependencies we need. The classpath will be bigger than duct
				// strictly needs, but the cost of that is negligible
				"-cp", getClassPath(),
				// invoke this class's main method
				Duct.class.getName(),
				// pass the report path on the commandline - the above main method will take
				// care of adding and browsing it
				report.toAbsolutePath().toString() )
				.filter( Objects::nonNull )
				.collect( toList() ) );

		launch( pb, debug );
	}

	/**
	 * Launches a process, and if our {@link #DEBUG} is doing anything, pipes the
	 * stdout content from that process intto it
	 *
	 * @param pb The process to launch
	 */
	private static final void launch( ProcessBuilder pb, FailureSink debug ) {
		if( debug != SILENT ) {
			pb.redirectErrorStream( true );
		}
		try {
			// this process will persist after the demise of the current JVM
			Process p = pb.start();

			if( debug != SILENT ) {
				Thread t = new Thread( () -> {
					try( InputStreamReader isr = new InputStreamReader( p.getInputStream() );
							BufferedReader br = new BufferedReader( isr ) ) {
						String line = null;
						while( (line = br.readLine()) != null ) {
							debug.log( "duct launch stdout : {}", line );
						}

						debug.log( "duct stdout ended! Command was:" );
						debug.log( pb.command().stream().collect( joining( " " ) ) );
						debug.log( "exit code {}", p.exitValue() );
					}
					catch( Exception e ) {
						debug.log( "duct stdout capture failure!", e );
					}
				}, "stream printer" );
				t.setDaemon( true );
				t.start();
			}
		}
		catch( IOException e ) {
			debug.log( "Failed to launch:\n{}",
					pb.command().stream().collect( joining( " " ) ),
					e );
		}
	}

	/**
	 * Attempts to gets the classpath of the current JVM. You would think that you
	 * could just use the <code>java.class.path</code> system property, but when
	 * running tests via maven (a really common use-case for us) <a href=
	 * "https://cwiki.apache.org/confluence/display/MAVEN/Maven+3.x+Class+Loading">that
	 * just contains <code>plexus-classworlds.jar</code></a>, which is no use to us.
	 * Hence we're reduced to this: ascending the classloader chain and pulling out
	 * any URL that we find. This is all <i>highly</i> platform and VM-dependent
	 * stuff, it's <i>horribly fragile</i>.
	 *
	 * @return The classpath of the current JVM
	 */
	private static String getClassPath() {
		List<URL> urls = new ArrayList<>();
		ClassLoader cl = Duct.class.getClassLoader();
		while( cl != null ) {
			if( cl instanceof URLClassLoader ) {
				Collections.addAll( urls, ((URLClassLoader) cl).getURLs() );
			}
			cl = cl.getParent();
		}
		String cp = urls.stream()
				.map( URL::getPath )
				.collect( joining( File.pathSeparator ) );

		if( cp.isEmpty() ) {
			// The complicated thing didn't work. Let's try the simple thing!
			cp = System.getProperty( "java.class.path" );
		}

		return cp;
	}
}
