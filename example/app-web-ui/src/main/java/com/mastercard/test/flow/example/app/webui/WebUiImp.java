package com.mastercard.test.flow.example.app.webui;

import static java.util.stream.Collectors.joining;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.example.app.Ui;
import com.mastercard.test.flow.example.app.WebUi;

/**
 * Implementation of the web service
 */
public class WebUiImp implements WebUi {

	private static final Logger LOG = LoggerFactory.getLogger( WebUiImp.class );

	/**
	 * Provides app functionality
	 */
	@Dependency
	public Ui ui;

	private static final String WEB_TEMPLATE = load( "web.html" );
	private static final String RESULT_TEMPLATE = load( "results.html" );
	private static final Map<String, String> WHITESPACE;
	static {
		Map<String, String> ws = new HashMap<>();
		ws.put( " ", "' '" );
		ws.put( "\t", "\\t" );
		ws.put( "\n", "\\n" );
		ws.put( "\r", "\\r" );
		WHITESPACE = Collections.unmodifiableMap( ws );
	}

	@Override
	public String home() {
		LOG.info( "serving home page" );
		return WEB_TEMPLATE;
	}

	@Override
	public String process( String subject, String characters ) {
		LOG.info( "form submission subject = {}, characters = {}", subject, characters );
		Map<String, Integer> results;

		if( characters == null || characters.isEmpty() ) {
			results = ui.histogram( subject );
		}
		else {
			results = ui.histogram( subject, characters );
		}

		String flattened = results.entrySet().stream()
				.map( e -> String.format( "%3s = %s",
						WHITESPACE.getOrDefault( e.getKey(), e.getKey() ),
						e.getValue() ) )
				.collect( joining( "\n" ) );

		return populate( RESULT_TEMPLATE,
				"subject", subject,
				"characters", characters,
				"result", flattened );
	}

	/**
	 * <b>N.B:</b> This is a giant gaping XSS vector. Use a proper templating lib in
	 * non-toy projects.
	 *
	 * @param template Template text
	 * @param nvp      name/value pairs to populate into the template
	 * @return
	 */
	private static String populate( String template, String... nvp ) {
		String populated = template;
		for( int i = 0; i < nvp.length; i += 2 ) {
			populated = populated.replaceAll( "\\$\\{" + nvp[i] + "\\}", nvp[i + 1] );
		}
		return populated;
	}

	private static String load( String name ) {
		String res = "webui/templates/" + name;
		try( BufferedReader br = new BufferedReader(
				new InputStreamReader( WebUiImp.class.getClassLoader()
						.getResourceAsStream( res ) ) ) ) {
			return br.lines().collect( Collectors.joining( "\n" ) );
		}
		catch( Exception e ) {
			throw new IllegalStateException( "Failed to load resource '" + res + "'", e );
		}
	}
}
