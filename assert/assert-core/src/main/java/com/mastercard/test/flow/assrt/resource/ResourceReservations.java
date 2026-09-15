package com.mastercard.test.flow.assrt.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Same-classloader/JVM whole-set reservations for cooperating runners and
 * fixture owners. No worker waits here. Callers register only dependency-ready
 * work, try admission outside their own locks, then commit or return a proven
 * unused grant. Pending registration order is stable; oldest-conflicting-ready
 * fairness is not yet enforced. Reservations do not transfer fixture lifecycle
 * ownership.
 */
public final class ResourceReservations {
	private static final ResourceReservations SHARED = new ResourceReservations();
	private final Set<Request> pending = new LinkedHashSet<>();
	private final Set<String> held = new HashSet<>();
	private int active;
	private boolean exclusive;

	private ResourceReservations() {
		// All callers deliberately share one scope, not one scope per runner.
	}

	/** @return The default shared scope (not a cross-classloader/process lock) */
	public static ResourceReservations shared() {
		return SHARED;
	}

	/**
	 * Creates an execution-capacity owner in this shared resource scope.
	 *
	 * @param limit Maximum simultaneously granted requests for this owner
	 * @return A counter reserved atomically with every complete resource set
	 */
	public Capacity capacity( int limit ) {
		if( limit < 1 ) {
			throw new IllegalArgumentException( "Execution capacity must be positive" );
		}
		return new Capacity( limit );
	}

	/**
	 * Registers dependency-ready work without acquiring any resource or capacity.
	 *
	 * @param capacity     Execution owner, shared by this run's requests
	 * @param requirements Immutable resolved requirements
	 * @param changed      Short nonthrowing wakeup, delivered outside reservation
	 *                     locks; callers must also release their own locks before
	 *                     reservation operations
	 * @return A stable pending request, retained across unsuccessful tries
	 */
	public synchronized Request register( Capacity capacity, ResourceRequirements requirements,
			Runnable changed ) {
		Request request = new Request( Objects.requireNonNull( capacity ),
				Objects.requireNonNull( requirements ), Objects.requireNonNull( changed ) );
		pending.add( request );
		return request;
	}

	/** Run-local execution slots; their bookkeeping belongs to the shared scope. */
	public static final class Capacity {
		private final int limit;
		private int active;

		private Capacity( int limit ) {
			this.limit = limit;
		}
	}

	/** One whole-set request, registered once when dependency-ready. */
	public final class Request {
		private final Capacity capacity;
		private final ResourceRequirements requirements;
		private final Runnable changed;

		private Request( Capacity capacity, ResourceRequirements requirements, Runnable changed ) {
			this.capacity = capacity;
			this.requirements = requirements;
			this.changed = changed;
		}

		/**
		 * Never waits or holds a partial set. A returned token owns all keys and one
		 * execution slot, including for known-empty work.
		 *
		 * @return Complete grant, or null when busy, cancelled or already granted
		 */
		public Grant tryAcquire() {
			synchronized( ResourceReservations.this ) {
				if( !pending.contains( this ) || capacity.active == capacity.limit || exclusive
						|| requirements.exclusive() && active != 0
						|| requirements.keys().stream().anyMatch( held::contains ) ) {
					return null;
				}
				pending.remove( this );
				held.addAll( requirements.keys() );
				exclusive = requirements.exclusive();
				active++;
				capacity.active++;
				return new Grant( this );
			}
		}

		/** Withdraws only an ungranted request; it cannot release live ownership. */
		public void cancel() {
			synchronized( ResourceReservations.this ) {
				pending.remove( this );
			}
		}
	}

	/** Ownership released only after proven unused or safely finished use. */
	public final class Grant implements AutoCloseable {
		private final Request request;
		private boolean released;

		private Grant( Request request ) {
			this.request = request;
		}

		/**
		 * Idempotently returns exactly this grant, then wakes pending coordinators.
		 * Never call merely because stop was requested or a future was cancelled.
		 */
		@Override
		public void close() {
			List<Runnable> notifications;
			synchronized( ResourceReservations.this ) {
				if( released ) {
					return;
				}
				released = true;
				held.removeAll( request.requirements.keys() );
				if( request.requirements.exclusive() ) {
					exclusive = false;
				}
				active--;
				request.capacity.active--;
				Set<Runnable> wakeups = new LinkedHashSet<>();
				pending.forEach( waiter -> wakeups.add( waiter.changed ) );
				notifications = new ArrayList<>( wakeups );
			}
			notifications.forEach( Runnable::run );
		}
	}
}
