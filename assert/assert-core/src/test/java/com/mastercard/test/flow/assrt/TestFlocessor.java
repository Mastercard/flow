package com.mastercard.test.flow.assrt;

import static java.util.stream.Collectors.joining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.History.Result;

/**
 * A flocessor that doesn't really do anything, just logs what it's been asked
 * to assert or skip.
 */
public class TestFlocessor extends AbstractFlocessor<TestFlocessor> {

	private final List<String> eventLog = new ArrayList<>();

	/**
	 * @param title test title
	 * @param model test data
	 */
	public TestFlocessor( String title, Model model ) {
		super( title, model );
	}

	/**
	 *
	 */
	public void execute() {
		eventLog.clear();
		history.clear();

		flows().forEach( f -> {
			try {
				process( f );
				history.recordResult( f, Result.SUCCESS );
			}
			catch( @SuppressWarnings("unused") SkipException se ) {
				history.recordResult( f, Result.SKIP );
			}
			catch( @SuppressWarnings("unused") CompareException ce ) {
				history.recordResult( f, Result.UNEXPECTED );
			}
			catch( Exception e ) {
				history.recordResult( f, Result.ERROR );
				eventLog.add( f.meta().id() + " error " + e.getMessage() );
			}
		} );
	}

	/**
	 * @return The comparison and skip events
	 */
	public String events() {
		return eventLog.stream().collect( joining( "\n" ) );
	}

	/**
	 * @return flow testing results
	 */
	public String results() {
		return flows()
				.map( f -> f.meta().id() + " " + history.get( f ) )
				.collect( joining( "\n" ) );
	}

	@Override
	protected void skip( String reason ) {
		eventLog.add( "SKIP " + reason );
		throw new SkipException( reason );
	}

	private static class SkipException extends RuntimeException {
		/***/
		private static final long serialVersionUID = 1L;

		public SkipException( String msg ) {
			super( msg );
		}
	}

	@Override
	protected void compare( String message, String expected, String actual ) {
		eventLog.add( "COMPARE "
				// mask line numbers
				+ message.replaceAll( "\\.java:\\d+", ".java:_" )
				+ "\n" + sideBySide( expected, actual ) );

		if( !expected.equals( actual ) ) {
			throw new CompareException( message );
		}
	}

	private static class CompareException extends AssertionError {
		/***/
		private static final long serialVersionUID = 1L;

		public CompareException( String msg ) {
			super( msg );
		}
	}

	private static String sideBySide( String left, String right ) {
		Deque<String> leftLines = new ArrayDeque<>( Arrays.asList( left.split( "\n" ) ) );
		Deque<String> rightLines = new ArrayDeque<>( Arrays.asList( right.split( "\n" ) ) );

		int leftWidth = leftLines.stream().mapToInt( String::length ).max().orElse( 1 );
		int rightWidth = rightLines.stream().mapToInt( String::length ).max().orElse( 1 );
		leftWidth = Math.max( leftWidth, 1 );
		rightWidth = Math.max( rightWidth, 1 );

		String format = " | %" + leftWidth + "s | %" + rightWidth + "s |";

		StringBuilder sb = new StringBuilder();
		while( !leftLines.isEmpty() || !rightLines.isEmpty() ) {
			sb.append( String.format( format,
					Optional.ofNullable( leftLines.pollFirst() ).orElse( "" ),
					Optional.ofNullable( rightLines.pollFirst() ).orElse( "" ) ) )
					.append( "\n" );
		}
		return sb.toString();
	}
}
