package com.mastercard.test.flow.doc.quick;

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
}
