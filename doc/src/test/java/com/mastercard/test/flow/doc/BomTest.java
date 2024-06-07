package com.mastercard.test.flow.doc;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.doc.PomData.DepData;

/**
 * Ensures that our Bill of Materials pom contains all of the artifacts in this
 * project
 */
class BomTest {

	@Test
	void check() {
		PomData root = new PomData( null, Paths.get( "..", "pom.xml" ) );

		PomData bom = root.modules()
				.filter( m -> "bom".equals( m.artifactId() ) )
				.findAny()
				.orElseThrow( () -> new IllegalStateException( "Failed to find bom" ) );

		List<PomData> artifacts = collectArtifacts( root, new ArrayList<>() );

		// these are not releasable artifacts, so no point in managing them
		artifacts.removeIf( pom -> pom.groupId().endsWith( "example" )
				|| "aggregator".equals( pom.artifactId() )
				|| "doc".equals( pom.artifactId() ) );
		// no self-reference please
		artifacts.remove( bom );

		String expected = artifacts.stream()
				.sorted( Comparator.comparing( PomData::artifactId ) )
				.map( artifact -> String.format( ""
						+ "\t\t\t<dependency>\n"
						+ "\t\t\t\t<groupId>%s</groupId>\n"
						+ "\t\t\t\t<artifactId>%s</artifactId>\n"
						+ "\t\t\t\t<version>%s</version>\n"
						+ "\t\t\t</dependency>",
						artifact.groupId(), artifact.artifactId(), artifact.version() ) )
				.collect( joining(
						"\n\n",
						""
								+ "\t<dependencyManagement>\n"
								+ "\t\t<dependencies>\n",
						""
								+ "\n"
								+ "\t\t</dependencies>\n"
								+ "\t</dependencyManagement>" ) );

		String actual = bom.dependencyManagement()
				.sorted( Comparator.comparing( DepData::artifactId ) )
				.map( dep -> String.format( ""
						+ "\t\t\t<dependency>\n"
						+ "\t\t\t\t<groupId>%s</groupId>\n"
						+ "\t\t\t\t<artifactId>%s</artifactId>\n"
						+ "\t\t\t\t<version>%s</version>\n"
						+ "\t\t\t</dependency>",
						dep.groupId(), dep.artifactId(), dep.version() ) )
				.collect( joining(
						"\n\n",
						""
								+ "\t<dependencyManagement>\n"
								+ "\t\t<dependencies>\n",
						""
								+ "\n"
								+ "\t\t</dependencies>\n"
								+ "\t</dependencyManagement>" ) );

		assertEquals( expected, actual );
	}

	private static List<PomData> collectArtifacts( PomData pom, List<PomData> modules ) {
		if( "jar".equals( pom.packaging() ) ) {
			modules.add( pom );
		}
		pom.modules().forEach( child -> collectArtifacts( child, modules ) );
		return modules;
	}
}
