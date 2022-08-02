package com.mastercard.test.flow.doc.dep;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple stateful system
 */
class BenSys {

	private static final Pattern PUT_REQUEST = Pattern.compile( "Please hang on to '(.*)' for me" );
	private static final Pattern GET_REQUEST = Pattern.compile( "Can I have '(.*)' back please\\?" );

	private final Map<String, String> storage = new HashMap<>();

	/**
	 * Handles a storage request
	 *
	 * @param request the request
	 * @return the appropriate response
	 */
	String getStorageResponse( String request ) {

		Matcher pm = PUT_REQUEST.matcher( request );
		if( pm.matches() ) {
			String key = UUID.randomUUID().toString();
			storage.put( key, pm.group( 1 ) );
			return "No problem, just ask for '" + key + "' when you want it back";
		}

		Matcher gm = GET_REQUEST.matcher( request );
		if( gm.matches() ) {
			String value = storage.getOrDefault( gm.group( 1 ), "I forgot it" );
			return "Here it is! '" + value + "'";
		}

		return "I'm sorry, I don't understand";
	}
}
