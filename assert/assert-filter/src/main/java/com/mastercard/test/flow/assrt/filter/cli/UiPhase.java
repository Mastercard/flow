package com.mastercard.test.flow.assrt.filter.cli;

import org.jline.reader.Completer;

/**
 * A stage in the command-line interface that allows the set of exercised flows
 * to be customised at runtime
 */
interface UiPhase {

	/**
	 * @param cli Populate this with the content that should precede the input
	 *            prompt
	 */
	void render( Cli cli );

	/**
	 * @param input User input
	 * @return The next ui phase, or <code>null</code> if filter customisation is
	 *         complete
	 */
	UiPhase next( String input );

	/**
	 * @return The completion behaviour for this phase
	 */
	Completer completer();
}
