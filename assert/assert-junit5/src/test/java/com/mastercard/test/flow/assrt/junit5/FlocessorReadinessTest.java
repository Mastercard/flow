package com.mastercard.test.flow.assrt.junit5;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Applicator;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.builder.Creator;

/**
 * Exercises readiness-gated leaf emission through {@link Flocessor#tests()}.
 */
@SuppressWarnings("static-method")
class FlocessorReadinessTest {

	/**
	 * Independent work can execute while a prerequisite is held, but its dependent
	 * is not emitted until the prerequisite completes.
	 *
	 * @throws Exception On test coordination failure
	 */
	@Test
	void dependencyIsWithheldUntilCompletion() throws Exception {
		Flow source = flow( "source" );
		Flow dependent = flow( "dependent", source );
		Flow independent = flow( "independent" );
		CountDownLatch sourceStarted = new CountDownLatch( 1 );
		CountDownLatch releaseSource = new CountDownLatch( 1 );
		List<String> bodies = new ArrayList<>();

		Iterator<
				DynamicNode> leaves = new Flocessor( "readiness", model( source, dependent, independent ) )
						.system( State.FUL, Actrs.BEN )
						.behaviour( assertion -> {
							String name = assertion.flow().meta().description();
							if( "source".equals( name ) ) {
								sourceStarted.countDown();
								await( releaseSource );
							}
							bodies.add( name );
							assertion.actual().response( assertion.expected().response().content() );
						} )
						.tests().iterator();

		Map<String, DynamicTest> ready = new ConcurrentHashMap<>();
		for( int i = 0; i < 2; i++ ) {
			DynamicTest leaf = (DynamicTest) leaves.next();
			ready.put( leaf.getDisplayName(), leaf );
		}
		assertEquals( Set.of( "source []", "independent []" ), ready.keySet() );

		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			Future<?> sourceRun = executor.submit( () -> execute( ready.get( "source []" ) ) );
			assertTrue( sourceStarted.await( 10, TimeUnit.SECONDS ), "source did not start" );
			Future<DynamicNode> nextLeaf = executor.submit( leaves::next );
			assertThrows( TimeoutException.class, () -> nextLeaf.get( 100, TimeUnit.MILLISECONDS ) );

			execute( ready.get( "independent []" ) );
			assertEquals( List.of( "independent" ), bodies );

			releaseSource.countDown();
			sourceRun.get( 10, TimeUnit.SECONDS );
			DynamicTest dependentLeaf = (DynamicTest) nextLeaf.get( 10, TimeUnit.SECONDS );
			assertEquals( "dependent []", dependentLeaf.getDisplayName() );
			execute( dependentLeaf );
			assertEquals( Set.of( "source", "independent", "dependent" ), Set.copyOf( bodies ) );
			assertFalse( leaves.hasNext() );
		}
		finally {
			releaseSource.countDown();
			executor.shutdownNow();
			assertTrue( executor.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/**
	 * A context-free body cannot enter while another context transition is still
	 * removing the previously applied context.
	 *
	 * @throws Exception On test coordination failure
	 */
	@Test
	void contextTransitionsAreSerialized() throws Exception {
		Flow contextual = contextualFlow( "context" );
		Flow first = flow( "first", contextual );
		Flow second = flow( "second", contextual );
		CountDownLatch removalStarted = new CountDownLatch( 1 );
		CountDownLatch releaseRemoval = new CountDownLatch( 1 );
		AtomicInteger transitions = new AtomicInteger();
		List<String> bodies = Collections.synchronizedList( new ArrayList<>() );

		Iterator<DynamicNode> leaves = new Flocessor( "contexts", model( contextual, first, second ) )
				.system( State.LESS, Actrs.BEN )
				.applicators( new Applicator<TestContext>( TestContext.class, 1 ) {
					@Override
					public Comparator<TestContext> order() {
						return Comparator.comparing( Context::name );
					}

					@Override
					public void transition( TestContext from, TestContext to ) {
						transitions.incrementAndGet();
						if( from != null && to == null ) {
							removalStarted.countDown();
							await( releaseRemoval );
						}
					}
				} )
				.behaviour( assertion -> {
					bodies.add( assertion.flow().meta().description() );
					assertion.actual().response( assertion.expected().response().content() );
				} )
				.tests().iterator();

		execute( (DynamicTest) leaves.next() );
		DynamicTest firstFree = (DynamicTest) leaves.next();
		DynamicTest secondFree = (DynamicTest) leaves.next();
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			Future<?> firstRun = executor.submit( () -> execute( firstFree ) );
			assertTrue( removalStarted.await( 10, TimeUnit.SECONDS ), "context removal did not start" );
			Future<?> secondRun = executor.submit( () -> execute( secondFree ) );
			assertThrows( TimeoutException.class, () -> secondRun.get( 100, TimeUnit.MILLISECONDS ) );

			releaseRemoval.countDown();
			firstRun.get( 10, TimeUnit.SECONDS );
			secondRun.get( 10, TimeUnit.SECONDS );
			assertEquals( 2, transitions.get() );
			assertEquals( Set.of( "context", "first", "second" ), Set.copyOf( bodies ) );
		}
		finally {
			releaseRemoval.countDown();
			executor.shutdownNow();
			assertTrue( executor.awaitTermination( 10, TimeUnit.SECONDS ) );
		}
	}

	/** Interrupted producer waits fail clearly and preserve the interrupt flag. */
	@Test
	void interruptedProducerWait() throws Exception {
		Flow source = flow( "source" );
		Flow dependent = flow( "dependent", source );
		Iterator<DynamicNode> leaves = new Flocessor( "interrupted", model( source, dependent ) )
				.system( State.LESS, Actrs.BEN )
				.behaviour( assertion -> assertion.actual().response(
						assertion.expected().response().content() ) )
				.tests().iterator();
		assertEquals( "source []", leaves.next().getDisplayName() );

		List<Throwable> failures = Collections.synchronizedList( new ArrayList<>() );
		List<Boolean> interrupted = Collections.synchronizedList( new ArrayList<>() );
		Thread producer = new Thread( () -> {
			try {
				leaves.next();
			}
			catch( Throwable throwable ) {
				failures.add( throwable );
				interrupted.add( Thread.currentThread().isInterrupted() );
			}
		}, "flow-stream-producer" );
		producer.start();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos( 10 );
		while( producer.getState() != Thread.State.WAITING ) {
			assertTrue( System.nanoTime() < deadline, "producer did not wait: " + producer.getState() );
			Thread.onSpinWait();
		}
		producer.interrupt();
		producer.join( 10_000 );

		assertFalse( producer.isAlive() );
		assertEquals( List.of( true ), interrupted );
		assertEquals( 1, failures.size() );
		assertTrue( failures.get( 0 ) instanceof IllegalStateException );
		assertTrue( failures.get( 0 ).getMessage().contains( "source []" ), failures::toString );
		assertTrue( failures.get( 0 ).getCause() instanceof InterruptedException );
	}

	private static void execute( DynamicTest test ) {
		try {
			test.getExecutable().execute();
		}
		catch( Throwable throwable ) {
			throw new IllegalStateException( throwable );
		}
	}

	private static void await( CountDownLatch latch ) {
		try {
			if( !latch.await( 10, TimeUnit.SECONDS ) ) {
				throw new IllegalStateException( "Timed out waiting for test coordination" );
			}
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException( e );
		}
	}

	private static Flow flow( String name, Flow... prerequisites ) {
		return Creator.build( f -> {
			f.meta( m -> m.description( name ) )
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
							.request( new Msg( "request" ) ).response( new Msg( "response" ) ) );
			for( Flow prerequisite : prerequisites ) {
				f.prerequisite( prerequisite );
			}
		} );
	}

	private static Flow contextualFlow( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.context( new TestContext( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) ) );
	}

	private static Model model( Flow... flows ) {
		Model model = mock( Model.class );
		when( model.flows( anySet(), anySet() ) ).thenAnswer( invocation -> Stream.of( flows ) );
		return model;
	}

	private static final class TestContext implements Context {
		private final String name;

		private TestContext( String name ) {
			this.name = name;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Set<Actor> domain() {
			return Set.of( Actrs.BEN );
		}

		@Override
		public Context child() {
			return new TestContext( name );
		}
	}
}
