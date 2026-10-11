package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Checks the generated application template that is copied into every report
 * page.
 */
@SuppressWarnings("static-method")
class GeneratedTemplateTest {

	private static final String TEMPLATE = "/com/mastercard/test/flow/report/index.html";

	/**
	 * Application and font styles remain shared resources rather than being copied
	 * into every generated page.
	 */
	@Test
	void stylesRemainShared() {
		String template = template();

		assertFalse( Pattern.compile( "<style(?:\\s|>)", Pattern.CASE_INSENSITIVE )
				.matcher( template ).find(), "Generated template contains an inline style block" );
		assertTrue( Pattern.compile( "<link[^>]+rel=\"stylesheet\"[^>]+href=\"styles-[^\"]+\\.css\"" )
				.matcher( template ).find(),
				"Generated template does not link to the application stylesheet" );
		assertTrue( Pattern.compile( "href=\"https://fonts\\.googleapis\\.com/css2\\?"
				+ "family=Roboto:wght@300;400;500(?:&|&amp;)display=swap\"" )
				.matcher( template ).find(),
				"Generated template does not link to the Roboto stylesheet" );
		assertTrue( template.contains(
				"href=\"https://fonts.googleapis.com/icon?family=Material+Icons\"" ),
				"Generated template does not link to the Material Icons stylesheet" );
	}

	private static String template() {
		try( InputStream in = GeneratedTemplateTest.class.getResourceAsStream( TEMPLATE ) ) {
			assertNotNull( in, "Missing generated report template" );
			return new String( in.readAllBytes(), UTF_8 );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}
}
