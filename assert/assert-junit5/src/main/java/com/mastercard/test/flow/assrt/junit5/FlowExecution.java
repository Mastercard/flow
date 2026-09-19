package com.mastercard.test.flow.assrt.junit5;

import java.util.Objects;

import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.History;

/**
 * Factory-local handle supplied by {@link FlowTest}. It owns the run's
 * {@link History} and closes the report when the test class finishes.
 */
@SuppressWarnings("deprecation") // CloseableResource still drives store cleanup on Jupiter 5.10
public final class FlowExecution implements CloseableResource, AutoCloseable {
	private final History history = new History();
	private final boolean concurrent;
	private PreparedFlocessor runner;

	/** @param concurrent Whether the factory's leaves will execute concurrently */
	FlowExecution( boolean concurrent ) {
		this.concurrent = concurrent;
	}

	/**
	 * Attaches the factory's model and title. Call once per factory invocation.
	 *
	 * @param title Test title
	 * @param model The system model
	 * @return The prepared adapter, to be configured and then asked for
	 *         {@link PreparedFlocessor#tests()}
	 */
	public synchronized PreparedFlocessor flocessor( String title, Model model ) {
		if( runner != null ) {
			throw new IllegalStateException( "FlowExecution already has a runner" );
		}
		runner = new PreparedFlocessor( this, Objects.requireNonNull( title ),
				Objects.requireNonNull( model ) );
		return runner;
	}

	/** @return The History shared by every leaf, also the factory's wait monitor */
	History history() {
		return history;
	}

	/** @return Whether the factory's leaves will execute concurrently */
	boolean concurrent() {
		return concurrent;
	}

	/**
	 * Completes the report. Invoked by the class-context store after the factory's
	 * subtree has finished, including when the factory was aborted or skipped.
	 */
	@Override
	public synchronized void close() {
		if( runner != null ) {
			runner.complete();
		}
	}
}
