package com.mastercard.test.flow.assrt.junit5;

import java.util.Optional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Discovery-time mode selector installed by {@link FlowTest}, not a user
 * orderer.
 */
public final class FlowMethodOrderer implements MethodOrderer {
	/** Discovery-time opt-in. Parallel execution is not implemented yet. */
	public static final String PARALLEL_PROPERTY = "flow.parallel";
	private ExecutionMode mode = ExecutionMode.SAME_THREAD;

	@Override
	public void orderMethods( MethodOrdererContext context ) {
		// Select native mode here, never by a late fluent setting or descriptor
		// mutation.
		mode = parallel( context.getConfigurationParameter( PARALLEL_PROPERTY ).orElse( "false" ) )
				? ExecutionMode.CONCURRENT
				: ExecutionMode.SAME_THREAD;
	}

	/**
	 * @param value The exact discovery-time opt-in value
	 * @return Whether parallel execution was explicitly requested
	 */
	static boolean parallel( String value ) {
		if( !"true".equals( value ) && !"false".equals( value ) ) {
			throw new IllegalArgumentException(
					PARALLEL_PROPERTY + " must be true or false at discovery" );
		}
		return "true".equals( value );
	}

	@Override
	public Optional<ExecutionMode> getDefaultExecutionMode() {
		return Optional.of( mode );
	}
}
