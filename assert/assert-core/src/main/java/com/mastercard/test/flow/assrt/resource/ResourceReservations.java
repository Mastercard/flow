package com.mastercard.test.flow.assrt.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private final Map<Object, Retention> unsafe = new LinkedHashMap<>();
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
		private final Set<Grant> grants = new LinkedHashSet<>();
		private volatile int active;
		private volatile Throwable unsafe;

		private Capacity( int limit ) {
			this.limit = limit;
		}

		/**
		 * @return First unsafe grant owned by this run, independently of pending work
		 */
		public Throwable uncertainty() {
			return unsafe;
		}

		/** @return Whole grants still owned, including explicitly retained use */
		public int owned() {
			return active;
		}

		/**
		 * Includes operations whose native grant close was requested before Stop.
		 *
		 * @param cause Exact owning run's stop cause
		 */
		public void stopping( Throwable cause ) {
			Objects.requireNonNull( cause );
			List<Grant> owned;
			synchronized( SHARED ) {
				owned = new ArrayList<>( grants );
			}
			signalAll( owned.stream().<Runnable>map( grant -> () -> grant.stopping( cause ) ).toList() );
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
				if( !pending.contains( this ) )
					return null;
				if( capacity.unsafe != null )
					throw new IllegalStateException( "Resource owner has uncertain fixture use",
							capacity.unsafe );
				for( Retention retained : unsafe.values() ) {
					if( conflicts( requirements, retained.requirements ) )
						throw new IllegalStateException(
								"Resource ownership retained after uncertain fixture use",
								retained.cause );
				}
				if( capacity.active == capacity.limit || exclusive
						|| requirements.exclusive() && active != 0
						|| requirements.keys().stream().anyMatch( held::contains ) ) {
					return null;
				}
				for( Request older : pending ) {
					if( older == this )
						break;
					if( conflicts( older.requirements, requirements ) )
						return null;
				}
				pending.remove( this );
				held.addAll( requirements.keys() );
				exclusive = requirements.exclusive();
				active++;
				capacity.active++;
				Grant grant = new Grant( this );
				capacity.grants.add( grant );
				return grant;
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
			signalAll( notifications );
		}
	}

	private List<Runnable> notifications() {
		Set<Runnable> wakeups = new LinkedHashSet<>();
		pending.forEach( waiter -> wakeups.add( waiter.changed ) );
		return new ArrayList<>( wakeups );
	}

	private static void signalAll( List<Runnable> notifications ) {
		Throwable first = null;
		for( Runnable notification : notifications ) {
			try {
				notification.run();
			}
			catch( RuntimeException | Error failure ) {
				if( first == null )
					first = failure;
				else if( first != failure )
					first.addSuppressed( failure );
			}
		}
		if( first instanceof RuntimeException failure )
			throw failure;
		if( first instanceof Error failure )
			throw failure;
	}

	private static boolean conflicts( ResourceRequirements first, ResourceRequirements second ) {
		return first.exclusive() || second.exclusive()
				|| first.keys().stream().anyMatch( second.keys()::contains );
	}

	private record Retention(ResourceRequirements requirements, Throwable cause) {
	}

	/**
	 * Exact outstanding-operation proof. Obtain before use escapes the synchronous
	 * scope. This is deliberately not AutoCloseable: scope exit, cancelled futures
	 * and request return are not evidence that the operation ended.
	 */
	public final class Operation {
		private Grant grant;

		private Operation( Grant grant ) {
			this.grant = grant;
		}

		/**
		 * Reports actual cessation of this operation and its required cleanup. Repeat
		 * proof is harmless. It cannot repair damaged fixture state, finish another
		 * operation, or bypass the grant's native/body/chain close gate.
		 */
		public void complete() {
			List<Runnable> notifications;
			synchronized( ResourceReservations.this ) {
				if( grant == null )
					return;
				Grant completed = grant;
				grant = null;
				completed.operations--;
				if( completed.operations == 0 && completed.failure == null )
					unsafe.remove( completed.identity );
				completed.releaseIfDrained();
				notifications = completed.notifications();
			}
			signalAll( notifications );
		}
	}

	/** Ownership released only after proven unused or safely finished use. */
	public final class Grant implements AutoCloseable {
		private final Request request;
		private final Object identity = new Object();
		private boolean released;
		private boolean closing;
		private volatile int operations;
		private Throwable stopped;
		private Throwable failure;

		private Grant( Request request ) {
			this.request = request;
		}

		/**
		 * @return Exact outstanding operations, readable without nesting owner locks
		 */
		public int operations() {
			return operations;
		}

		/** @return A receipt acquired before this exact owner's operation escapes */
		public Operation operation() {
			synchronized( ResourceReservations.this ) {
				if( released || closing || failure != null || stopped != null )
					throw new IllegalStateException( "Operation requires live safe ownership", failure );
				operations++;
				return new Operation( this );
			}
		}

		/**
		 * Marks outstanding operations explicitly unsafe after observed Stop. No
		 * operation is cancelled or completed here. Call outside owner locks.
		 *
		 * @param cause First owning run's stop cause
		 */
		public void stopping( Throwable cause ) {
			List<Runnable> notifications;
			synchronized( ResourceReservations.this ) {
				Objects.requireNonNull( cause );
				if( released || stopped != null )
					return;
				stopped = cause;
				if( operations == 0 )
					return;
				if( request.capacity.unsafe == null )
					request.capacity.unsafe = cause;
				unsafe.putIfAbsent( identity, new Retention( request.requirements, cause ) );
				notifications = notifications();
			}
			signalAll( notifications );
		}

		private List<Runnable> notifications() {
			Set<Runnable> wakeups = new LinkedHashSet<>();
			wakeups.add( request.changed );
			wakeups.addAll( ResourceReservations.this.notifications() );
			return new ArrayList<>( wakeups );
		}

		/**
		 * Verifies existing ownership without acquiring or waiting for anything.
		 *
		 * @param requirements Complete footprint to be used on the invocation thread
		 */
		public void check( ResourceRequirements requirements ) {
			synchronized( ResourceReservations.this ) {
				if( released || failure != null
						|| requirements.exclusive() && !request.requirements.exclusive()
						|| !request.requirements.keys().containsAll( requirements.keys() ) )
					throw new IllegalStateException( "Fixture does not have a safe complete grant", failure );
			}
		}

		/**
		 * Latches uncertain fixture use and wakes conflicting waiters for diagnosis.
		 * Even a later close cannot release this ownership. No recovery is implied.
		 *
		 * @param cause Original owner-supplied uncertainty evidence
		 * @return The first cause retained for this exact grant
		 */
		public Throwable retain( Throwable cause ) {
			List<Runnable> notifications;
			synchronized( ResourceReservations.this ) {
				Objects.requireNonNull( cause );
				if( released )
					throw new IllegalStateException( "Cannot retain released ownership", cause );
				if( failure != null )
					return failure;
				failure = cause;
				if( request.capacity.unsafe == null )
					request.capacity.unsafe = failure;
				// Retain the ownership diagnosis, not the request's run/model wakeup.
				unsafe.putIfAbsent( identity, new Retention( request.requirements, failure ) );
				Set<Runnable> wakeups = new LinkedHashSet<>();
				// This request is no longer pending. Its owner must stop even when no
				// other request exists; pending-waiter notification alone is insufficient.
				wakeups.add( request.changed );
				wakeups.addAll( ResourceReservations.this.notifications() );
				notifications = new ArrayList<>( wakeups );
			}
			signalAll( notifications );
			return failure;
		}

		/**
		 * Idempotently returns exactly this grant, then wakes pending coordinators.
		 * Never call merely because stop was requested or a future was cancelled.
		 */
		@Override
		public void close() {
			List<Runnable> notifications;
			synchronized( ResourceReservations.this ) {
				if( closing ) {
					return;
				}
				closing = true;
				releaseIfDrained();
				notifications = ResourceReservations.this.notifications();
			}
			signalAll( notifications );
		}

		private void releaseIfDrained() {
			if( !closing || released || failure != null || operations != 0 )
				return;
			released = true;
			unsafe.remove( identity );
			held.removeAll( request.requirements.keys() );
			if( request.requirements.exclusive() )
				exclusive = false;
			active--;
			request.capacity.active--;
			request.capacity.grants.remove( this );
		}
	}
}
