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
 * unused grant. Older pending work protects its entire set against newer
 * conflicting requests, without partially holding keys or capacity.
 * Reservations do not transfer fixture lifecycle ownership.
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
	public Request register( Capacity capacity, ResourceRequirements requirements,
			Runnable changed ) {
		return register( capacity, List.of( requirements ), changed ).get( 0 );
	}

	/**
	 * Publishes a jointly ready cohort atomically, in canonical tie order.
	 * Insertion into the shared pending set is the scope-wide readiness
	 * linearization point; unpublished work has no priority. Call outside the run
	 * lock, before exposing any member of the cohort to admission.
	 *
	 * @param capacity     Execution owner for the cohort
	 * @param requirements Immutable requirements in canonical order
	 * @param changed      Short nonthrowing notification, delivered outside locks
	 * @return Stable requests in the supplied order
	 */
	public List<Request> register( Capacity capacity, List<ResourceRequirements> requirements,
			Runnable changed ) {
		Objects.requireNonNull( capacity );
		Objects.requireNonNull( changed );
		List<Request> requests = List.copyOf( requirements ).stream()
				.map( requirement -> new Request( capacity, requirement, changed ) ).toList();
		synchronized( this ) {
			pending.addAll( requests );
		}
		return requests;
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
				for( Request older : pending ) {
					if( older == this )
						break;
					if( older.requirements.exclusive() || requirements.exclusive()
							|| older.requirements.keys().stream().anyMatch( requirements.keys()::contains ) )
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
			List<Runnable> notifications;
			synchronized( ResourceReservations.this ) {
				if( !pending.remove( this ) )
					return;
				notifications = notifications();
			}
			notifications.forEach( Runnable::run );
		}
	}

	private List<Runnable> notifications() {
		Set<Runnable> wakeups = new LinkedHashSet<>();
		pending.forEach( waiter -> wakeups.add( waiter.changed ) );
		return new ArrayList<>( wakeups );
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
				notifications = notifications();
			}
			notifications.forEach( Runnable::run );
		}
	}
}
