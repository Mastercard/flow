package com.mastercard.test.flow.doc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This is a terrible pay to parse pom files, but we only need a few basic
 * fields
 */
class PomData {
	private final PomData parent;
	private final Path dirPath;
	private final String groupId;
	private final String artifactId;
	private final String version;
	private final String packaging;
	private final String name;
	private final String description;
	private final List<PomData> modules;
	private final List<DepData> dependencies;
	private final List<DepData> dependencyManagement;

	/**
	 * @param parent The parent pom, or <code>null</code>
	 * @param path   The path to the pom file
	 */
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
			groupId = Optional.ofNullable( xpath.evaluate( "/project/groupId", doc ) )
					.filter( s -> !s.isEmpty() )
					.orElseGet( () -> parent.groupId() );
			version = Optional.of( xpath.evaluate( "/project/version", doc ) )
					.filter( s -> !s.isEmpty() )
					.orElseGet( () -> parent.version() );
			packaging = xpath.evaluate( "/project/packaging", doc );
			name = xpath.evaluate( "/project/name", doc );
			description = xpath.evaluate( "/project/description", doc );
			modules = new ArrayList<>();
			NodeList mnl = (NodeList) xpath.evaluate( "/project/modules/module", doc,
					XPathConstants.NODESET );

			for( int i = 0; i < mnl.getLength(); i++ ) {
				modules.add( new PomData( this, path
						.getParent()
						.resolve( mnl.item( i ).getTextContent() )
						.resolve( "pom.xml" ) ) );
			}

			dependencies = new ArrayList<>();
			NodeList dnl = (NodeList) xpath.evaluate( "/project/dependencies/dependency", doc,
					XPathConstants.NODESET );

			for( int i = 0; i < dnl.getLength(); i++ ) {
				dependencies.add( new DepData( xpath, dnl.item( i ), this ) );
			}

			dependencyManagement = new ArrayList<>();
			NodeList dpnl = (NodeList) xpath.evaluate(
					"/project/dependencyManagement/dependencies/dependency", doc,
					XPathConstants.NODESET );
			for( int i = 0; i < dpnl.getLength(); i++ ) {
				dependencyManagement.add( new DepData( xpath, dpnl.item( i ), this ) );
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
	public Path dirPath() {
		return dirPath;
	}

	/**
	 * @return The parent project data
	 */
	public PomData parent() {
		return parent;
	}

	/**
	 * @return The POM's artifactId value
	 */
	public String artifactId() {
		return artifactId;
	}

	/**
	 * @return The project's groupID
	 */
	public String groupId() {
		return groupId;
	}

	/**
	 * @return The project's version string
	 */
	public String version() {
		return version;
	}

	/**
	 * @return The project's packaging value
	 */
	public String packaging() {
		return packaging;
	}

	/**
	 * @return The combined group and artifact IDs
	 */
	public String coords() {
		return groupId() + ":" + artifactId();
	}

	/**
	 * @return child modules
	 */
	public Stream<PomData> modules() {
		return modules.stream();
	}

	/**
	 * @return project dependencies
	 */
	public Stream<DepData> dependencies() {
		return dependencies.stream();
	}

	/**
	 * @return managed dependencies
	 */
	public Stream<DepData> dependencyManagement() {
		return dependencyManagement.stream();
	}

	/**
	 * @return Project name
	 */
	public String name() {
		return name.isEmpty() ? artifactId : name;
	}

	/**
	 * @return project description
	 */
	public String description() {
		return description;
	}

	/**
	 * Recursive operation applicator
	 *
	 * @param vistor The thing to do on the pom
	 */
	public void visit( Consumer<PomData> vistor ) {
		vistor.accept( this );
		modules().forEach( child -> child.visit( vistor ) );
	}

	@Override
	public String toString() {
		return name();
	}

	/**
	 * A project dependency
	 */
	public static class DepData {
		private final String groupId;
		private final String artifactId;
		private final String version;
		private final String scope;
		private final boolean optional;

		/**
		 * @param xpath How to extract data
		 * @param n     The input node
		 * @param pom   The pom that contains the input
		 * @throws XPathExpressionException if xpath fails
		 */
		DepData( XPath xpath, Node n, PomData pom ) throws XPathExpressionException {
			String gd = xpath.evaluate( "groupId", n );
			if( "${project.groupId}".equals( gd ) ) {
				groupId = pom.groupId();
			}
			else {
				groupId = gd;
			}
			artifactId = xpath.evaluate( "artifactId", n );
			version = xpath.evaluate( "version", n );
			scope = Optional.ofNullable( xpath.evaluate( "scope", n ) )
					.filter( s -> !s.isEmpty() )
					.orElse( "compile" );
			optional = Optional.ofNullable( xpath.evaluate( "optional", n ) )
					.map( "true"::equals )
					.orElse( false );
		}

		/**
		 * @return The dependency group ID
		 */
		public String groupId() {
			return groupId;
		}

		/**
		 * @return The dependency artifact ID
		 */
		public String artifactId() {
			return artifactId;
		}

		/**
		 * @return The dependency version string
		 */
		public String version() {
			return version;
		}

		/**
		 * @return commbined group and artifact IDs
		 */
		public String coords() {
			return groupId + ":" + artifactId;
		}

		/**
		 * @return dependency scope
		 */
		public String scope() {
			return scope;
		}

		/**
		 * @return <code>true</code> of the dependency is optional
		 */
		public boolean optional() {
			return optional;
		}
	}
}
