package com.mastercard.test.flow.doc;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Maintains the angular component-usage diagram in the report-ng readme
 */
@SuppressWarnings("static-method")
class AngularComponentStructureTest {
	private static final Path MODULE_ROOT = Paths.get( "../report/report-ng" );
	private static final Path APP_ROOT = MODULE_ROOT.resolve( "projects/report/src/app" );
	private static final Path APP_COMPONENT = APP_ROOT.resolve( "app.component.html" );
	private static final Pattern COMPONENT_USAGE = Pattern.compile( "<app-([a-z-]+)[ >]" );

	/**
	 * Spiders over the component HTML files and regenerates the structure diagram
	 * in the readme. If the file is changed by that then the test fails.
	 *
	 * @throws Exception on IO failure
	 */
	@Test
	void diagram() throws Exception {
		Map<String, Set<String>> usage = new TreeMap<>();

		Deque<String> toExplore = new ArrayDeque<>();
		// prime the pump with the root component
		String html = new String( QuietFiles.readAllBytes( APP_COMPONENT ), UTF_8 );
		extractUsages( "app", html, usage, toExplore );

		// manually add the routed components - these links are not apparent in the html
		addUsage( "index-route", "model-diff", usage, toExplore );
		addUsage( "index-route", "index", usage, toExplore );

		while( !toExplore.isEmpty() ) {
			String component = toExplore.poll();
			html = new String( QuietFiles.readAllBytes( APP_ROOT
					.resolve( component )
					.resolve( component + ".component.html" ) ), UTF_8 );
			extractUsages( component, html, usage, toExplore );
		}

		StringBuilder mermaid = new StringBuilder( "```mermaid\ngraph LR" );
		usage.forEach( ( source, sinks ) -> sinks.forEach( sink -> mermaid
				.append( "\n  " )
				.append( source )
				.append( " --> " )
				.append( sink ) ) );
		mermaid.append( "\n```" );

		Util.insert( MODULE_ROOT.resolve( "README.md" ),
				"<!-- start_component_structure -->",
				existing -> mermaid.toString(),
				"<!-- end_component_structure -->" );
	}

	private static void extractUsages( String component, String html, Map<String, Set<String>> usage,
			Deque<String> toExplore ) {
		Matcher m = COMPONENT_USAGE.matcher( html );
		while( m.find() ) {
			addUsage( component, m.group( 1 ), usage, toExplore );
		}
	}

	private static void addUsage( String user, String used, Map<String, Set<String>> usage,
			Deque<String> toExplore ) {
		usage.computeIfAbsent( user, c -> new TreeSet<>() ).add( used );
		toExplore.add( used );
	}

}
