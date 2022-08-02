package com.mastercard.test.flow.report;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;

/**
 * Exercises {@link Template}
 */
@SuppressWarnings("static-method")
class TemplateTest {

	/**
	 * This is taken from the actual angular index file, then lightly edited to take
	 * out some of the longer lines that don't get altered in the template
	 * population process
	 */
	private static final String TEMPLATE_CONTENT = ""
			+ "<!DOCTYPE html><html lang=\"en\">\n"
			+ "<head>\n"
			+ "  <meta charset=\"utf-8\">\n"
			+ "  <title>Index</title>\n"
			+ "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
			+ "  <link rel=\"icon\" type=\"image/x-icon\" href=\"favicon.ico\">\n"
			+ "  <script type=\"text/javascript\">\n"
			+ "    index_json =\n"
			+ "    // START_JSON_DATA\n"
			+ "// this is where the content should be!\n"
			+ "    // END_JSON_DATA\n"
			+ "    ;\n"
			+ "  </script>\n"
			+ "  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\">\n"
			+ "  <style>"
			+ "    .mat-typography {\n"
			+ "      font:400 14px/20px Roboto,Helvetica Neue,sans-serif;\n"
			+ "      letter-spacing:normal;\n"
			+ "    }\n"
			+ "    body,html {\n"
			+ "      height:100%;\n"
			+ "    }\n"
			+ "    body {\n"
			+ "      margin:0;\n"
			+ "      font-family:Roboto,Helvetica Neue,sans-serif;\n"
			+ "    }\n"
			+ "    </style>\n"
			+ "    <link rel=\"stylesheet\" href=\"styles.fcf3ffb375fb6ac10674.css\" media=\"print\" onload=\"this.media='all'\">"
			+ "  <noscript>\n"
			+ "    <link rel=\"stylesheet\" href=\"styles.fcf3ffb375fb6ac10674.css\">\n"
			+ "  </noscript>\n"
			+ "</head>\n"
			+ "<body class=\"mat-typography\">\n"
			+ "  <app-root></app-root>\n"
			+ "  <script src=\"runtime.5d78440cd03435fa112e.js\" defer></script>\n"
			+ "  <script src=\"polyfills.7058b61b535b67bd6769.js\" defer></script>\n"
			+ "  <script src=\"main.e0a5c762a86d3709d923.js\" defer></script>\n"
			+ "</body></html>";
	private static final Template TEMPLATE;
	private static final Map<String, Object> DATA = new TreeMap<>();
	static {
		Path tp = QuietFiles.createTempFile( null, null );
		tp.toFile().deleteOnExit();
		QuietFiles.write( tp, TEMPLATE_CONTENT.getBytes( StandardCharsets.UTF_8 ) );
		TEMPLATE = new Template( tp );
		DATA.put( "foo", "bar" );
		DATA.put( "html_open_comment", "<!--" );
		DATA.put( "html_open_script", "<script>" );
		DATA.put( "html_close_script", "</script>" );
	}

	/**
	 * Shows that template insertion puts the data in the expected place, that
	 * elements are escaped properly, and that relative paths are updated
	 */
	@Test
	void insert() {
		String populated = TEMPLATE.insert( DATA, Paths.get( "a", "b" ) );
		Patch<String> p = DiffUtils.diff( TEMPLATE_CONTENT, populated, null );

		String diff = p.getDeltas().stream()
				.map( d -> {
					String source = d.getSource().getLines().stream()
							.collect( joining( "\n-", "-", "" ) );
					String target = d.getTarget().getLines().stream()
							.collect( joining( "\n+", "+", "" ) );
					if( d.getType() == DeltaType.CHANGE ) {
						return source + "\n"
								+ target;
					}
					return d.toString();
				} )
				.collect( Collectors.joining( "\n" ) );

		// links to resources have been altered, json has been injected
		Assertions.assertEquals( ""
				+ "-  <link rel=\"icon\" type=\"image/x-icon\" href=\"favicon.ico\">\n"
				+ "+  <link rel=\"icon\" type=\"image/x-icon\" href=\"a/b/favicon.ico\">\n"
				+ "-// this is where the content should be!\n"
				+ "+{\n" + ""
				+ "+  \"foo\" : \"bar\",\n"
				+ "+  \"html_close_script\" : \"<\\/script>\",\n"
				+ "+  \"html_open_comment\" : \"<\\!--\",\n"
				+ "+  \"html_open_script\" : \"<\\script>\"\n"
				+ "+}\n"
				+ "-    <link rel=\"stylesheet\" href=\"styles.fcf3ffb375fb6ac10674.css\" media=\"print\" onload=\"this.media='all'\">  <noscript>\n"
				+ "-    <link rel=\"stylesheet\" href=\"styles.fcf3ffb375fb6ac10674.css\">\n"
				+ "+    <link rel=\"stylesheet\" href=\"a/b/styles.fcf3ffb375fb6ac10674.css\" media=\"print\" onload=\"this.media='all'\">  <noscript>\n"
				+ "+    <link rel=\"stylesheet\" href=\"a/b/styles.fcf3ffb375fb6ac10674.css\">\n"
				+ "-  <script src=\"runtime.5d78440cd03435fa112e.js\" defer></script>\n"
				+ "-  <script src=\"polyfills.7058b61b535b67bd6769.js\" defer></script>\n"
				+ "-  <script src=\"main.e0a5c762a86d3709d923.js\" defer></script>\n"
				+ "+  <script src=\"a/b/runtime.5d78440cd03435fa112e.js\" defer></script>\n"
				+ "+  <script src=\"a/b/polyfills.7058b61b535b67bd6769.js\" defer></script>\n"
				+ "+  <script src=\"a/b/main.e0a5c762a86d3709d923.js\" defer></script>",
				diff );
	}

	/**
	 * Shows that data is extracted properly from a populated template
	 */
	@Test
	void extract() {
		String populated = TEMPLATE.insert( DATA, Paths.get( "a", "b" ) );
		Map<String, Object> extracted = Template.extract( populated, Map.class );
		Assertions.assertEquals( DATA, extracted );
	}

	/**
	 * Shows the behaviour of malformed templates
	 */
	@Test
	void badTemplate() {
		for( String t : Arrays.asList(
				"// START_JSON_DATA", // missing end line
				"// END_JSON_DATA" // missing start line
		) ) {
			Path tp = QuietFiles.createTempFile( null, null );
			tp.toFile().deleteOnExit();
			QuietFiles.write( tp, t.getBytes( StandardCharsets.UTF_8 ) );
			assertThrows( IllegalArgumentException.class, () -> new Template( tp ) );
		}

		assertThrows( UncheckedIOException.class,
				() -> new Template( Paths.get( "no such file" ) ) );
	}

	/**
	 * Shows what happens when we try to extract data from malformed content
	 */
	@Test
	void badExtract() {
		assertThrows( IllegalArgumentException.class, () -> Template
				.extract( "no content markers!", Map.class ) );

		assertThrows( UncheckedIOException.class, () -> Template
				.extract( "// START_JSON_DATA\n"
						+ "this is not json!\n"
						+ "// END_JSON_DATA", Map.class ) );
	}

	/**
	 * Whos what happens when non-serialisable data is supplied
	 */
	@Test
	void badInsert() {
		UncheckedIOException uioe = assertThrows( UncheckedIOException.class,
				() -> TEMPLATE.insert( new Object(), Paths.get( "" ) ) );
		assertEquals( InvalidDefinitionException.class, uioe.getCause().getClass() );
	}
}
