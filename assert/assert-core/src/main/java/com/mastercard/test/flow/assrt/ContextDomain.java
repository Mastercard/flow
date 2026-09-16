package com.mastercard.test.flow.assrt;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Operation;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;
import com.mastercard.test.flow.assrt.resource.ResourceRules;

/**
 * Applied state of one actual shared fixture, supplied by its existing owner.
 * All runners and wrappers of that state must share this handle for its entire
 * physical lifetime. Context actor sets, applicator identity and resource
 * labels do not identify a physical domain. Different handles must represent
 * genuinely different state, even when additional account/configuration keys
 * overlap. No fixture is created, reset or closed by constructing or releasing
 * this handle.
 */
public final class ContextDomain {
	private final Map<Class<? extends Context>, Context> current = new HashMap<>();
	private final Thread owner;
	private final ResourceRequirements requirements;
	private final ThreadLocal<Use> use = new ThreadLocal<>();
	private volatile Throwable unsafe;
	private Consumer<Operation> cancellation;
	private boolean configured;

	/**
	 * @param keys Additional shared state, such as an account or whole-table
	 *             residue
	 */
	public ContextDomain( String... keys ) {
		this( null, keys );
	}

	/**
	 * Declare before creating the fixture. Initial parallel execution does not
	 * support physical affinity; serial execution checks the actual thread.
	 *
	 * @param owner Required physical thread, or null for no affinity
	 * @param keys  Additional shared state; labels never replace this domain
	 *              identity
	 */
	public ContextDomain( Thread owner, String... keys ) {
		this.owner = owner;
		requirements = new ResourceRules()
				.resources( "fixture domain", f -> true, "flow-context-" + UUID.randomUUID() )
				.resources( "fixture shared state", f -> true, keys ).resolve( null );
	}

	/**
	 * Configures this physical fixture's optional cancellation integration once,
	 * before its footprint is shared or any use/mode check. Only actual operations
	 * registered through a Receipt bind this handler; admission creates no fake
	 * use. The handler receives the identical operation, never an inferred current
	 * user. It must return promptly; its return or exception does not prove
	 * completion or permanently poison the domain. Claimed callbacks may arrive
	 * after proof. A client can prepare an inert pre-start-cancellable handle, then
	 * hold a short fixture correlation mutex around receipt.operation() and
	 * identity-map insert. Release that mutex before starting IO; start must honor
	 * earlier cancellation atomically. The handler looks up under the same mutex
	 * and releases it before client cancellation. Publication must contain no IO,
	 * waits, Stop or completion. Remove the mapping under that mutex, then call
	 * operation.complete() outside it; a late cancellation of an already completed
	 * handle must be harmless.
	 *
	 * @param handler Client-specific cancellation request for a supported operation
	 * @return This fixture domain
	 */
	public synchronized ContextDomain cancellation( Consumer<Operation> handler ) {
		if( configured )
			throw new IllegalStateException( "Fixture cancellation must be configured once before use" );
		cancellation = Objects.requireNonNull( handler );
		configured = true;
		return this;
	}

	private synchronized void freeze() {
		configured = true;
	}

	/**
	 * @return Complete footprint, including possible previous-context removal;
	 *         publishing it fixes the optional cancellation configuration
	 */
	public ResourceRequirements requirements() {
		freeze();
		return requirements;
	}

	/**
	 * @return Latched owner uncertainty, or null; never a classification by
	 *         exception type
	 */
	public Throwable uncertainty() {
		return unsafe;
	}

	/**
	 * Must precede the existing owner's first fixture action in the chosen mode.
	 *
	 * @param parallel Whether native parallel mode was selected
	 */
	public void checkMode( boolean parallel ) {
		freeze();
		if( parallel && owner != null )
			throw new IllegalStateException( "Flow parallel does not support physical fixture affinity" );
		check();
	}

	private void check() {
		freeze();
		if( unsafe != null )
			throw new IllegalStateException( "Fixture context domain is unsafe", unsafe );
		if( owner != null && owner != Thread.currentThread() )
			throw new IllegalStateException( "Fixture requires its actual owner thread" );
	}

	/**
	 * Nonblocking acquisition for existing owner creation/reset/teardown. Within a
	 * native invocation this borrows its current whole grant, never a fresh grant.
	 * A null result means busy; callers must not perform the fixture action.
	 *
	 * @return Scoped ownership, or null when conflicting use has not drained
	 */
	public Use tryAcquire() {
		check();
		Use previous = use.get();
		if( previous != null )
			return enter( previous.grant );
		ResourceReservations resources = ResourceReservations.shared();
		Request request = resources.register( resources.capacity( 1 ), requirements, () -> {
			// Lifecycle acquisition never waits for a resource notification.
		} );
		try {
			Grant grant = request.tryAcquire();
			return grant == null ? null : new Use( grant, true, null );
		}
		finally {
			request.cancel();
		}
	}

	/**
	 * Binds already admitted whole ownership to the actual synchronous invocation.
	 * Closing this scope does not release native outer cleanup or a chain grant.
	 *
	 * @param grant The existing complete reservation, not a worker-side acquisition
	 * @return A borrowed scope on this actual thread
	 */
	public Use enter( Grant grant ) {
		check();
		Objects.requireNonNull( grant ).check( requirements );
		Use previous = use.get();
		if( previous != null && previous.grant != grant )
			throw new IllegalStateException( "Fixture already has a different current grant" );
		return new Use( grant, false, previous );
	}

	/**
	 * Records owner-supplied evidence of uncertain state or remaining use. This is
	 * irreversible: no reset retry, repair, recreation or forced release is
	 * provided. Ordinary safely completed assertion failures do not call this
	 * method. This convenience requires a current synchronous Use. External owner
	 * diagnostics must instead retain the exact Receipt supplied before invocation;
	 * neither a thread nor the domain's currently active user identifies that
	 * grant.
	 *
	 * @param failure The original uncertainty cause
	 */
	public void uncertain( Throwable failure ) {
		requiredUse().receipt().uncertain( failure );
	}

	/**
	 * Captures existing complete ownership without authorizing fixture actions or
	 * acquiring resources. Adapters publish this before native emission, so owners
	 * can attribute evidence from pre-body work through final outer cleanup.
	 *
	 * @param grant The exact whole reservation still owned by the supplying run
	 * @return A thread-independent evidence receipt, never a current-grant lookup
	 */
	public Receipt receipt( Grant grant ) {
		freeze();
		Objects.requireNonNull( grant ).check( requirements );
		return new Receipt( grant );
	}

	/** Owner evidence only; it does not permit cross-thread fixture access. */
	public final class Receipt {
		private final Grant grant;

		private Receipt( Grant grant ) {
			this.grant = grant;
		}

		/**
		 * Register before background use escapes. Only that operation's actual end and
		 * required cleanup permit its completion proof; a cancelled future or local
		 * return does not. Unlike uncertain state this obligation can drain.
		 *
		 * @return Exact whole-grant operation receipt, without authorizing thread
		 *         access
		 */
		public ResourceReservations.Operation operation() {
			return grant.operation( cancellation );
		}

		/**
		 * Irreversibly retains this exact whole grant and stops its owning run. May be
		 * called from a diagnostic thread or after a borrowed Use closes, but must
		 * precede native completion. A released grant rejects late evidence without
		 * poisoning the domain or a newer user's grant. This is not repair or late-safe
		 * recovery; safely completed cleanup failures need no evidence.
		 *
		 * @param failure Original owner-supplied uncertainty, not inferred native
		 *                FAILED
		 */
		public void uncertain( Throwable failure ) {
			Throwable retained = grant.retain( failure );
			synchronized( ContextDomain.this ) {
				if( unsafe == null )
					unsafe = retained;
			}
		}
	}

	private Use requiredUse() {
		Use active = use.get();
		if( active == null )
			throw new IllegalStateException( "Fixture use requires its complete reservation" );
		active.checkCurrent();
		return active;
	}

	/** @return Applied state shared by all wrappers of this actual fixture */
	Map<Class<? extends Context>, Context> current() {
		check();
		requiredUse().grant.check( requirements );
		return current;
	}

	/**
	 * Scoped synchronous access; lifecycle responsibility stays with the caller.
	 */
	public final class Use implements AutoCloseable {
		private final Grant grant;
		private final boolean acquired;
		private final Use previous;
		private final Thread thread = Thread.currentThread();
		private boolean closed;

		private Use( Grant grant, boolean acquired, Use previous ) {
			this.grant = grant;
			this.acquired = acquired;
			this.previous = previous;
			use.set( this );
		}

		/**
		 * Runs an existing owner's lifecycle action on the required actual thread.
		 * Failure conservatively retains ownership: the owner must handle any known
		 * safely completed failure inside its action rather than guess from its type.
		 *
		 * @param action Existing create/reset/cleanup action, with no implicit retry
		 */
		public void change( Runnable action ) {
			checkCurrent();
			check();
			grant.check( requirements );
			try {
				action.run();
			}
			catch( Throwable failure ) {
				uncertain( failure );
				throw failure;
			}
		}

		/**
		 * Records a baseline only after the owner's explicit reset succeeds. Never
		 * called automatically between flows, chain members or runner lifetimes.
		 *
		 * @param action The actual fixture reset (or initial baseline creation)
		 */
		public void reset( Runnable action ) {
			change( action );
			current.clear();
		}

		private void checkCurrent() {
			if( closed || thread != Thread.currentThread() || use.get() != this )
				throw new IllegalStateException( "Fixture scope must close on its owning thread in order" );
		}

		/**
		 * @return Evidence for this exact grant, usable after this borrowed scope
		 *         closes
		 */
		public Receipt receipt() {
			checkCurrent();
			return new Receipt( grant );
		}

		/**
		 * Closes once on the creating thread in LIFO order, restoring the previous
		 * borrowed scope (or removing it). Repeated close is a no-op. Only a scope that
		 * acquired ownership releases it; borrowed close never ends native or
		 * whole-chain ownership, and retained unsafe grants cannot be released. An
		 * order/thread violation leaves scope drainage unproved and retains this exact
		 * grant, even when the processing result itself was safely completed.
		 */
		@Override
		public void close() {
			if( closed )
				return;
			try {
				checkCurrent();
			}
			catch( IllegalStateException failure ) {
				try {
					new Receipt( grant ).uncertain( failure );
				}
				catch( RuntimeException | Error cleanup ) {
					if( cleanup != failure )
						failure.addSuppressed( cleanup );
				}
				throw failure;
			}
			closed = true;
			if( previous == null )
				use.remove();
			else
				use.set( previous );
			if( acquired )
				grant.close();
		}
	}
}
