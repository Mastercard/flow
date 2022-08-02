package com.mastercard.test.flow.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Embeds json data into a HTML template
 */
class Template {

	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	private static final String START_LINE = "// START_JSON_DATA";
	private static final String END_LINE = "// END_JSON_DATA";
	private final String prefix;
	private final String suffix;

	/**
	 * @param file The path to the template file
	 */
	public Template( Path file ) {
		try( BufferedReader br = new BufferedReader(
				new InputStreamReader( Files.newInputStream( file ) ) ) ) {
			String template = br.lines().collect( Collectors.joining( "\n" ) );
			int s = template.indexOf( START_LINE );
			int e = template.lastIndexOf( END_LINE );

			if( s == -1 || e == -1 ) {
				throw new IllegalArgumentException(
						String.format( "Start or end line not found in %s\n%s", file, template ) );
			}

			// pitest says that it can negate the addition in the next line and have tests
			// still pass, but I can't replicate that manually
			prefix = template.substring( 0, template.indexOf( '\n', s ) + 1 );
			suffix = template.substring( template.lastIndexOf( '\n', e ) );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( "Failed to read file " + file, ioe );
		}
	}

	/**
	 * @param data           The data to insert into the template
	 * @param pathCorrection The relative path between where the index file think it
	 *                       is and where it's actually being written to. This is
	 *                       used to correct relative paths of referenced resources
	 * @return the populated template content
	 */
	public String insert( Object data, Path pathCorrection ) {
		String pc = pathCorrection.toString().replace( '\\', '/' ) + '/';
		try {
			return ""
					+ prefix
							.replaceAll( "(href=\")(?!http)", "$1" + pc )
							.replaceAll( "(src=\")", "$1" + pc )
					+ escapeScriptContents( JSON.writeValueAsString( data ) )
							.replace( "\r", "" )
					+ suffix
							.replaceAll( "(href=\")(?!http)", "$1" + pc )
							.replaceAll( "(src=\")", "$1" + pc );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	/**
	 * @param <T>     The desired data type
	 * @param content The populated template content
	 * @param type    The desired data type
	 * @return The extracted and parsed data
	 */
	public static <T> T extract( String content, Class<T> type ) {
		int s = content.indexOf( START_LINE );
		int e = content.lastIndexOf( END_LINE );
		if( s == -1 || e == -1 ) {
			throw new IllegalArgumentException(
					String.format( "Start or end line not found in\n%s", content ) );
		}

		try {
			return JSON.readValue(
					unEscapeScriptContents( content.substring( s + START_LINE.length(), e ).trim() ), type );
		}
		catch( IOException ioe ) {
			throw new UncheckedIOException( ioe );
		}
	}

	/**
	 * Per <a href=
	 * "https://www.w3.org/TR/html52/semantics-scripting.html#restrictions-for-contents-of-script-elements">this
	 * guidance</a>, contents of <code>&lt;script&gt;</code> elements need to be
	 * escaped to be interpreted correctly by the browser.
	 *
	 * @param raw The desired contents of the <code>&lt;script&gt;</code>. This is
	 *            what you would expect to find in an standalone javascript file.
	 * @return Equivalent content that is suitable for inclusion in a
	 *         <code>&lt;script&gt;</code> element
	 */
	private static String escapeScriptContents( String raw ) {
		return raw
				// add one level of escaping
				.replaceAll( "<(\\\\*)!--", "<\\\\$1!--" )
				.replaceAll( "<(\\\\*)script", "<\\\\$1script" )
				.replaceAll( "<(\\\\*)/script", "<\\\\$1/script" );
	}

	/**
	 * The inverse of {@link #escapeScriptContents(String)}
	 *
	 * @param raw The text from the written report that has previously been
	 *            processed via {@link #escapeScriptContents(String)}
	 * @return The original text
	 */
	private static String unEscapeScriptContents( String raw ) {
		return raw
				// strip one level of escaping
				.replaceAll( "<(\\\\*)\\\\!--", "<$1!--" )
				.replaceAll( "<(\\\\*)\\\\script", "<$1script" )
				.replaceAll( "<(\\\\*)\\\\/script", "<$1/script" );
	}
}
