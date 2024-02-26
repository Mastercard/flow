
package com.mastercard.test.flow.doc;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
		Path path = Paths.get( "../README.md" );
		Util.insert( path,
				"<!-- start_module_diagram:framework -->",
				s -> diagram( "TB", l -> l.isTo( "com.mastercard.test.flow" ), s ),
				"<!-- end_module_diagram -->" );
	}

	/**
	 * Regerenates the diagram in the example system readme
	 *
	 * @throws Exception if insertion fails
	 */
	@Test
	void example() throws Exception {
		Path path = Paths.get( "../example/README.md" );
		Util.insert( path,
				"<!-- start_module_diagram:example -->",
				s -> diagram( "LR", l -> l.isTo( "com.mastercard.test.flow.example" ), s ),
				"<!-- end_module_diagram -->" );
	}

	private static String diagram( String orientation, Predicate<Link> inclusion,
			String existing ) {

		PomData root = new PomData( null, Paths.get( "../pom.xml" ) );

		Set<String> artifacts = new HashSet<>();
		root.visit( pd -> artifacts.add( pd.coords() ) );
		artifacts.removeAll( OCCULTED );

		// from groupId to list of artifacts in that group
		Map<String, Set<PomData>> groups = new TreeMap<>();
		root.visit( pd -> groups
				.computeIfAbsent( pd.groupId(), g -> new TreeSet<>( comparing( PomData::artifactId ) ) )
				.add( pd ) );

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

		// remove links that don't fit in the requested diagram
		links.values().forEach( ll -> ll.removeIf( l -> !inclusion.test( l ) ) );

		// remove artifacts with no dependencies (e.g.: parent poms
		groups.values().forEach( arts -> arts
				.removeIf( pd -> !links.values().stream()
						.map( ll -> ll.stream()
								.filter( l -> l.involves( pd ) )
								.findAny() )
						.filter( Optional::isPresent )
						.map( Optional::get )
						.findAny().isPresent() ) );
		groups.values().removeIf( Set::isEmpty );

		Set<String> existingModules = extractModules( existing );
		Set<String> desiredModules = groups.values().stream()
				.flatMap( Set::stream )
				.map( pd -> moduleLink( root.dirPath(), pd ) )
				.collect( toCollection( TreeSet::new ) );
		Set<String> existingLinks = extractLinks( existing );
		Set<String> desiredLinks = links.values().stream()
				.flatMap( List::stream )
				.map( Link::toString )
				.collect( toCollection( TreeSet::new ) );
		if( existingModules.equals( desiredModules ) && existingLinks.equals( desiredLinks ) ) {
			// The existing diagram has all the modules and links we want to show, so let's
			// leave it as it is
			return existing;
			// This allows us to make manual edits for reasons of layout while still
			// ensuring the dependency structure is correct
		}

		// The existing diagram is not accurate, so overwrite it with one that is

		StringBuilder mermaid = new StringBuilder( "```mermaid\ngraph " )
				.append( orientation )
				.append( "\n" );

		groups.forEach( ( groupId, pomdatas ) -> {
			mermaid.append( "  subgraph " ).append( groupId ).append( "\n" );
			pomdatas.stream()
					.sorted( comparing( PomData::artifactId ) )
					.forEach( pd -> mermaid
							.append( "    " ).append( moduleLink( root.dirPath(), pd ) ).append( "\n" ) );
			mermaid.append( "  end\n" );
		} );

		desiredLinks.stream()
				.forEach( l -> mermaid.append( "  " ).append( l ).append( "\n" ) );

		mermaid.append( "```" );
		return mermaid.toString();
	}

	/**
	 * Pending https://github.com/orgs/community/discussions/106690
	 */
	private static boolean RENDER_LINKS = false;

	private static final String moduleLink( Path root, PomData pom ) {
		if( RENDER_LINKS ) {
			return String.format( "%s[<a href='https://github.com/Mastercard/flow/tree/main/%s'>%s</a>]",
					pom.artifactId(),
					root.relativize( pom.dirPath() ).toString().replace( '\\', '/' ),
					pom.artifactId() );
		}
		return String.format( "%s[%s]", pom.artifactId(), pom.artifactId() );
	}

	private static final Set<String> extractModules( String mermaid ) {
		Set<String> modules = new TreeSet<>();
		Matcher m;
		if( RENDER_LINKS ) {
			m = Pattern.compile( "\\S+\\[<a href='\\S+'>\\S+</a>\\]" ).matcher( mermaid );
		}
		else {
			m = Pattern.compile( "\\S+\\[\\S+\\]" ).matcher( mermaid );
		}
		while( m.find() ) {
			modules.add( m.group() );
		}
		return modules;
	}

	private static final Set<String> extractLinks( String mermaid ) {
		Set<String> links = new TreeSet<>();
		Matcher m = Pattern.compile( "\\S+ -\\.?-> \\S+" ).matcher( mermaid );
		while( m.find() ) {
			links.add( m.group() );
		}
		return links;
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

		public boolean isTo( String g ) {
			return toGroup.equals( g );
		}

		public String fromArtifactId() {
			return fromArtifact;
		}

		@Override
		public String toString() {
			return fromArtifact + type + toArtifact;
		}
	}
}
