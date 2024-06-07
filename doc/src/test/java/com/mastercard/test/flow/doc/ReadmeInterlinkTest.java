package com.mastercard.test.flow.doc;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * This test ensures that the interlinks between module readmes accurately
 * reflect the POM structure
 */
class ReadmeInterlinkTest {

	private static final String TITLE_START = "<!-- title start -->";
	private static final String TITLE_END = "<!-- title end -->";

	/**
	 * Spiders over the pom structure and regenerates the title sections of
	 * associated readme files. If this test fails it's because that regeneration
	 * made a change. It should pass if you just run it again, but do be sure to
	 * check the changes are desired before you commit them.
	 *
	 * @return Test cases to regenerate readme interlinks
	 */
	@TestFactory
	DynamicContainer titles() {
		return fromPom( new PomData( null, Paths.get( "../pom.xml" ) ) );
	}

	private DynamicContainer fromPom( PomData pom ) {
		DynamicTest readme = dynamicTest( "title", () -> {
			Util.insert( pom.dirPath().resolve( "README.md" ),
					TITLE_START,
					existing -> compliant( existing, pom ) ? existing : title( pom ),
					TITLE_END );
		} );
		return dynamicContainer( pom.artifactId(),
				Stream.concat( Stream.of( readme ),
						pom.modules().map( this::fromPom ) ) );
	}

	private static boolean compliant( String section, PomData pom ) {
		return section.contains( pom.name() )
				&& section.contains( pom.description() )
				&& section.contains( parentLink( pom ) )
				&& section.contains( javadocBadge( pom ) )
				&& childLinks( pom )
						.map( String::trim )
						.allMatch( section::contains );
	}

	private static String parentLink( PomData pom ) {
		if( pom.parent() == null ) {
			// root readme, nowhere to go
			return "";
		}
		if( pom.parent().parent() == null ) {
			// 1st-level, github has a weird thing where just linking to `..` and hoping for
			// the root page results in a 404. It works fine on stash ¯\_(ツ)_/¯
			return String.format( "\n * [../%s](https://github.com/Mastercard/flow) %s",
					pom.parent().name(), pom.parent().description() );
		}
		// general case, works on 2nd level and, presumably, beyond
		return String.format( "\n * [../%s](..) %s",
				pom.parent().name(), pom.parent().description() );
	}

	private static final Set<String> NO_JAVADOC = Stream.of(
			"report-ng", "doc", "aggregator",
			"app", "app-framework", "app-api", "app-web-ui", "app-ui", "app-core", "app-histogram",
			"app-queue", "app-store", "app-model", "app-assert", "app-itest" )
			.collect( toSet() );

	private static String javadocBadge( PomData pom ) {
		if( "jar".equals( pom.packaging() ) && !NO_JAVADOC.contains( pom.artifactId() ) ) {
			return String.format( ""
					+ "["
					+ "![javadoc](https://javadoc.io/badge2/%s/%s/javadoc.svg)"
					+ "]"
					+ "(https://javadoc.io/doc/%s/%s)",
					pom.groupId(), pom.artifactId(), pom.groupId(), pom.artifactId() );
		}

		return "";
	}

	private static Stream<String> childLinks( PomData pom ) {
		return pom.modules()
				.map( child -> String.format( " * [%s](%s) %s\n",
						child.name(),
						pom.dirPath().relativize( child.dirPath() ),
						child.description() ) );
	}

	private static String title( PomData pom ) {
		return String.format( ""
				+ "# %s\n" // name
				+ "\n"
				+ "%s\n" // description
				+ "\n"
				+ "%s\n" // javadoc badge
				+ "%s\n" // parent link
				+ "%s", // child links
				pom.name(),
				pom.description(),
				javadocBadge( pom ),
				parentLink( pom ),
				childLinks( pom ).collect( joining() ) )
				.trim();
	}
}
