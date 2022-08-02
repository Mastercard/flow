package com.mastercard.test.flow.assrt.filter.cli;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * Offers completion suggestions for commands
 */
class CommandCompleter implements Completer {
	/**
	 * Possible commands
	 */
	public enum Command {
		/***/
		HELP("Show help text"),
		/***/
		RESET("Clear all filters"),
		/***/
		TAG_RESET("Clear tag choices"),
		/***/
		FLOW_RESET("Clear flow choices"),
		/***/
		FAILS("Exercise historic failures");

		/**
		 * Human-readable function hint
		 */
		public final String description;

		Command( String description ) {
			this.description = description;
		}

		/**
		 * @return The string that must be typed to invoke the command
		 */
		public String syntax() {
			return name().toLowerCase();
		}

		/**
		 * @param input form the user
		 * @return <code>true</code> if the associated action should be taken for this
		 *         command
		 */
		public boolean invokedBy( String input ) {
			return syntax().equals( input.trim() );
		}
	}

	private final EnumSet<Command> available = EnumSet.noneOf( Command.class );

	/**
	 * @param commands The available commands
	 */
	public CommandCompleter( Command... commands ) {
		Collections.addAll( available, commands );
	}

	@Override
	public void complete( LineReader reader, ParsedLine line, List<Candidate> candidates ) {
		for( Command command : available ) {
			if( command.syntax().startsWith( line.word() ) ) {
				candidates.add( new Candidate( command.syntax(), command.syntax(),
						"command", command.description,
						null, null,
						true ) );
			}
		}
	}

}
