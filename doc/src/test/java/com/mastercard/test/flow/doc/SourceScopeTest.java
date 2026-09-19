package com.mastercard.test.flow.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Routine source checks must not treat disposable research as library inputs.
 */
@SuppressWarnings("static-method")
class SourceScopeTest {
	@ParameterizedTest
	@ValueSource(strings = { ".md", ".java", ".xml", ".ts", ".component.html" })
	void ignoresScratchButRetainsLibrarySources( String suffix, @TempDir Path root )
			throws IOException {
		Path library = root.resolve( "module/src/test/Example" + suffix );
		Path similarlyNamed = root.resolve( ".scratch-notes/Example" + suffix );
		Path scratch = root.resolve( ".scratch/experiment/Example" + suffix );
		for( Path file : Set.of( library, similarlyNamed, scratch ) ) {
			Files.createDirectories( file.getParent() );
			Files.writeString( file, "fixture" );
		}
		var sources = new Util.SuffixForager( suffix );
		Files.walkFileTree( root, sources );
		assertEquals( Set.of( library, similarlyNamed ), Set.copyOf( sources.files() ) );
	}
}
