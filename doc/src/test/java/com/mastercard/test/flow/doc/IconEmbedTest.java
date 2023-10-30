package com.mastercard.test.flow.doc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Checks that all mat-icon usage in the report app is using embedded SVGs. Like
 * with {@link SysOutTest} this isn't really about documentation, we're just
 * taking advantage of the file-scanning infrastructure
 */
@SuppressWarnings("static-method")
class IconEmbedTest {
	private static final String ICON_SERVICE_NAME = "icon-embed.service.ts";

	private static final Pattern ICON_SERVICE_INJECTION_PATTERN = Pattern.compile(
			"(\\S*?): IconEmbedService" );

	/**
	 * Matches SVG definitions out of {@value #ICON_SERVICE_NAME}, captures the icon
	 * name
	 */
	private static final Pattern ICON_DEF_PATTERN = Pattern.compile(
			"\\s*([a-z_]+): `<svg.*</svg>`," );

	/**
	 * Finds <i>all</i> mat-icon use
	 */
	private static final Pattern ICON_USE_PATTERN = Pattern.compile(
			"<mat-icon.*</mat-icon>" );

	/**
	 * Matches <i>correct</i> (embedded svg) mat-icon use. Captures the icon name
	 */
	private static final Pattern EMBEDDED_USE_PATTERN = Pattern.compile(
			".*svgIcon=\"([a-zA-Z_(){}.?':]+)\".*></mat-icon" );

	private static Set<String> embeddedIcons = new TreeSet<>();

	/**
	 * Checks that all mat-icon invocation in our angular templates use an embedded
	 * SVG that actually exists in the icon service
	 *
	 * @return One test instance per component template
	 */
	@TestFactory
	Stream<DynamicTest> icons() {
		return Util.componentTemplateFiles()
				.map( path -> dynamicTest( path.getFileName().toString(),
						() -> {
							Set<String> registered = new TreeSet<>();

							QuietFiles.lines( path ).forEach( line -> {
								Matcher usage = ICON_USE_PATTERN.matcher( line );
								while( usage.find() ) {
									Matcher embedded = EMBEDDED_USE_PATTERN.matcher( usage.group() );
									if( embedded.find() ) {
										if( registered.isEmpty() ) {
											registeredIcons( path, registered );
										}

										String icon = embedded.group( 1 );
										if( icon.matches( "[a-z_]+" ) ) {
											assertTrue( embeddedIcons().contains( icon ), String.format(
													"Undefined icon '%s' in mat-icon invocation '%s'",
													icon, usage.group() ) );
											assertTrue( registered.contains( icon ), String.format(
													"Uregistered icon '%s' in mat-icon invocation '%s'",
													icon, usage.group() ) );
										}
									}
									else {
										fail( String.format(
												"non-embedded svg mat-icon invocation '%s'",
												usage.group() ) );
									}
								}
							} );
						} ) );
	}

	private Set<String> embeddedIcons() {
		if( embeddedIcons.isEmpty() ) {
			Path iconServiceSource = Util.typescriptFiles()
					.filter( p -> ICON_SERVICE_NAME.equals( p.getFileName().toString() ) )
					.findFirst()
					.orElseThrow( () -> new IllegalStateException(
							"Failed to find the icon embedding service!" ) );

			QuietFiles.lines( iconServiceSource )
					.map( ICON_DEF_PATTERN::matcher )
					.filter( Matcher::matches )
					.map( m -> m.group( 1 ) )
					.forEach( embeddedIcons::add );
		}
		return embeddedIcons;
	}

	private void registeredIcons( Path template, Set<String> registered ) {
		Path tsFile = template.getParent()
				.resolve( template.getFileName().toString().replaceAll( ".html$", ".ts" ) );
		String tsContent = new String( QuietFiles.readAllBytes( tsFile ), UTF_8 );

		Matcher injection = ICON_SERVICE_INJECTION_PATTERN.matcher( tsContent );
		assertTrue( injection.find(), "Failed to find IconEmbedService injection in " + tsFile );

		try {
			Pattern registerPattern = Pattern.compile( injection.group( 1 ) + ".register\\((.*?)\\)",
					Pattern.DOTALL );
			Matcher registration = registerPattern.matcher( tsContent );
			assertTrue( registration.find(), "Failed to find icon registration call in " + tsFile );

			Matcher names = Pattern.compile( "\"([a-z_]+)\"" ).matcher( registration.group( 1 ) );
			while( names.find() ) {
				registered.add( names.group( 1 ) );
			}
		}
		catch( PatternSyntaxException pse ) {
			throw new IllegalStateException(
					"Failed to compile regex with '" + injection.group( 1 ) + "' taken from " + template,
					pse );
		}

	}
}
