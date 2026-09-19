package com.mastercard.test.flow.assrt.junit5;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.opentest4j.IncompleteExecutionException;
import org.opentest4j.TestAbortedException;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor;
import com.mastercard.test.flow.assrt.Precedence;

/**
 * Self-typed sibling of {@link Flocessor}, supplied by {@link FlowExecution}.
 * {@link #tests()} returns a lazy stream: a flow is emitted only once every
 * flow it must follow has finished, so Jupiter may run the emitted leaves
 * concurrently without any leaf ever waiting on another.
 */
public final class PreparedFlocessor extends AbstractFlocessor<PreparedFlocessor> {
	private final FlowExecution owner;
	private boolean prepared;
	/** Bound on the wait for a running flow, or null to wait indefinitely */
	private Duration progressTimeout;

	/**
	 * @param owner The factory-local handle
	 * @param title The test title
	 * @param model The system model
	 */
	PreparedFlocessor( FlowExecution owner, String title, Model model ) {
		super( title, model, owner.history() );
		this.owner = owner;
		finalOnlyReporting();
		concurrentContexts();
	}

	/**
	 * Bounds how long the factory waits for any running flow to finish before
	 * failing the run. By default it waits indefinitely, as the test framework or
	 * build tool already bounds the run. The factory relies on every emitted flow
	 * running to completion; a cooperative cancellation (JUnit 6
	 * {@code CancellationToken}) skips emitted leaves without running them, so a
	 * cancelled run only ends when the JVM is stopped or this timeout elapses.
	 *
	 * @param timeout Positive duration
	 * @return this adapter
	 */
	public PreparedFlocessor progressTimeout( Duration timeout ) {
		beforeConfiguration();
		if( Objects.requireNonNull( timeout ).isNegative() || timeout.isZero() ) {
			throw new IllegalArgumentException( "Progress timeout must be positive" );
		}
		progressTimeout = timeout;
		return this;
	}

	/**
	 * Freezes configuration and prepares the flows. No flow bodies execute here.
	 *
	 * @return Leaves to return from the owning factory, emitted as they become
	 *         ready
	 */
	public Stream<DynamicNode> tests() {
		beforeConfiguration();
		prepared = true;
		if( owner.concurrent() ) {
			requireConcurrentConfiguration();
		}
		List<Flow> selected;
		try( Stream<Flow> flows = prepareFlows() ) {
			selected = flows.toList();
		}
		Set<String> identities = new HashSet<>();
		for( Flow flow : selected ) {
			if( !identities.add( flow.meta().id() ) ) {
				throw new IllegalArgumentException(
						"Duplicate prepared Flow identity: " + flow.meta().id() );
			}
		}
		Precedence order = new Precedence( selected );
		initializeReporting();
		return StreamSupport.stream( new ReadyStream( selected, order ), false );
	}

	@Override
	protected void beforeConfiguration() {
		if( prepared ) {
			throw new IllegalStateException(
					"Flow configuration is frozen after first tests() preparation" );
		}
	}

	/** Completes reporting after the owning class has finished. */
	void complete() {
		completeProcessing();
	}

	/**
	 * Emits each flow once its predecessors have finished. All bookkeeping is
	 * guarded by the shared History monitor, which finished leaves notify.
	 */
	private final class ReadyStream extends Spliterators.AbstractSpliterator<DynamicNode> {
		private final List<Flow> flows;
		private final Precedence.Readiness readiness;
		private final NavigableSet<Integer> ready = new TreeSet<>();
		private final Set<Integer> running = new HashSet<>();
		private int emitted;
		private int completed;

		ReadyStream( List<Flow> flows, Precedence order ) {
			super( flows.size(), Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.SIZED );
			this.flows = flows;
			readiness = order.readiness();
			ready.addAll( order.roots() );
		}

		@Override
		public boolean tryAdvance( Consumer<? super DynamicNode> action ) {
			synchronized( history ) {
				if( emitted == flows.size() ) {
					return false;
				}
			}
			int index = awaitReady();
			Flow flow = flows.get( index );
			action.accept( DynamicTest.dynamicTest( flow.meta().id(), Flocessor.testSource( flow ),
					() -> run( index ) ) );
			return true;
		}

		/**
		 * @return The index of the next ready flow, now counted as emitted and running
		 */
		private int awaitReady() {
			Blocker blocker = new Blocker();
			try {
				if( Thread.currentThread() instanceof ForkJoinWorkerThread ) {
					// Let the pool compensate for this parked worker so emitted leaves
					// still have somewhere to run.
					ForkJoinPool.managedBlock( blocker );
				}
				else {
					blocker.block();
				}
			}
			catch( InterruptedException e ) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException( "Interrupted while waiting for " + outstanding(), e );
			}
			synchronized( history ) {
				if( ready.isEmpty() ) {
					throw new IllegalStateException( "No Flow completed within " + progressTimeout
							+ "; " + outstanding() );
				}
				int index = ready.pollFirst();
				emitted++;
				running.add( index );
				return index;
			}
		}

		private String outstanding() {
			synchronized( history ) {
				return "running: " + running.stream().sorted().map( i -> flows.get( i ).meta().id() )
						.toList() + ", not started: " + (flows.size() - emitted);
			}
		}

		private void run( int index ) {
			try {
				processRecording( flows.get( index ), IncompleteExecutionException.class::isInstance );
			}
			finally {
				synchronized( history ) {
					running.remove( index );
					completed++;
					ready.addAll( readiness.finished( index ) );
					history.notifyAll();
				}
			}
		}

		/**
		 * Waits on the History monitor until a flow is ready or, if a progress timeout
		 * is set, until it passes without another flow finishing.
		 */
		private final class Blocker implements ForkJoinPool.ManagedBlocker {
			private long deadline = deadline();

			private long deadline() {
				return progressTimeout == null ? Long.MAX_VALUE
						: System.nanoTime() + progressTimeout.toNanos();
			}

			private boolean expired() {
				return deadline != Long.MAX_VALUE && deadline - System.nanoTime() <= 0;
			}

			@Override
			public boolean block() throws InterruptedException {
				synchronized( history ) {
					int observed = completed;
					while( ready.isEmpty() ) {
						if( deadline == Long.MAX_VALUE ) {
							history.wait();
						}
						else {
							// one clock read: the deadline may pass between a check and the wait
							long remaining = deadline - System.nanoTime();
							if( remaining <= 0 ) {
								return true;
							}
							history.wait( remaining / 1_000_000, (int) (remaining % 1_000_000) );
						}
						if( completed != observed ) {
							// Progress without readiness: another flow finished, so keep waiting.
							observed = completed;
							deadline = deadline();
						}
					}
					return true;
				}
			}

			@Override
			public boolean isReleasable() {
				synchronized( history ) {
					return !ready.isEmpty() || expired();
				}
			}
		}
	}

	@Override
	protected void skip( String reason ) {
		throw new TestAbortedException( reason );
	}

	@Override
	protected void compare( String message, String expected, String actual ) {
		Assertions.assertEquals( expected, actual, message );
	}
}
