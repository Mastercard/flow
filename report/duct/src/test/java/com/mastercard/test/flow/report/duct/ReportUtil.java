package com.mastercard.test.flow.report.duct;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.report.data.Meta;

/**
 * Utility for creating mock reports that we can exercise duct with
 */
class ReportUtil {

	private static final String INDEX_TEMPLATE = Duct.resource( "no-node/index.html" );
	private static final ObjectMapper JSON = new ObjectMapper()
			.enable( SerializationFeature.INDENT_OUTPUT );

	public static void createReport( Path dir, String model, String test, Instant timestamp )
			throws IOException {
		Files.createDirectories( dir );
		Index idx = new Index( new Meta( model, test, timestamp.toEpochMilli() ),
				Collections.emptyList() );
		String index = INDEX_TEMPLATE
				.replaceAll( "", JSON.writeValueAsString( idx ) )
				.replace( "\r", "" );
		Files.write( dir.resolve( "index.html" ), index.getBytes( UTF_8 ) );
	}
}
