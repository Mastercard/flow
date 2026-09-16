package com.mastercard.test.flow.assrt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Existing fixture-owner operations, without a native adapter or synthetic
 * fixture.
 */
class ContextDomainTest {
	/** Physical affinity rejects the wrong mode or thread before fixture access. */
	@Test
	void affinityChecksActualOwnerAndFreezesCancellationConfiguration() throws Exception {
		ContextDomain domain = new ContextDomain( Thread.currentThread() );
		assertThrows( IllegalStateException.class, () -> domain.checkMode( true ) );
		assertDoesNotThrow( () -> domain.checkMode( false ) );
		assertThrows( IllegalStateException.class, () -> domain.cancellation( operation -> fail() ) );
		onAnotherThread( () -> {
			assertThrows( IllegalStateException.class, () -> domain.checkMode( false ) );
			assertThrows( IllegalStateException.class, domain::tryAcquire );
		} );
		assertNull( domain.uncertainty() );
		try( var use = domain.tryAcquire() ) {
			assertNotNull( use );
		}
		assertDoesNotThrow( () -> new ContextDomain().checkMode( true ) );
	}

	/**
	 * A competing lifecycle owner cannot act; nested borrowing restores the outer
	 * scope.
	 */
	@Test
	void nestedScopesRestoreOwnershipAndRejectForeignActions() throws Exception {
		ContextDomain domain = new ContextDomain();
		AtomicInteger actions = new AtomicInteger();
		try( var outer = domain.tryAcquire() ) {
			assertNotNull( outer );
			onAnotherThread( () -> {
				try( var denied = domain.tryAcquire() ) {
					assertNull( denied );
				}
				assertThrows( IllegalStateException.class, () -> outer.change( actions::incrementAndGet ) );
				assertThrows( IllegalStateException.class, outer::receipt );
			} );
			try( var nested = domain.tryAcquire() ) {
				assertNotNull( nested );
				assertThrows( IllegalStateException.class, () -> outer.change( actions::incrementAndGet ) );
				assertThrows( IllegalStateException.class, outer::receipt );
				nested.change( actions::incrementAndGet );
			}
			outer.change( actions::incrementAndGet );
			assertEquals( 2, actions.get() );
			outer.close();
			outer.close();
			assertThrows( IllegalStateException.class, outer::receipt );
			assertThrows( IllegalStateException.class, () -> outer.change( actions::incrementAndGet ) );
		}
		assertThrows( IllegalStateException.class,
				() -> domain.uncertain( new IllegalStateException( "no current use" ) ) );
		assertNull( domain.uncertainty() );
		try( var next = domain.tryAcquire() ) {
			assertNotNull( next );
			next.change( actions::incrementAndGet );
		}
		assertEquals( 3, actions.get() );
	}

	/**
	 * Late diagnostics cannot poison a newer owner of the same physical fixture.
	 */
	@Test
	void releasedReceiptRejectsEvidenceWithoutAffectingNewOwner() {
		ContextDomain domain = new ContextDomain();
		ContextDomain.Receipt old;
		try( var use = domain.tryAcquire() ) {
			assertNotNull( use );
			old = use.receipt();
		}
		try( var next = domain.tryAcquire() ) {
			assertNotNull( next );
			assertThrows( IllegalStateException.class, old::operation );
			assertThrows( IllegalStateException.class,
					() -> old.uncertain( new IllegalStateException( "late evidence" ) ) );
			assertNull( domain.uncertainty() );
			AtomicInteger actions = new AtomicInteger();
			next.change( actions::incrementAndGet );
			assertEquals( 1, actions.get() );
		}
	}

	private static void onAnotherThread( Executable action ) throws Exception {
		FutureTask<Void> task = new FutureTask<>( () -> {
			try {
				action.execute();
			}
			catch( Throwable failure ) {
				throw new AssertionError( failure );
			}
			return null;
		} );
		Thread thread = new Thread( task, "foreign-fixture-owner" );
		thread.start();
		task.get( 5, TimeUnit.SECONDS );
		thread.join( 1000 );
		assertFalse( thread.isAlive() );
	}
}
