package com.mastercard.test.flow.assrt.junit5;

import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import com.mastercard.test.flow.Flow;

/**
 * Emits canonical flow indices only after their admission predecessors finish.
 * Readiness state belongs exclusively to the stream producer; flow workers only
 * publish completion events through {@link #finished(int)}.
 */
final class FlowAdmission extends Spliterators.AbstractSpliterator<Integer> {

	private final List<String> flowIds;
	private final Precedence.Readiness readiness;
	private final NavigableSet<Integer> ready = new TreeSet<>();
	private final Set<Integer> running = new HashSet<>();
	private final BlockingQueue<Integer> completions = new LinkedBlockingQueue<>();
	private int emitted;

	/**
	 * @param flows     Flows in canonical order
	 * @param replaying Whether every flow must be serialized for replay
	 */
	FlowAdmission( List<Flow> flows, boolean replaying ) {
		super( flows.size(), Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.SIZED );
		Precedence precedence = new Precedence( flows, replaying );
		flowIds = flows.stream().map( flow -> flow.meta().id() ).toList();
		readiness = precedence.readiness();
		ready.addAll( precedence.roots() );
	}

	/**
	 * Emits the next eligible canonical index. The single stream producer may wait
	 * here for a completion event; flow workers never do.
	 *
	 * @param action Receives the next eligible index
	 * @return {@code false} after every flow has been emitted
	 */
	@Override
	public boolean tryAdvance( Consumer<? super Integer> action ) {
		if( emitted == flowIds.size() ) {
			return false;
		}

		drainCompletions();
		while( ready.isEmpty() ) {
			complete( awaitCompletion() );
			drainCompletions();
		}

		int index = ready.pollFirst();
		emitted++;
		running.add( index );
		action.accept( index );
		return true;
	}

	/**
	 * @return Always {@code null}; admission has exactly one stream producer
	 */
	@Override
	public Spliterator<Integer> trySplit() {
		return null;
	}

	/**
	 * Publishes one worker completion without mutating producer-owned scheduler
	 * state.
	 *
	 * @param index A previously emitted canonical flow index
	 */
	void finished( int index ) {
		completions.add( index );
	}

	private void drainCompletions() {
		Integer completed;
		while( (completed = completions.poll()) != null ) {
			complete( completed );
		}
	}

	private void complete( int index ) {
		running.remove( index );
		ready.addAll( readiness.finished( index ) );
	}

	private int awaitCompletion() {
		CompletionBlocker blocker = new CompletionBlocker();
		try {
			if( Thread.currentThread() instanceof ForkJoinWorkerThread ) {
				ForkJoinPool.managedBlock( blocker );
			}
			else {
				blocker.block();
			}
			return blocker.completed();
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException( "Interrupted while waiting for flow readiness; running: "
					+ runningFlows(), e );
		}
	}

	private List<String> runningFlows() {
		return running.stream().sorted().map( flowIds::get ).toList();
	}

	private final class CompletionBlocker implements ForkJoinPool.ManagedBlocker {
		private Integer completed;

		@Override
		public boolean block() throws InterruptedException {
			if( completed == null ) {
				completed = completions.take();
			}
			return true;
		}

		@Override
		public boolean isReleasable() {
			if( completed == null ) {
				completed = completions.poll();
			}
			return completed != null;
		}

		private int completed() {
			return completed;
		}
	}
}
