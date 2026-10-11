package com.mastercard.test.flow.report;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Checks the generated application template that is copied into every report
 * page.
 */
@SuppressWarnings("static-method")
class GeneratedTemplateTest {

	private static final String TEMPLATE = "/com/mastercard/test/flow/report/index.html";
	private static final String STYLE_TAG_START = "<style";

	/**
	 * Application and font styles remain shared resources rather than being copied
	 * into every generated page.
	 */
	@Test
	void stylesRemainShared() {
		String template = template();

		assertFalse( hasStyleElement( template ),
				"Generated template contains an inline style block" );
		assertTrue( hasApplicationStylesheetLink( template ),
				"Generated template does not link to the application stylesheet" );
		String robotoLink = "href=\"https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500";
		assertTrue( template.contains( robotoLink + "&display=swap\"" )
				|| template.contains( robotoLink + "&amp;display=swap\"" ),
				"Generated template does not link to the Roboto stylesheet" );
		assertTrue( template.contains(
				"href=\"https://fonts.googleapis.com/icon?family=Material+Icons\"" ),
				"Generated template does not link to the Material Icons stylesheet" );
	}

	private static boolean hasStyleElement( String template ) {
		String lowerCaseTemplate = template.toLowerCase( Locale.ROOT );
		int start = lowerCaseTemplate.indexOf( STYLE_TAG_START );
		while( start >= 0 ) {
			int boundary = start + STYLE_TAG_START.length();
			if( boundary < lowerCaseTemplate.length()
					&& (lowerCaseTemplate.charAt( boundary ) == '>'
							|| Character.isWhitespace( lowerCaseTemplate.charAt( boundary ) )) ) {
				return true;
			}
			start = lowerCaseTemplate.indexOf( STYLE_TAG_START, boundary );
		}
		return false;
	}

	private static boolean hasApplicationStylesheetLink( String template ) {
		String lowerCaseTemplate = template.toLowerCase( Locale.ROOT );
		int start = lowerCaseTemplate.indexOf( "<link" );
		while( start >= 0 ) {
			int end = lowerCaseTemplate.indexOf( '>', start );
			if( end < 0 ) {
				return false;
			}
			String link = lowerCaseTemplate.substring( start, end );
			int href = link.indexOf( "href=\"styles-" );
			if( link.contains( "rel=\"stylesheet\"" ) && href >= 0 ) {
				int hrefEnd = link.indexOf( '"', href + "href=\"".length() );
				if( hrefEnd > href && link.substring( href, hrefEnd ).endsWith( ".css" ) ) {
					return true;
				}
			}
			start = lowerCaseTemplate.indexOf( "<link", end + 1 );
		}
		return false;
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
