package com.mastercard.test.flow.doc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.autodoc.Docs;
import com.mastercard.test.flow.report.QuietFiles;

/**
 * Checks that the documented maven dependency declarations are accurate
 */
@SuppressWarnings("static-method")
class DependencyTest {

	private static Pattern DEP = Pattern.compile( "<dependency>(.*?)</dependency>", Pattern.DOTALL );
	private static Pattern GRP = Pattern.compile( "<groupId>(.*)</groupId>" );
	private static Pattern RTF = Pattern.compile( "<artifactId>(.*)</artifactId>" );
	private static Pattern VRS = Pattern.compile( "<version>(.*)</version>" );

	/**
	 * These files contain dependency declarations that aren't so trivially
	 * validated, so we'll skip over them here and trust to manual review.
	 */
	private static final Set<Path> EXCLUDED = Stream.of(
			"../bom/README.md",
			"../doc/src/main/markdown/quickstart.md" )
			.map( Paths::get )
			.collect( toSet() );

	/**
	 * Checks that all of the suggested dependency declarations in the readmes
	 * accurately target an artifact from this project
	 *
	 * @return per-markdown-file tests
	 */
	@TestFactory
	Stream<DynamicNode> markdown() {
		PomData root = new PomData( null, Paths.get( "../pom.xml" ) );
		Set<String> allowed = new TreeSet<>();
		Map<Path, PomData> dirPoms = new TreeMap<>();
		root.visit( pd -> {
			allowed.add( pd.groupId() + ":" + pd.artifactId() );
			dirPoms.put( pd.dirPath(), pd );
		} );
		String allowedString = allowed.stream().collect( joining( "\n  " ) );

		Set<String> used = new TreeSet<>();

		return Stream.concat(
				Docs.markdownFiles()
						.filter( p -> !EXCLUDED.contains( p ) )
						.map( path -> dynamicTest( path.toString(), () -> {
							String content = new String( QuietFiles.readAllBytes( path ), UTF_8 );
							Matcher depM = DEP.matcher( content );
							while( depM.find() ) {
								Matcher grpM = GRP.matcher( depM.group( 1 ) );
								assertTrue( grpM.find(), "groupId found in " + depM.group( 0 ) );

								Matcher rtfM = RTF.matcher( depM.group( 1 ) );
								assertTrue( rtfM.find(), "artifactId found in " + depM.group( 0 ) );

								// our documentation assumes a bom import, so we shouldn't have a version
								Matcher vrsM = VRS.matcher( depM.group( 1 ) );
								Assertions.assertFalse( vrsM.find(), "version found in " + depM.group( 0 ) );

								String dep = grpM.group( 1 ) + ":" + rtfM.group( 1 );
								used.add( dep );

								if( "README.md".equals( path.getFileName().toString() ) ) {
									PomData pom = dirPoms.get( path.getParent() );
									assertNotNull( pom, "pom associated with readme" );
									assertEquals(
											pom.groupId() + ":" + pom.artifactId(),
											dep,
											String.format(
													"Documented dependency%n%s%nmatches associated pom",
													depM.group() ) );
								}

								assertTrue( allowed.contains( dep ),
										String.format(
												"Dependency '%s' taken from dependency%n%s%nfound in allowed set:%n%s",
												dep, depM.group(), allowedString ) );
							}
						} ) ),
				Stream.of( dynamicTest( "Documented dependencies", () -> {
					assertEquals( ""
							+ "com.mastercard.test.flow:assert-junit4\n"
							+ "com.mastercard.test.flow:assert-junit5\n"
							+ "com.mastercard.test.flow:builder\n"
							+ "com.mastercard.test.flow:duct\n"
							+ "com.mastercard.test.flow:message-core\n"
							+ "com.mastercard.test.flow:message-http\n"
							+ "com.mastercard.test.flow:message-json\n"
							+ "com.mastercard.test.flow:message-sql\n"
							+ "com.mastercard.test.flow:message-text\n"
							+ "com.mastercard.test.flow:message-web\n"
							+ "com.mastercard.test.flow:message-xml\n"
							+ "com.mastercard.test.flow:model\n"
							+ "com.mastercard.test.flow:validation-junit4\n"
							+ "com.mastercard.test.flow:validation-junit5",
							used.stream().collect( joining( "\n" ) ) );
				} ) ) );
	}
}
