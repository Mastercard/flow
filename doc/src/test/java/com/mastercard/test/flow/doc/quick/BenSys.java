package com.mastercard.test.flow.doc.quick;

import java.util.function.BiConsumer;

/**
 * Trivial example of a system in need of testing.
 */
class BenSys {

	/**
	 * Generates an appropriate response to greetings that Ben receives
	 *
	 * @param input How Ben is greeted
	 * @return What Ben replies with
	 */
	// snippet-start:system
	public static String getGreetingResponse( String input ) {
		// Ben's default position is to be polite...
		String output = "I am well, thanks for asking.";
		if( input.contains( "despise" ) ) {
			// ... but he strongly defends his boundaries
			output = "The feeling is mutual!";
		}
		return output;
	}
	// snippet-end:system

	/**
	 * Where Ben's logs go, or <code>null</code> to discard them
	 */
	private static volatile BiConsumer<String, String> logs;

	/**
	 * @param listener Receives the correlation identifier and message of each log
	 *                 event, or <code>null</code> to stop listening
	 */
	static void listen( BiConsumer<String, String> listener ) {
		logs = listener;
	}

	/**
	 * As {@link #getGreetingResponse(String)}, but logging the work under the
	 * caller's correlation identifier
	 *
	 * @param input       How Ben is greeted
	 * @param correlation Identifies the conversation, so that log events can be
	 *                    attributed to it
	 * @return What Ben replies with
	 */
	static String getGreetingResponse( String input, String correlation ) {
		BiConsumer<String, String> l = logs;
		if( l != null ) {
			l.accept( correlation, "Greeted with '" + input + "'" );
		}
		String output = getGreetingResponse( input );
		if( l != null ) {
			l.accept( correlation, "Replying '" + output + "'" );
		}
		return output;
	}
}
