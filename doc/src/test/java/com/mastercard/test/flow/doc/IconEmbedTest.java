package com.mastercard.test.flow.doc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.report.QuietFiles;

/**
 * Checks that all mat-icon usage in the report app is using embedded SVGs. Like
 * with {@link SysOutTest} this isn't really about documentation, we're just
 * using the file-scanning infrastructure
 */
@SuppressWarnings("static-method")
class IconEmbedTest {
	private static final String ICON_SERVICE_NAME = "icon-embed.service.ts";

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
						() -> QuietFiles.lines( path ).forEach( line -> {
							Matcher usage = ICON_USE_PATTERN.matcher( line );
							while( usage.find() ) {
								Matcher embedded = EMBEDDED_USE_PATTERN.matcher( usage.group() );
								if( embedded.find() ) {
									String icon = embedded.group( 1 );
									if( icon.matches( "[a-z_]+" ) ) {
										assertTrue( embeddedIcons().contains( icon ), String.format(
												"Undefined icon '%s' in mat-icon invocation '%s'",
												icon, usage.group() ) );
									}
								}
								else {
									fail( String.format(
											"non-embedded svg mat-icon invocation '%s'",
											usage.group() ) );
								}
							}
						} ) ) );
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
}
