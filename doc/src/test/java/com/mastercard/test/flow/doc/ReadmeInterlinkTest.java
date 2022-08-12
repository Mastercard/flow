package com.mastercard.test.flow.doc;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
	 * made a change. It should pass if you just run it again.
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
					existing -> pom.compliant( existing ) ? existing : pom.title(),
					TITLE_END );
		} );
		return dynamicContainer( pom.artifactId(),
				Stream.concat( Stream.of( readme ),
						pom.modules().map( this::fromPom ) ) );
	}

	/**
	 * This is a terrible pay to parse pom files, but we only need a few basic
	 * fields
	 */
	private static class PomData {
		private final PomData parent;
		private final Path dirPath;
		private final String artifactId;
		private final String name;
		private final String description;
		private final List<PomData> modules;

		PomData( PomData parent, Path path ) {
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse( path.toFile() );
				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();

				this.parent = parent;
				dirPath = path.getParent();
				artifactId = xpath.evaluate( "/project/artifactId", doc );
				name = xpath.evaluate( "/project/name", doc );
				description = xpath.evaluate( "/project/description", doc );
				modules = new ArrayList<>();
				NodeList nl = (NodeList) xpath.evaluate( "/project/modules/module", doc,
						XPathConstants.NODESET );

				for( int i = 0; i < nl.getLength(); i++ ) {
					modules.add( new PomData( this, path
							.getParent()
							.resolve( nl.item( i ).getTextContent() )
							.resolve( "pom.xml" ) ) );
				}
			}
			catch( ParserConfigurationException
					| SAXException
					| IOException
					| XPathExpressionException e ) {
				throw new IllegalStateException( "Failed to parse " + path, e );
			}
		}

		/**
		 * @return The directory that the POM resides in
		 */
		Path dirPath() {
			return dirPath;
		}

		/**
		 * @return The POM's artifactId value
		 */
		String artifactId() {
			return artifactId;
		}

		/**
		 * @return child modules
		 */
		Stream<PomData> modules() {
			return modules.stream();
		}

		/**
		 * @param section A block of text
		 * @return <code>true</code> if that block contains the content harvested from
		 *         the pom
		 */
		boolean compliant( String section ) {
			return section.contains( name() )
					&& section.contains( description )
					&& section.contains( parentLink() )
					&& childLinks().allMatch( section::contains );
		}

		/**
		 * @return The link list item to the parent module, or the empty string if there
		 *         is no parent.
		 */
		String parentLink() {
			if( parent == null ) {
				// root readme, nowhere to go
				return "";
			}
			if( parent.parent == null ) {
				// 1st-level, github has a weird thing where just linking to `..` and hoping for
				// the root page results in a 404. It works fine on stash ¯\_(ツ)_/¯
				return String.format( "\n * [../%s](https://github.com/Mastercard/flow) %s",
						parent.name(), parent.description );
			}
			// general case, works on 2nd level and, presumably, beyond
			return String.format( "\n * [../%s](..) %s",
					parent.name(), parent.description );
		}

		/**
		 * @return The link list items to child modules
		 */
		Stream<String> childLinks() {
			return modules.stream()
					.map( child -> String.format( " * [%s](%s) %s\n",
							child.name(),
							dirPath.relativize( child.dirPath ),
							child.description ) );
		}

		String title() {
			return String.format( ""
					+ "# %s\n" // name
					+ "\n"
					+ "%s\n" // description
					+ "%s\n" // parent link
					+ "%s", // child links
					name(),
					description,
					parentLink(),
					childLinks().collect( joining() ) )
					.trim();
		}

		private String name() {
			return name.isEmpty() ? artifactId : name;
		}
	}
}
