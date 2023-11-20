
package com.mastercard.test.flow.doc;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Ensures that the project diagram in the main readme accurately reflects the
 * POM structure. Spiders over the pom structure and regenerates the mermaid
 * diagrams. These tests will fail if that regeneration makes any changes. It
 * should pass if you just run it again, but do be sure to check the changes are
 * desired before you commit them.
 */
@SuppressWarnings("static-method")
class ModuleDiagramTest {

	/**
	 * Projects that aren't really interesting but that stink up the diagram with
	 * all their links
	 */
	private static final Set<String> OCCULTED = Stream.of(
			"com.mastercard.test.flow:aggregator",
			"com.mastercard.test.flow:doc" ).collect( toSet() );

	/**
	 * Maps from the groupID to the scope of links that we're interested in within
	 * that group.
	 */
	private static final Map<String, Set<String>> SCOPES;
	static {
		Map<String, Set<String>> m = new HashMap<>();
		// For the framework we want to show *what* will be included transitively if you
		// consume an artifact. We've got some test-scope dependencies in here to
		// avoid excessive mocking, but they just cloud the diagram.
		m.put( "com.mastercard.test.flow", Stream.of( "compile" ).collect( toSet() ) );
		// For the example service it's important to show *how* the framework
		// dependencies are consumed - e.g. assert and validation are always test scope,
		// the model doesn't make it into the distributable artifacts, etc
		m.put( "com.mastercard.test.flow.example", Stream.of( "compile", "test" ).collect( toSet() ) );
		SCOPES = Collections.unmodifiableMap( m );
	}

	/**
	 * Regenerates the diagram in the main readme
	 *
	 * @throws Exception if insertion fails
	 */
	@Test
	void framework() throws Exception {
		Util.insert( Paths.get( "../README.md" ),
				"<!-- start_module_diagram:framework -->",
				s -> diagram( "TB", "com.mastercard.test.flow" ),
				"<!-- end_module_diagram -->" );
	}

	/**
	 * Regerenates the diagram in the example system readme
	 *
	 * @throws Exception if insertion fails
	 */
	@Test
	void example() throws Exception {
		Util.insert( Paths.get( "../example/README.md" ),
				"<!-- start_module_diagram:example -->",
				s -> diagram( "LR",
						"com.mastercard.test.flow",
						"com.mastercard.test.flow.example" ),
				"<!-- end_module_diagram -->" );
	}

	private static String diagram( String orientation, String... groupIDs ) {
		Arrays.sort( groupIDs );
		PomData root = new PomData( null, Paths.get( "../pom.xml" ) );

		Set<String> artifacts = new HashSet<>();
		root.visit( pd -> artifacts.add( pd.coords() ) );
		artifacts.removeAll( OCCULTED );

		// from groupId to list of artifacts in that group
		Map<String, Set<PomData>> groups = new TreeMap<>();
		root.visit( pd -> groups
				.computeIfAbsent( pd.groupId(), g -> new TreeSet<>( comparing( PomData::artifactId ) ) )
				.add( pd ) );
		groups.keySet().removeIf( g -> Arrays.binarySearch( groupIDs, g ) < 0 );

		// from artifact coordinate to list of dependents of that artifact
		Map<String, List<Link>> links = new HashMap<>();
		root.visit( pd -> pd.dependencies()
				.filter( dd -> !dd.optional() )
				.filter( dd -> artifacts.contains( dd.coords() ) )
				.filter( dd -> artifacts.contains( pd.coords() ) )
				.filter( dd -> SCOPES.get( pd.groupId() ).contains( dd.scope() ) )
				.forEach( dd -> links
						.computeIfAbsent( dd.coords(), g -> new ArrayList<>() )
						.add( new Link(
								dd.groupId(),
								dd.artifactId(),
								"compile".equals( dd.scope() )
										? " --> "
										: " -.-> ",
								pd.groupId(),
								pd.artifactId() ) ) ) );

		// remove links that don't fit in the requested groups
		links.values().forEach( ll -> ll.removeIf( l -> !l.within( groupIDs ) ) );

		// remove artifacts with no dependencies (e.g.: parent poms
		groups.values().forEach( arts -> arts.removeIf(
				pd -> !links.values().stream()
						.map( ll -> ll.stream()
								.filter( l -> l.involves( pd ) )
								.findAny() )
						.filter( Optional::isPresent )
						.map( Optional::get )
						.findAny().isPresent() ) );

		StringBuilder mermaid = new StringBuilder( "```mermaid\ngraph " )
				.append( orientation )
				.append( "\n" );

		groups.forEach( ( groupId, pomdatas ) -> {
			mermaid.append( "  subgraph " ).append( groupId ).append( "\n" );
			pomdatas.stream()
					.sorted( comparing( PomData::artifactId ) )
					.forEach( pd -> mermaid.append( "    " ).append( pd.artifactId() ).append( "\n" ) );
			mermaid.append( "  end\n" );
		} );

		links.values().stream()
				.flatMap( List::stream )
				.sorted( Comparator.comparing( Link::fromArtifactId ) )

				.forEach( l -> mermaid.append( l ) );

		mermaid.append( "```" );
		return mermaid.toString();
	}

	private static class Link {

		private final String fromGroup;
		private final String fromArtifact;
		private final String type;
		private final String toGroup;
		private final String toArtifact;

		protected Link( String fromGroup, String fromArtifact, String type, String toGroup,
				String toArtifact ) {
			this.fromGroup = fromGroup;
			this.fromArtifact = fromArtifact;
			this.type = type;
			this.toGroup = toGroup;
			this.toArtifact = toArtifact;
		}

		/**
		 * @param pd An artifact
		 * @return <code>true</code> if this link involves that artifact
		 */
		public boolean involves( PomData pd ) {
			return fromGroup.equals( pd.groupId() ) && fromArtifact.equals( pd.artifactId() )
					|| toGroup.equals( pd.groupId() ) && toArtifact.equals( pd.artifactId() );
		}

		/**
		 * @param groupIds A set of groupIds
		 * @return <code>true</code> if this link involves only those groupIds
		 */
		public boolean within( String... groupIds ) {
			return Arrays.binarySearch( groupIds, fromGroup ) >= 0
					&& Arrays.binarySearch( groupIds, toGroup ) >= 0;
		}

		public String fromArtifactId() {
			return fromArtifact;
		}

		@Override
		public String toString() {
			return "  " + fromArtifact + type + toArtifact + "\n";
		}
	}
}
