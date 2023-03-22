package com.mastercard.test.flow.doc;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.filter.FilterOptions;
import com.mastercard.test.flow.report.QuietFiles;

/**
 * Checks the correctness of things that look like system properties in our
 * documentation
 */
@SuppressWarnings("static-method")
class PropertyTest {

	private static final Pattern PROPERTY = Pattern.compile( "(mctf\\.[a-z._]+)" );

	/**
	 * @return per-file tests that throw a wobbler if we find an invalid property
	 */
	@TestFactory
	Stream<DynamicTest> markdown() {

		Set<String> validProperties = new TreeSet<>();
		Stream.of( AssertionOptions.values() ).forEach( o -> validProperties.add( o.property() ) );
		Stream.of( FilterOptions.values() ).forEach( o -> validProperties.add( o.property() ) );

		return Util.markdownFiles()
				.map( mdFile -> dynamicTest(
						mdFile.toString(),
						() -> checkProperties( mdFile, validProperties ) ) );
	}

	private static void checkProperties( Path file, Set<String> validProperties ) {
		QuietFiles.lines( file )
				.forEach( line -> {
					Matcher mtch = PROPERTY.matcher( line );
					while( mtch.find() ) {
						if( !validProperties.contains( mtch.group( 1 ) ) ) {
							fail( String.format(
									"Line\n  '%s'\ncontains unknown property '%s'. Valid values are\n  %s",
									line, mtch.group( 1 ),
									validProperties.stream().collect( Collectors.joining( "\n  " ) ) ) );
						}
					}
				} );
	}
}
