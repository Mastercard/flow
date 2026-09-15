package com.mastercard.test.flow.assrt.junit5;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Assertion;
import com.mastercard.test.flow.assrt.junit5.mock.Actrs;
import com.mastercard.test.flow.assrt.junit5.mock.Mdl;
import com.mastercard.test.flow.assrt.junit5.mock.Msg;
import com.mastercard.test.flow.assrt.resource.ResourceRequirements;
import com.mastercard.test.flow.assrt.resource.ResourceRules;
import com.mastercard.test.flow.assrt.resource.ResourceReservations;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Request;
import com.mastercard.test.flow.assrt.resource.ResourceReservations.Grant;
import com.mastercard.test.flow.builder.Creator;

/**
 * Real factory/Launcher seam with independent native pools and fake shared SUT
 * state.
 */
class NativeResourceAdmissionTest {
	private static final Map<String, Run> RUNS = new ConcurrentHashMap<>();
	private static final AtomicInteger IDS = new AtomicInteger();
	private static final ThreadLocal<Run> FACTORY = new ThreadLocal<>();

	/**
	 * B has no prerequisite or resource wait of its own, but cannot overtake A's
	 * synchronous publication. D proves the factory can make independent progress.
	 *
	 * @param shape Overlapping fields or distinct messages within the destination
	 * @throws Exception If a real Launcher fails to drain
	 */
	@ParameterizedTest
	@ValueSource(strings = { "same-field", "different-fields", "parent-child", "different-messages" })
	void canonicalPublishersMatchSerialValuesWhenBIsReadyBeforeA( String shape ) throws Exception {
		List<List<String>> observations = new ArrayList<>();
		for( boolean parallel : List.of( false, true ) ) {
			boolean fields = shape.equals( "different-fields" ) || shape.equals( "parent-child" );
			String first = fields ? shape.equals( "parent-child" ) ? "left=A,right=A" : "left=A" : "A";
			String second = fields ? "right=B" : "B";
			Flow a = ParallelBindingFixture.create( "A", first );
			Flow b = ParallelBindingFixture.create( "B", second );
			Map<Flow, Thread> callers = new ConcurrentHashMap<>();
			List<String> writes = new CopyOnWriteArrayList<>();
			Flow c = Creator.build( f -> {
				f.meta( m -> m.description( "C" ) ).call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Fields( fields ? "left=old,right=old" : "pending" ) )
						.response( new ParallelBindingFixture.Text( "response" ) ) );
				for( Flow source : List.of( a, b ) ) {
					for( int binding = 0; binding < 2; binding++ ) {
						f.dependency( source, d -> d.from( i -> true,
								RESPONSE, ".+" )
								.mutate( value -> {
									assertSame( callers.get( source ), Thread.currentThread() );
									writes.add( value.toString() );
									return value;
								} )
								.to( i -> true,
										shape.equals( "different-messages" ) && source == b ? RESPONSE : REQUEST,
										fields && source == b ? "right=[^,]+"
												: shape.equals( "different-fields" ) ? "left=[^,]+" : ".+" ) );
					}
				}
			} );
			c.root().request().set( ".+", fields ? "left=old,right=old" : "pending" );
			c.root().response().set( ".+", "response" );
			CountDownLatch independent = new CountDownLatch( 1 );
			Run run = new Run( parallel, List.of( c, b, a, flow( "D" ) ), r -> r
					.independent( "audited model messages and synchronous callbacks", f -> true )
					.resources( "A's own prerequisite resource", f -> f == a, "native-publication-A" ),
					x -> {
						callers.put( x.flow(), Thread.currentThread() );
						if( x.flow() == c ) {
							assertEquals(
									fields ? "left=A,right=B" : shape.equals( "different-messages" ) ? "A" : "B",
									x.expected().request().assertable() );
							assertEquals( shape.equals( "different-messages" ) ? "B" : "response",
									x.expected().response().assertable() );
						}
						if( named( x.flow(), "D" ) )
							independent.countDown();
					} );
			var rules = new ResourceRules().resources( "external fixture owner", f -> true,
					"native-publication-A" );
			var scope = ResourceReservations.shared();
			var holder = scope.register( scope.capacity( 1 ), rules.resolve( a ), () -> {
			} );
			Grant held = parallel ? holder.tryAcquire() : null;
			Throwable primary = null;
			try( held ) {
				if( parallel )
					assertNotNull( held );
				run.start();
				if( parallel ) {
					await( independent );
					assertEquals( 0, b.dependencies().count(), "B has no genuine prerequisite" );
					assertFalse( run.registered.contains( "A []" ), "A's own resource is held" );
					assertFalse( run.registered.contains( "B []" ), "ready B must not even be emitted" );
					assertEquals( List.of(), writes );
				}
			}
			catch( Throwable failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				try {
					holder.cancel();
				}
				catch( Throwable cleanup ) {
					if( primary == null ) {
						primary = cleanup;
						throw cleanup;
					}
					if( primary != cleanup )
						primary.addSuppressed( cleanup );
				}
				finally {
					try {
						run.finish();
					}
					catch( Throwable cleanup ) {
						if( primary == null )
							throw cleanup;
						if( primary != cleanup )
							primary.addSuppressed( cleanup );
					}
				}
			}
			assertEquals( List.of( first, first, second, second ), writes,
					"every binding runs exactly once" );
			assertEquals( 4, c.dependencies().count() );
			assertDoesNotThrow( run.handle::close );
			observations.add( List.copyOf( writes ) );
		}
		assertEquals( observations.get( 0 ), observations.get( 1 ) );
	}

	/**
	 * Fixture message with observable overlapping regex fields and no hidden
	 * backing aliases.
	 */
	private static final class Fields extends ParallelBindingFixture.Text {
		private Fields( String value ) {
			super( value );
		}

		@Override
		public Message child() {
			return new Fields( assertable() );
		}

		@Override
		public Message set( String field, Object value ) {
			return super.set( ".+", assertable().replaceAll( field,
					java.util.regex.Matcher.quoteReplacement( value.toString() ) ) );
		}
	}

	/**
	 * C's source has finished, but B still owns the same message through D. C must
	 * not read that alias until B's native publication has drained.
	 *
	 * @throws Exception If the real Launcher fails to drain
	 */
	@Test
	void sharedDestinationReadersCannotOverlapAnotherDestinationsWriter() throws Exception {
		for( boolean parallel : List.of( false, true ) ) {
			Flow a = ParallelBindingFixture.create( "A", "A" );
			Flow b = ParallelBindingFixture.create( "B", "B" );
			var shared = new ParallelBindingFixture.Text( "pending" ) {
				@Override
				public com.mastercard.test.flow.Message child() {
					return this;
				}
			};
			CountDownLatch writing = new CountDownLatch( 1 );
			CountDownLatch release = new CountDownLatch( 1 );
			CountDownLatch independent = new CountDownLatch( 1 );
			List<String> reads = new CopyOnWriteArrayList<>();
			List<Flow> flows = new ArrayList<>( List.of( a, b ) );
			for( Flow source : List.of( a, b ) ) {
				flows.add( Creator.build( f -> f.meta( m -> m.description( source == a ? "C" : "D" ) )
						.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( shared )
								.response( new ParallelBindingFixture.Text( "response" ) ) )
						.dependency( source, d -> d.from( i -> true,
								com.mastercard.test.flow.util.Transmission.Type.RESPONSE, ".+" )
								.mutate( value -> {
									if( parallel && source == b ) {
										writing.countDown();
										await( release );
									}
									return value;
								} ).to( i -> true, com.mastercard.test.flow.util.Transmission.Type.REQUEST,
										".+" ) ) ) );
			}
			assertSame( flows.get( 2 ).root().request(), flows.get( 3 ).root().request() );
			flows.add( flow( "E" ) );
			Run run = new Run( parallel, flows,
					r -> r.independent( "known message aliases; otherwise isolated synchronous use",
							f -> true ),
					x -> {
						if( named( x.flow(), "C" ) || named( x.flow(), "D" ) )
							reads
									.add( x.flow().meta().description() + ":" + x.expected().request().assertable() );
						if( named( x.flow(), "E" ) ) {
							if( parallel )
								await( writing );
							independent.countDown();
						}
					} );
			Throwable primary = null;
			try {
				run.start();
				if( parallel ) {
					await( independent );
					assertEquals( List.of(), reads );
					assertFalse( run.registered.contains( "C []" ), "producer-only A->B is insufficient" );
					assertFalse( run.registered.contains( "D []" ) );
				}
			}
			catch( Throwable failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				release.countDown();
				try {
					run.finish();
				}
				catch( Throwable cleanup ) {
					if( primary == null )
						throw cleanup;
					if( primary != cleanup )
						primary.addSuppressed( cleanup );
				}
			}
			assertEquals( List.of( "C:B", "D:B" ), reads );
			assertDoesNotThrow( run.handle::close );
		}
	}

	/**
	 * A participates in both destination groups, but B and C do not conflict with
	 * each other. Real publication must preserve both groups without a component
	 * barrier or moving callbacks off their original producing threads.
	 *
	 * @throws Exception If the real Launcher fails to drain
	 */
	@Test
	void multiDestinationProducerPreservesSerialValuesWithoutSerializingItsComponent()
			throws Exception {
		for( boolean parallel : List.of( false, true ) ) {
			Map<String, Thread> callers = new ConcurrentHashMap<>();
			Map<String, List<String>> operations = new ConcurrentHashMap<>();
			AtomicBoolean armed = new AtomicBoolean();
			List<Flow> producers = new ArrayList<>();
			for( String name : List.of( "A", "B", "C" ) ) {
				operations.put( name, new CopyOnWriteArrayList<>() );
				var response = new ParallelBindingFixture.Text( name ) {
					@Override
					public Message child() {
						return this;
					}

					@Override
					public Message peer( byte[] bytes ) {
						assertSame( callers.get( name ), Thread.currentThread() );
						operations.get( name ).add( "peer" );
						return new ParallelBindingFixture.Text( new String( bytes,
								java.nio.charset.StandardCharsets.UTF_8 ) ) {
							@Override
							public Object get( String field ) {
								assertSame( callers.get( name ), Thread.currentThread() );
								operations.get( name ).add( "get" );
								return super.get( field );
							}
						};
					}
				};
				producers.add( Creator.build( f -> f.meta( m -> m.description( name ) )
						.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
								.request( new ParallelBindingFixture.Text( "request" ) ).response( response ) ) ) );
			}
			Flow a = producers.get( 0 );
			List<Flow> destinations = new ArrayList<>();
			for( String destination : List.of( "X", "Y" ) ) {
				var request = new ParallelBindingFixture.Text( "pending" ) {
					@Override
					public Message child() {
						return this;
					}

					@Override
					public Message set( String field, Object value ) {
						if( armed.get() ) {
							assertSame( callers.get( value ), Thread.currentThread() );
							operations.get( value ).add( "set:" + destination );
						}
						return super.set( field, value );
					}
				};
				destinations.add( Creator.build( f -> {
					f.meta( m -> m.description( destination ) )
							.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN ).request( request )
									.response( new ParallelBindingFixture.Text( "response" ) ) );
					for( Flow source : List.of( a, producers.get( destination.equals( "X" ) ? 1 : 2 ) ) )
						for( int binding = 0; binding < 2; binding++ )
							f.dependency( source, d -> d.from( i -> true, RESPONSE, ".+" )
									.mutate( value -> {
										String name = source.meta().description();
										assertSame( callers.get( name ), Thread.currentThread() );
										operations.get( name ).add( "mutation:" + destination );
										return value;
									} ).to( i -> true, REQUEST, ".+" ) );
				} ) );
				request.set( ".+", "pending" );
			}
			armed.set( true );
			CountDownLatch independent = new CountDownLatch( 1 );
			CountDownLatch successors = new CountDownLatch( 2 );
			CountDownLatch release = new CountDownLatch( 1 );
			List<String> reads = new CopyOnWriteArrayList<>();
			List<Flow> flows = new ArrayList<>( destinations );
			flows.addAll( List.of( producers.get( 2 ), producers.get( 1 ), a ) );
			flows.add( flow( "D" ) );
			Run run = new Run( parallel, flows, r -> r
					.independent( "owned messages and synchronous callbacks", f -> true )
					.resources( "A's own resource", f -> f == a, "multi-destination-A" ), x -> {
						String name = x.flow().meta().description();
						assertNull( callers.put( name, Thread.currentThread() ), "one body per flow" );
						if( name.equals( "D" ) )
							independent.countDown();
						if( parallel && (name.equals( "B" ) || name.equals( "C" )) ) {
							successors.countDown();
							await( release );
						}
						if( name.equals( "X" ) || name.equals( "Y" ) ) {
							assertEquals( name.equals( "X" ) ? "B" : "C",
									x.expected().request().assertable() );
							reads.add( name + ":" + x.expected().request().assertable() );
						}
					} );
			var rules = new ResourceRules().resources( "external fixture owner", f -> true,
					"multi-destination-A" );
			var scope = ResourceReservations.shared();
			var holder = scope.register( scope.capacity( 1 ), rules.resolve( a ), () -> {
			} );
			Grant held = parallel ? holder.tryAcquire() : null;
			Throwable primary = null;
			try( held ) {
				if( parallel )
					assertNotNull( held );
				run.start();
				if( parallel ) {
					await( independent );
					assertEquals( List.of( "D []" ), run.registered );
					assertTrue( operations.values().stream().allMatch( List::isEmpty ) );
					assertEquals( List.of( "pending", "pending" ), destinations.stream()
							.map( f -> f.root().request().assertable() ).toList() );
					held.close();
					await( successors );
					assertEquals( List.of( "A", "A" ), destinations.stream()
							.map( f -> f.root().request().assertable() ).toList(),
							"both successors enter after A, before either publishes" );
					assertEquals( List.of(), reads );
				}
			}
			catch( Throwable failure ) {
				primary = failure;
				throw failure;
			}
			finally {
				release.countDown();
				try {
					holder.cancel();
				}
				catch( Throwable cleanup ) {
					if( primary == null ) {
						primary = cleanup;
						throw cleanup;
					}
					if( primary != cleanup )
						primary.addSuppressed( cleanup );
				}
				finally {
					try {
						run.finish();
					}
					catch( Throwable cleanup ) {
						if( primary == null )
							throw cleanup;
						if( primary != cleanup )
							primary.addSuppressed( cleanup );
					}
				}
			}
			assertEquals( List.of( "X:B", "Y:C" ), reads.stream().sorted().toList() );
			assertEquals( List.of( "peer", "get", "mutation", "set", "get", "mutation", "set",
					"get", "mutation", "set", "get", "mutation", "set" ),
					operations.get( "A" ).stream()
							.map( operation -> operation.replaceAll( ":[XY]", "" ) ).toList() );
			for( String destination : List.of( "X", "Y" ) )
				assertEquals( List.of( "mutation:" + destination, "set:" + destination,
						"mutation:" + destination, "set:" + destination ),
						operations.get( "A" ).stream()
								.filter( operation -> operation.endsWith( ":" + destination ) ).toList() );
			assertEquals( List.of( "peer", "get", "mutation:X", "set:X", "get", "mutation:X", "set:X" ),
					operations.get( "B" ) );
			assertEquals( List.of( "peer", "get", "mutation:Y", "set:Y", "get", "mutation:Y", "set:Y" ),
					operations.get( "C" ) );
			assertEquals( 6, callers.size() );
			assertEquals( 8, destinations.stream().mapToLong( f -> f.dependencies().count() ).sum() );
			assertEquals( 1, run.closes.get() );
			assertDoesNotThrow( run.handle::close );
		}
	}

	/**
	 * Immediate publication errors fail A, not its order-only successor B. The
	 * genuine data consumer C still aborts, with every partial write left intact.
	 *
	 * @param fault The failing synchronous operation
	 * @throws Exception If the actual Launcher fails to drain
	 */
	@ParameterizedTest
	@ValueSource(strings = { "peer", "get", "mutation", "set", "set-after" })
	void publicationFaultsRetainPartialEffectsAndDoNotSuppressOrderOnlySuccessors( String fault )
			throws Exception {
		for( boolean parallel : List.of( false, true ) ) {
			RuntimeException original = new IllegalStateException( "publication " + fault );
			AtomicReference<Thread> caller = new AtomicReference<>();
			AtomicBoolean armed = new AtomicBoolean();
			AtomicInteger gets = new AtomicInteger();
			AtomicInteger mutations = new AtomicInteger();
			AtomicInteger sets = new AtomicInteger();
			List<String> operations = new CopyOnWriteArrayList<>();
			List<String> partial = new CopyOnWriteArrayList<>();
			var sourceMessage = new ParallelBindingFixture.Text( "A" ) {
				@Override
				public com.mastercard.test.flow.Message child() {
					return this;
				}

				@Override
				public com.mastercard.test.flow.Message peer( byte[] bytes ) {
					assertSame( caller.get(), Thread.currentThread() );
					operations.add( "peer" );
					if( fault.equals( "peer" ) )
						throw original;
					return new ParallelBindingFixture.Text( "A" ) {
						@Override
						public Object get( String field ) {
							assertSame( caller.get(), Thread.currentThread() );
							int call = gets.incrementAndGet();
							operations.add( "get" + call );
							if( call == 2 && fault.equals( "get" ) )
								throw original;
							return "A" + call;
						}
					};
				}
			};
			var sinkMessage = new ParallelBindingFixture.Text( "initial" ) {
				@Override
				public com.mastercard.test.flow.Message child() {
					return this;
				}

				@Override
				public com.mastercard.test.flow.Message set( String field, Object value ) {
					if( !armed.get() || !value.toString().startsWith( "A" ) )
						return super.set( field, value );
					assertSame( caller.get(), Thread.currentThread() );
					int call = sets.incrementAndGet();
					operations.add( "set" + call );
					if( call == 2 && fault.equals( "set" ) )
						throw original;
					super.set( field, value );
					if( call == 2 && fault.equals( "set-after" ) )
						throw original;
					return this;
				}
			};
			Flow a = Creator.build( f -> f.meta( m -> m.description( "A" ) )
					.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
							.request( new ParallelBindingFixture.Text( "request" ) )
							.response( sourceMessage ) ) );
			Flow b = ParallelBindingFixture.create( "B", "B" );
			Flow c = Creator.build( f -> {
				f.meta( m -> m.description( "C" ) ).call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( sinkMessage ).response( new ParallelBindingFixture.Text( "response" ) ) );
				for( int binding = 0; binding < 3; binding++ )
					f.dependency( a, d -> d.from( i -> true,
							com.mastercard.test.flow.util.Transmission.Type.RESPONSE, ".+" ).mutate( value -> {
								assertSame( caller.get(), Thread.currentThread() );
								int call = mutations.incrementAndGet();
								operations.add( "mutation" + call );
								if( call == 2 && fault.equals( "mutation" ) )
									throw original;
								return value;
							} ).to( i -> true, com.mastercard.test.flow.util.Transmission.Type.REQUEST, ".+" ) );
				f.dependency( b, d -> d.from( i -> true,
						com.mastercard.test.flow.util.Transmission.Type.RESPONSE, ".+" ).mutate( value -> {
							partial.add( sinkMessage.assertable() );
							return value;
						} ).to( i -> true, com.mastercard.test.flow.util.Transmission.Type.REQUEST, ".+" ) );
			} );
			sinkMessage.set( ".+", "initial" );
			armed.set( true );
			Run run = new Run( parallel, List.of( c, b, a, flow( "D" ) ),
					r -> r.independent( "audited synchronous fault fixture", f -> true ), x -> {
						if( x.flow() == a )
							caller.set( Thread.currentThread() );
					} );
			run.start();
			run.awaitCompletion();
			assertEquals( 4, run.getSummary().getTestsStartedCount() );
			assertEquals( 2, run.getSummary().getTestsSucceededCount() );
			assertEquals( 1, run.getSummary().getTestsFailedCount() );
			assertEquals( 1, run.getSummary().getTestsAbortedCount() );
			assertEquals( 3, run.bodies.get(), "B and independent D enter; genuine consumer C does not" );
			var failure = run.getSummary().getFailures().get( 0 );
			assertEquals( "A []", failure.getTestIdentifier().getDisplayName() );
			assertSame( original, failure.getException().getCause().getCause() );
			assertEquals( List.of( fault.equals( "peer" ) ? "initial"
					: fault.equals( "set-after" ) ? "A2" : "A1" ), partial );
			assertEquals( "B", sinkMessage.assertable(), "eligible B publishes after A's failure" );
			assertEquals( 0, b.dependencies().count(), "order-only must not enter History" );
			assertEquals( 4, c.dependencies().count() );
			List<String> expected = switch( fault ) {
				case "peer" -> List.of( "peer" );
				case "get" -> List.of( "peer", "get1", "mutation1", "set1", "get2" );
				case "mutation" -> List.of( "peer", "get1", "mutation1", "set1", "get2", "mutation2" );
				default -> List.of( "peer", "get1", "mutation1", "set1", "get2", "mutation2", "set2" );
			};
			assertEquals( expected, operations );
			assertDoesNotThrow( run.handle::close );
		}
	}

	/**
	 * Jupiter may fill the logical window or execute inline first. Neither choice
	 * may strand queued work or require another body executor.
	 * 
	 * @throws Exception If the real Launcher fails to drain
	 */
	@Test
	void twelveTargetTwentyCapSupportsQueuedOrInlineWorkWithinTheAdmissionBound() throws Exception {
		CountDownLatch queuedOrInline = new CountDownLatch( 1 );
		CountDownLatch entered = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		AtomicInteger inline = new AtomicInteger();
		Run run = new Run( true, IntStream.range( 0, 40 )
				.mapToObj( i -> flow( "queued-" + i ) ).toList(),
				r -> r.independent( "finite synchronous isolated use", f -> true ), a -> {
					entered.countDown();
					await( release );
				} );
		run.registration = id -> {
			if( run.registered.size() == 24 )
				queuedOrInline.countDown();
		};
		run.inline = () -> {
			inline.incrementAndGet();
			queuedOrInline.countDown();
		};
		Throwable primary = null;
		try {
			run.start();
			await( queuedOrInline );
			await( entered );
			assertTrue( run.registered.size() <= 24, "logical bound is not a guaranteed queue depth" );
			assertTrue( run.bodies.get() < run.registered.size(), "real admitted nodes remain queued" );
			Files.writeString( Files.createDirectories( Path.of( "target", "admission13" ) )
					.resolve( "native-queue.txt" ),
					"target=12, maximum=20, registered=" + run.registered.size()
							+ ", bodies=" + run.bodies.get() + ", inline=" + inline.get() + ", terminal=0\n" );
		}
		catch( Throwable failure ) {
			primary = failure;
			throw failure;
		}
		finally {
			release.countDown();
			try {
				run.finish();
			}
			catch( Throwable cleanup ) {
				if( primary == null )
					throw cleanup;
				if( primary != cleanup )
					primary.addSuppressed( cleanup );
			}
		}
	}

	/**
	 * A parallel request must explain why unaudited work will run serially.
	 *
	 * @throws Exception If the native run fails to finish
	 */
	@Test
	void unclassifiedParallelFlowsReportFallbackBeforeNativeEmission() throws Exception {
		Run run = new Run( true, List.of( flow( "first unknown" ), flow( "second unknown" ) ),
				r -> {
				}, a -> {
				} );
		run.start();
		run.finish();
		assertEquals( 1, run.fallbacks.size(), "one factory diagnostic, not one per flow" );
		String diagnostic = run.fallbacks.get( 0 );
		assertTrue( diagnostic.contains( "2 of 2" ), diagnostic );
		assertTrue( diagnostic.contains( "first unknown []" ), diagnostic );
		assertTrue( diagnostic.contains( "second unknown []" ), diagnostic );
		assertTrue( diagnostic.contains( "UNKNOWN" ), diagnostic );
		assertTrue( diagnostic.contains( "global-exclusive" ), diagnostic );
		assertTrue( diagnostic.contains( "serial" ), diagnostic );
	}

	/**
	 * Explicit classifications are not fallback, and serial compatibility is quiet.
	 *
	 * @param scenario The audit and requested execution mode
	 * @throws Exception If the native run fails to finish
	 */
	@ParameterizedTest
	@ValueSource(strings = { "partial", "classified", "serial", "serial-declared" })
	void fallbackDiagnosticMatchesTheRequestedModeAndAudit( String scenario ) throws Exception {
		Run run = new Run( !scenario.startsWith( "serial" ),
				List.of( flow( "unclassified" ), flow( "empty" ), flow( "named" ), flow( "exclusive" ) ),
				r -> {
					if( !scenario.equals( "serial" ) ) {
						r.independent( "empty", f -> named( f, "empty" ) );
						r.resources( "named", f -> named( f, "named" ), "diagnostic-resource" );
						r.exclusive( "exclusive", f -> named( f, "exclusive" ) );
					}
					if( scenario.equals( "classified" ) ) {
						r.independent( "remaining audit", f -> named( f, "unclassified" ) );
					}
				}, a -> {
				} );
		run.start();
		run.finish();
		if( scenario.equals( "partial" ) ) {
			assertEquals( 1, run.fallbacks.size() );
			String diagnostic = run.fallbacks.get( 0 );
			assertTrue( diagnostic.contains( "1 of 4" ), diagnostic );
			assertTrue( diagnostic.contains( "unclassified []" ), diagnostic );
			assertTrue( diagnostic.contains( "global-exclusive" ), diagnostic );
			assertFalse( diagnostic.contains( "empty []" ), diagnostic );
			assertFalse( diagnostic.contains( "named []" ), diagnostic );
			assertFalse( diagnostic.contains( "exclusive []" ), diagnostic );
			assertFalse( diagnostic.contains( "All selected flows will run serially" ), diagnostic );
		}
		else {
			assertEquals( List.of(), run.fallbacks );
		}
		assertEquals( Set.of( "unclassified []", "empty []", "named []", "exclusive []" ),
				new HashSet<>( run.registered ) );
		run.flows.forEach( f -> assertEquals( Set.of(), f.meta().tags() ) );
	}

	/**
	 * Large unclassified models need one bounded sample, not a full identity dump.
	 *
	 * @throws Exception If the native run fails to finish
	 */
	@Test
	void fallbackDiagnosticBoundsBothFlowCountAndIdentityLength() throws Exception {
		Run run = new Run( true, IntStream.range( 0, 200 )
				.mapToObj( i -> flow( "unclassified-" + i + "-" + "x".repeat( 2000 ) ) ).toList(),
				r -> {
				}, a -> {
				} );
		run.start();
		run.finish();
		assertEquals( 1, run.fallbacks.size() );
		String diagnostic = run.fallbacks.get( 0 );
		assertTrue( diagnostic.length() <= 1024, "output must be bounded despite long identities" );
		assertTrue( diagnostic.contains( "200 of 200" ), diagnostic );
		assertTrue( diagnostic.contains( "unclassified-0-" ), diagnostic );
		assertTrue( diagnostic.contains( "..." ), diagnostic );
		assertTrue( diagnostic.contains( "195 more" ), diagnostic );
		assertFalse( diagnostic.contains( "unclassified-199-" ), diagnostic );
	}

	/**
	 * Equal resource identities exclude each other across independent Launchers.
	 * 
	 * @throws Exception If either run fails to finish
	 */
	@Test
	void equalKeysSerializeAcrossLaunchersWhileDisjointWorkPasses() throws Exception {
		crossRun( true, true, false );
	}

	/**
	 * A blocked union leaves its free key available to unrelated work.
	 * 
	 * @throws Exception If either run fails to finish
	 */
	@Test
	void blockedMultiResourceSetDoesNotHoldItsFreeKey() throws Exception {
		crossRun( true, true, true );
	}

	/**
	 * Serial and parallel factories cooperate through the same default scope.
	 * 
	 * @throws Exception If either run fails to finish
	 */
	@Test
	void preparedSerialAndParallelShareTheSameScopeInBothDirections() throws Exception {
		crossRun( false, true, false );
		crossRun( true, false, false );
	}

	private static void crossRun( boolean firstParallel, boolean secondParallel, boolean multi )
			throws Exception {
		FakeResourceSUT sut = new FakeResourceSUT();
		CountDownLatch held = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		CountDownLatch freeEntered = new CountDownLatch( 1 );
		CountDownLatch blockedEntered = new CountDownLatch( 1 );
		Run first = new Run( firstParallel, List.of( flow( "holder" ) ),
				r -> r.resources( "queue owner", f -> true, "resource-test-B" ),
				a -> sut.use( Set.of( "B" ), () -> {
					held.countDown();
					await( release );
				} ) );
		List<Flow> secondFlows = secondParallel
				? List.of( flow( "blocked" ), flow( "free" ) )
				: List.of( flow( "blocked" ) );
		Run second = new Run( secondParallel, secondFlows, r -> {
			r.resources( "queue alias", f -> named( f, "blocked" ), new String( "resource-test-B" ) );
			if( multi ) {
				r.resources( "account audit", f -> named( f, "blocked" ), "resource-test-A" );
				r.independent( "broad empty cannot erase either key", f -> true );
			}
			r.resources( "isolated A", f -> named( f, "free" ), "resource-test-A" );
		}, a -> {
			if( named( a.flow(), "blocked" ) ) {
				blockedEntered.countDown();
				sut.use( multi ? Set.of( "A", "B" ) : Set.of( "B" ), () -> {
				} );
			}
			else {
				sut.use( Set.of( "A" ), freeEntered::countDown );
			}
		} );
		try {
			first.start();
			await( held );
			second.start();
			await( second.prepared );
			if( secondParallel ) {
				await( freeEntered );
				assertEquals( 1, blockedEntered.getCount(), "busy flow must not pass disjoint work" );
				assertFalse( second.registered.contains( "blocked []" ), "reservation precedes emission" );
			}
			else {
				assertFalse( blockedEntered.await( 150, TimeUnit.MILLISECONDS ) );
			}
		}
		finally {
			release.countDown();
			first.finish();
			second.finish();
		}
		assertNotSame( first.pool, second.pool, "supported separate actual Launcher pools" );
		assertEquals( secondParallel ? 2 : 1, sut.peak );
		assertEquals( 0, blockedEntered.getCount() );
		if( multi ) {
			assertEquals( Set.of( "resource-test-A", "resource-test-B" ),
					second.requirements.get( 0 ).keys() );
		}
	}

	/**
	 * Both unclassified and explicitly exclusive work conflict with known-empty
	 * use.
	 * 
	 * @throws Exception If a run fails to finish
	 */
	@Test
	void unknownAndExplicitExclusiveConflictWithKnownEmptyInBothDirections() throws Exception {
		for( String policy : List.of( "unknown", "exclusive", "empty-first" ) ) {
			CountDownLatch entered = new CountDownLatch( 1 );
			CountDownLatch release = new CountDownLatch( 1 );
			CountDownLatch later = new CountDownLatch( 1 );
			Run first = new Run( true, List.of( flow( "holder" ) ), r -> {
				if( policy.equals( "exclusive" ) ) {
					r.exclusive( "whole fixture reset", f -> true );
					r.independent( "broad empty must not erase reset", f -> true );
				}
				if( policy.equals( "empty-first" ) )
					r.independent( "audited empty", f -> true );
			}, a -> {
				entered.countDown();
				await( release );
			} );
			Run second = new Run( true, List.of( flow( "later" ) ), r -> {
				if( !policy.equals( "empty-first" ) )
					r.independent( "audited empty", f -> true );
			}, a -> later.countDown() );
			first.state = State.LESS;
			try {
				first.start();
				await( entered );
				second.start();
				await( second.prepared );
				assertFalse( later.await( 150, TimeUnit.MILLISECONDS ), policy );
				assertEquals( List.of(), second.registered, policy );
			}
			finally {
				release.countDown();
				first.finish();
				second.finish();
			}
			assertEquals( 0, later.getCount() );
			assertEquals( policy.equals( "unknown" ), first.requirements.get( 0 ).unknown() );
		}
	}

	/**
	 * Keeps grants through native interceptor cleanup, not merely Flow body return.
	 *
	 * @param parallel Whether the holder uses concurrent native execution
	 * @throws Exception If a Launcher does not finish
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void nativeTerminalNotBodyReturnReleasesUse( boolean parallel ) throws Exception {
		for( boolean rejected : List.of( false, true ) ) {
			CountDownLatch cleaning = new CountDownLatch( 1 );
			CountDownLatch releaseCleanup = new CountDownLatch( 1 );
			CountDownLatch later = new CountDownLatch( 1 );
			Run first = new Run( parallel, List.of( flow( "holder" ) ),
					r -> r.resources( "fixture", f -> true, "cleanup-resource" ), a -> {
					} );
			first.cleanup = () -> {
				cleaning.countDown();
				await( releaseCleanup );
			};
			first.rejectBody = rejected;
			Run second = new Run( true, List.of( flow( "later" ) ),
					r -> r.resources( "same fixture", f -> true, "cleanup-resource" ),
					a -> later.countDown() );
			try {
				first.start();
				await( cleaning );
				second.start();
				await( second.prepared );
				assertFalse( later.await( 150, TimeUnit.MILLISECONDS ) );
			}
			finally {
				releaseCleanup.countDown();
				if( rejected ) {
					first.awaitCompletion();
					assertEquals( 0, first.bodies.get() );
					assertEquals( 1, first.getSummary().getTestsFailedCount() );
				}
				else {
					first.finish();
				}
				second.finish();
			}
			assertEquals( 0, later.getCount() );
		}
	}

	/**
	 * Resolves every matching declaration once, including expanded prerequisites.
	 * 
	 * @throws Exception If the native run fails to finish
	 */
	@Test
	void dependencyExpansionResolvesOnceAndKnownUnionKeepsRestrictions() throws Exception {
		Flow prerequisite = flow( "prerequisite" );
		Flow selected = Creator.build( f -> f.meta( m -> m.description( "selected" ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) )
				.prerequisite( prerequisite ) );
		AtomicInteger calls = new AtomicInteger();
		Run run = new Run( true, List.of( prerequisite, selected ), r -> {
			r.exercising( f -> f == selected, message -> {
			} );
			r.resources( "A", f -> {
				calls.incrementAndGet();
				return true;
			}, "audit-A" );
			r.resources( "B", f -> {
				calls.incrementAndGet();
				return true;
			}, "audit-B" );
			r.independent( "empty", f -> {
				calls.incrementAndGet();
				return true;
			} );
		}, a -> {
		} );
		run.start();
		run.finish();
		assertEquals( 6, calls.get() );
		assertEquals( 2, run.requirements.size() );
		for( ResourceRequirements requirement : run.requirements ) {
			assertEquals( Set.of( "audit-A", "audit-B" ), requirement.keys() );
			assertEquals( List.of( "A", "B", "empty" ), requirement.rules().stream().toList() );
			assertFalse( requirement.exclusive() );
		}
		assertEquals( Set.of(), prerequisite.meta().tags() );
		assertEquals( Set.of(), selected.meta().tags() );
	}

	/**
	 * Native rejection remains incomplete, but must not strand unused global or
	 * named ownership.
	 *
	 * @param parallel Whether the native factory uses concurrent execution
	 * @throws Exception If a Launcher does not finish
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void terminalBeforeFlowEntryReturnsOnlyTheProvenUnusedGrant( boolean parallel ) throws Exception {
		for( boolean unknown : List.of( false, true ) ) {
			for( String outcome : List.of( "reject", "skip", "cleanup-throw" ) ) {
				Consumer<PreparedFlocessor> audit = r -> {
					if( !unknown )
						r.resources( "fixture", f -> true, "unused-resource" );
				};
				Run rejected = new Run( parallel, List.of( flow( "rejected" ) ), audit,
						a -> assertEquals( "cleanup-throw", outcome, "no SUT entry" ) );
				rejected.rejectBody = outcome.equals( "reject" );
				rejected.skipBody = outcome.equals( "skip" );
				AtomicInteger cleanups = new AtomicInteger();
				rejected.cleanup = () -> {
					cleanups.incrementAndGet();
					if( outcome.equals( "cleanup-throw" ) )
						throw new IllegalStateException( "native cleanup failure" );
				};
				rejected.start();
				rejected.awaitCompletion();
				assertEquals( 1, rejected.getSummary().getTestsStartedCount() );
				assertEquals( outcome.equals( "skip" ) ? 0 : 1,
						rejected.getSummary().getTestsFailedCount() );
				assertEquals( outcome.equals( "cleanup-throw" ) ? 1 : 0, rejected.bodies.get() );
				assertEquals( 1, cleanups.get() );
				assertFalse( rejected.failures.isEmpty(),
						"native failure/omitted processing stays visible" );
				assertAvailable( unknown ? new String[0] : new String[] { "unused-resource" } );
				Run next = new Run( parallel, List.of( flow( "next" ) ), r -> {
					if( unknown )
						r.independent( "empty after UNKNOWN", f -> true );
					else
						audit.accept( r );
				}, a -> {
				} );
				next.start();
				next.finish();
			}
		}
	}

	/**
	 * Closing a published serial owner before grant prevents later admission and
	 * preserves cleanup failure.
	 *
	 * @param phase Factory return, held native advance, or a race with admission
	 * @throws Exception If a Launcher does not finish
	 */
	@ParameterizedTest
	@ValueSource(strings = { "factory", "advance", "admitting" })
	void disposingSerialAdmissionCannotAcquireAfterTheHolderReleases( String phase )
			throws Exception {
		for( boolean liveClose : List.of( false, true ) ) {
			disposeBeforeGrant( phase, liveClose );
		}
	}

	/**
	 * Coordinates close at a real native consumption edge, never a guessed stack
	 * state.
	 *
	 * @param phase     The native edge to hold or race
	 * @param liveClose Whether to close the live stream instead of the handle
	 * @throws Exception If either Launcher exceeds its deadline
	 */
	private static void disposeBeforeGrant( String phase, boolean liveClose )
			throws Exception {
		CountDownLatch held = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		CountDownLatch returning = new CountDownLatch( 1 );
		CountDownLatch proceed = new CountDownLatch( 1 );
		Run holder = new Run( true, List.of( flow( "holder" ) ),
				r -> r.resources( "held", f -> true, "dispose-resource" ), a -> {
					held.countDown();
					await( release );
				} );
		Run waiting = new Run( false, List.of( flow( "waiting" ) ),
				r -> r.resources( "waiting", f -> true, "dispose-resource" ),
				a -> fail( "disposed body" ) );
		Runnable gate = () -> {
			returning.countDown();
			if( !phase.equals( "admitting" ) )
				await( proceed );
		};
		if( phase.equals( "factory" ) )
			waiting.returning = gate;
		else
			waiting.beforeAdvance = gate;
		IllegalStateException cleanup = new IllegalStateException( "original cleanup failure" );
		waiting.streamCleanup = () -> {
			throw cleanup;
		};
		try {
			holder.start();
			await( held );
			waiting.start();
			await( returning );
			// "admitting" races actual tryAdvance; the public seam cannot prove the
			// exact Object.wait transition. The held phases prove predispatch stop.
			assertNotSame( waiting.original, waiting.liveStream );
			IllegalStateException failure = assertThrows( IllegalStateException.class,
					() -> {
						if( liveClose )
							waiting.liveStream.close();
						else
							waiting.handle.close();
					} );
			assertTrue( failure.getMessage().contains( "Incomplete Flow serial consumption" ) );
			assertEquals( List.of( cleanup ), List.of( failure.getSuppressed() ) );
			proceed.countDown();
			waiting.awaitCompletion();
			assertEquals( 1, release.getCount(),
					"stopped factory must finish without waiting for resource release" );
			assertReservation( false, "dispose-resource" );
		}
		finally {
			proceed.countDown();
			release.countDown();
			holder.finish();
			waiting.awaitCompletion();
		}
		assertEquals( 0, waiting.bodies.get() );
		assertEquals( List.of(), waiting.registered );
		assertEquals( 1, waiting.closes.get() );
		waiting.handle.close();
		waiting.liveStream.close();
		assertEquals( 1, waiting.closes.get() );
		assertFalse( waiting.failures.stream().anyMatch( NullPointerException.class::isInstance ),
				waiting.failures::toString );
		assertAvailable( "dispose-resource" );
		Run next = new Run( false, List.of( flow( "next" ) ),
				r -> r.resources( "reuse", f -> true, "dispose-resource" ), a -> {
				} );
		next.start();
		next.finish();
	}

	private static void assertAvailable( String... keys ) {
		assertReservation( true, keys );
	}

	private static void assertReservation( boolean available, String... keys ) {
		ResourceReservations scope = ResourceReservations.shared();
		Request request = scope.register( scope.capacity( 1 ),
				new ResourceRules().resources( "reuse probe", f -> true, keys ).resolve( null ), () -> {
				} );
		try( Grant grant = request.tryAcquire() ) {
			if( available )
				assertNotNull( grant, "previous run stranded ownership" );
			else
				assertNull( grant, "native cleanup still owns this resource" );
		}
		finally {
			request.cancel();
		}
	}

	/**
	 * Neither close route can release a serial grant or fixture during native
	 * cleanup.
	 * 
	 * @param liveClose Whether to close the retained live stream rather than its
	 *                  owner
	 * @throws Exception If the native run fails to return
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void closingSerialDuringNativeCleanupRetainsOwnershipUntilNativeReturn( boolean liveClose )
			throws Exception {
		CountDownLatch cleaning = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		Run run = new Run( false, List.of( flow( "cleaning" ) ),
				r -> r.resources( "cleanup owner", f -> true, "close-cleanup" ), a -> {
				} );
		run.cleanup = () -> {
			cleaning.countDown();
			await( release );
		};
		try {
			run.start();
			await( cleaning );
			assertEquals( 1, run.bodies.get() );
			assertNotSame( run.original, run.liveStream,
					"close the live native stream, not descriptions" );
			assertThrows( IllegalStateException.class,
					() -> {
						if( liveClose )
							run.liveStream.close();
						else
							run.handle.close();
					} );
			assertEquals( 0, run.closes.get(), "fixture cleanup must not race native use" );
			assertSame( run.requirements.get( 0 ), run.runner.requirements( run.flows.get( 0 ) ),
					"runner must remain attached through native cleanup" );
			assertReservation( false, "close-cleanup" );
			if( liveClose ) {
				run.liveStream.close(); // JDK close handlers are one-shot, even when they throw.
				assertEquals( 0, run.closes.get() );
				assertThrows( IllegalStateException.class, run.handle::close );
				assertReservation( false, "close-cleanup" );
			}
		}
		finally {
			release.countDown();
			run.awaitCompletion();
		}
		assertEquals( 1, run.closes.get() );
		assertFalse( run.failures.isEmpty(), "stop cannot finalize the run as successful" );
		assertAvailable( "close-cleanup" );
		run.handle.close();
		assertEquals( 1, run.closes.get() );
	}

	/**
	 * Resource declarations must not authorize unsupported fixture/report
	 * lifetimes.
	 * 
	 * @throws Exception If a rejected run fails to finish
	 */
	@Test
	void explicitSerialResourcesDoNotAuthorizeUnauditedChainsOrReports() throws Exception {
		Flow chain = Creator.build( f -> f.meta( m -> m.description( "chain" )
				.tags( t -> t.add( "chain:unowned" ) ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) ) );
		for( boolean reporting : List.of( false, true ) ) {
			Run run = new Run( false, List.of( reporting ? flow( "report" ) : chain ), r -> {
				r.resources( "fixture", f -> true, "serial-guard" );
				if( reporting )
					r.reporting( com.mastercard.test.flow.assrt.Reporting.QUIETLY );
			}, a -> fail( "unsupported serial ownership" ) );
			run.start();
			run.awaitCompletion();
			assertEquals( 0, run.bodies.get() );
			assertEquals( 0, run.getSummary().getTestsStartedCount() );
			assertTrue( run.failures.stream().anyMatch( f -> f.toString().contains(
					reporting ? "reporting NEVER" : "context, residue or chain" ) ), run.failures::toString );
		}
	}

	private static Flow flow( String name ) {
		return Creator.build( f -> f.meta( m -> m.description( name ) )
				.call( i -> i.from( Actrs.AVA ).to( Actrs.BEN )
						.request( new Msg( "request" ) ).response( new Msg( "response" ) ) ) );
	}

	private static boolean named( Flow flow, String name ) {
		return flow.meta().description().equals( name );
	}

	private static void await( CountDownLatch latch ) {
		try {
			assertTrue( latch.await( 5, TimeUnit.SECONDS ), "resource fixture coordination timed out" );
		}
		catch( InterruptedException failure ) {
			Thread.currentThread().interrupt();
			throw new AssertionError( failure );
		}
	}

	/**
	 * Fake mutable state detects actual overlapping conflicts, independently of
	 * Flow.
	 */
	static final class FakeResourceSUT {
		private final Set<String> active = new HashSet<>();
		private int users;
		int peak;

		/**
		 * Checks conflicts while executing a fake SUT operation.
		 * 
		 * @param keys   Mutable state used by this operation
		 * @param action Operation, possibly held by a test barrier
		 */
		void use( Set<String> keys, Runnable action ) {
			synchronized( this ) {
				assertTrue( keys.stream().noneMatch( active::contains ), "conflicting SUT use" );
				active.addAll( keys );
				peak = Math.max( peak, ++users );
			}
			try {
				action.run();
			}
			finally {
				synchronized( this ) {
					active.removeAll( keys );
					users--;
				}
			}
		}
	}

	/**
	 * Each task calls a real Launcher; no test body is submitted to this thread.
	 */
	static final class Run extends SummaryGeneratingListener {
		private boolean observed;
		final String id = "resource-run-" + IDS.incrementAndGet();
		final boolean parallel;
		final List<Flow> flows;
		final Consumer<PreparedFlocessor> configure;
		final Consumer<Assertion> body;
		final CountDownLatch prepared = new CountDownLatch( 1 );
		final List<String> registered = new CopyOnWriteArrayList<>();
		final List<Throwable> failures = new CopyOnWriteArrayList<>();
		final List<String> fallbacks = new CopyOnWriteArrayList<>();
		final List<ResourceRequirements> requirements = new ArrayList<>();
		final AtomicInteger bodies = new AtomicInteger();
		private Consumer<TestIdentifier> registration = id -> {
		};
		FutureTask<Void> execution;
		Thread launcherThread;
		Thread factoryThread;
		ForkJoinPool pool;
		Runnable cleanup = () -> {
		};
		private Runnable inline = () -> {
		};
		boolean rejectBody;
		boolean skipBody;
		FlowExecution handle;
		private PreparedFlocessor runner;
		private Stream<?> original;
		private Stream<?> liveStream;
		private Runnable beforeAdvance;
		final AtomicInteger closes = new AtomicInteger();
		Runnable returning = () -> {
		};
		Runnable streamCleanup = () -> {
		};
		State state = State.FUL;

		/**
		 * Defines one independently launched, genuinely native run.
		 * 
		 * @param parallel  Whether to enable the optional Launcher bridge
		 * @param flows     Selected model contents
		 * @param configure Resource declarations and runner options
		 * @param body      Fake SUT behavior on the native invocation thread
		 */
		Run( boolean parallel, List<Flow> flows, Consumer<PreparedFlocessor> configure,
				Consumer<Assertion> body ) {
			this.parallel = parallel;
			this.flows = flows;
			this.configure = configure;
			this.body = body;
		}

		/** Starts only the Launcher caller; Jupiter owns every body invocation. */
		void start() {
			String hook = "junit.platform.launcher.interceptors.enabled";
			String previous = System.getProperty( hook );
			System.setProperty( hook, Boolean.toString( parallel ) );
			var session = LauncherFactory.openSession( LauncherConfig.builder()
					.enableTestExecutionListenerAutoRegistration( false )
					.enableLauncherSessionListenerAutoRegistration( false ).build() );
			if( previous == null )
				System.clearProperty( hook );
			else
				System.setProperty( hook, previous );
			RUNS.put( id, this );
			var request = LauncherDiscoveryRequestBuilder.request()
					.selectors( selectClass( NativeResourceFixture.class ) )
					.configurationParameter( "resource.run", id )
					.configurationParameter( "flow.parallel", Boolean.toString( parallel ) )
					.configurationParameter( "junit.jupiter.execution.parallel.enabled", "true" )
					.configurationParameter( "junit.jupiter.execution.parallel.mode.default", "concurrent" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.strategy", "fixed" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.parallelism",
							"12" )
					.configurationParameter( "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
							"20" )
					.build();
			execution = new FutureTask<>( () -> {
				try( session ) {
					session.getLauncher().execute( request, this );
				}
				finally {
					RUNS.remove( id );
				}
				return null;
			} );
			launcherThread = new Thread( execution, id );
			launcherThread.start();
		}

		/**
		 * Joins the Launcher and checks all expected native and Flow outcomes.
		 * 
		 * @throws Exception If the Launcher fails or exceeds its deadline
		 */
		void finish() throws Exception {
			if( execution == null )
				return;
			awaitCompletion();
			assertEquals( List.of(), failures );
			assertEquals( flows.size(), getSummary().getTestsStartedCount() );
			assertEquals( flows.size(), getSummary().getTestsSucceededCount() );
			assertEquals( flows.size(), bodies.get() );
			assertEquals( 12, pool.getParallelism() );
		}

		/**
		 * Waits for the actual Launcher result, including intentionally failed runs.
		 * 
		 * @throws Exception If the Launcher fails or exceeds its deadline
		 */
		void awaitCompletion() throws Exception {
			execution.get( 10, TimeUnit.SECONDS );
			launcherThread.join( 1000 );
			assertFalse( launcherThread.isAlive(), "Launcher caller did not exit" );
			if( !observed ) {
				observed = true;
				System.out.printf(
						"Resource native %s parallel=%s started=%d succeeded=%d failed=%d aborted=%d bodies=%d%n",
						id, parallel, getSummary().getTestsStartedCount(),
						getSummary().getTestsSucceededCount(),
						getSummary().getTestsFailedCount(), getSummary().getTestsAbortedCount(), bodies.get() );
			}
		}

		@Override
		public void reportingEntryPublished( TestIdentifier id, ReportEntry entry ) {
			super.reportingEntryPublished( id, entry );
			String fallback = entry.getKeyValuePairs().get( "flow.resources.fallback" );
			if( fallback != null ) {
				fallbacks.add( fallback );
				// Launcher listeners can swallow assertion failures: record and check
				// these observations after execute returns instead.
				if( !id.isContainer() || !id.getDisplayName().equals( "flows(FlowExecution)" )
						|| !registered.isEmpty() || bodies.get() != 0 ) {
					failures.add( new AssertionError( "Fallback must precede native emission and SUT use: "
							+ id + ", registered=" + registered + ", bodies=" + bodies.get() ) );
				}
			}
		}

		@Override
		public void dynamicTestRegistered( TestIdentifier testIdentifier ) {
			super.dynamicTestRegistered( testIdentifier );
			registered.add( testIdentifier.getDisplayName() );
			registration.accept( testIdentifier );
		}

		@Override
		public void executionFinished( TestIdentifier id, TestExecutionResult result ) {
			super.executionFinished( id, result );
			result.getThrowable().ifPresent( failures::add );
		}
	}

	/** Uses public native contexts to select the run and hold post-body cleanup. */
	public static final class FixtureContext implements InvocationInterceptor {
		@Override
		@SuppressWarnings("unchecked")
		public <T> T interceptTestFactoryMethod( Invocation<T> invocation,
				ReflectiveInvocationContext<Method> method, ExtensionContext context ) throws Throwable {
			Run run = RUNS.get( context.getConfigurationParameter( "resource.run" ).orElseThrow() );
			FACTORY.set( run );
			try {
				T returned = invocation.proceed();
				run.liveStream = (Stream<?>) returned;
				assertNotSame( run.original, run.liveStream,
						"outer interceptor receives live consumption" );
				run.returning.run();
				if( run.beforeAdvance != null ) {
					Spliterator<DynamicNode> nativeSource = ((Stream<DynamicNode>) returned).spliterator();
					return (T) StreamSupport.stream(
							new Spliterators.AbstractSpliterator<DynamicNode>( Long.MAX_VALUE,
									Spliterator.ORDERED | Spliterator.NONNULL ) {
								@Override
								public boolean tryAdvance( Consumer<? super DynamicNode> action ) {
									assertSame( run.factoryThread, Thread.currentThread() );
									run.beforeAdvance.run();
									return nativeSource.tryAdvance( action );
								}
							}, false ).onClose( run.liveStream::close );
				}
				return returned;
			}
			finally {
				FACTORY.remove();
			}
		}

		@Override
		public void interceptDynamicTest( Invocation<Void> invocation,
				DynamicTestInvocationContext dynamic, ExtensionContext context ) throws Throwable {
			Run run = RUNS.get( context.getConfigurationParameter( "resource.run" ).orElseThrow() );
			assertEquals( run.parallel ? ExecutionMode.CONCURRENT : ExecutionMode.SAME_THREAD,
					context.getExecutionMode() );
			try {
				if( run.rejectBody )
					throw new IllegalStateException( "native rejection before Flow entry" );
				if( run.skipBody )
					invocation.skip();
				else
					invocation.proceed();
			}
			finally {
				run.cleanup.run();
			}
		}
	}

	/**
	 * Configures the real prepared runner for this factory's independent launch.
	 * 
	 * @param execution Public handle published for lifecycle tests
	 * @return Original descriptions with counted fixture cleanup
	 */
	static Stream<DynamicNode> prepare( FlowExecution execution ) {
		Run run = FACTORY.get();
		run.handle = execution;
		run.factoryThread = Thread.currentThread();
		run.pool = ForkJoinTask.getPool();
		PreparedFlocessor runner = execution.flocessor( "resource admission", new Mdl() {
			@Override
			public Stream<Flow> flows( Set<String> include, Set<String> exclude ) {
				return run.flows.stream();
			}
		} ).system( run.state, Actrs.BEN ).behaviour( a -> {
			assertSame( run.pool, ForkJoinTask.getPool(), "no second body executor" );
			if( !run.parallel )
				assertSame( run.factoryThread, Thread.currentThread() );
			run.bodies.incrementAndGet();
			if( run.parallel && Thread.currentThread() == run.factoryThread )
				run.inline.run();
			run.body.accept( a );
			a.actual().response( a.expected().response().content() );
		} );
		run.runner = runner;
		run.configure.accept( runner );
		Stream<DynamicNode> tests = runner.tests();
		assertThrows( IllegalStateException.class, runner::tests );
		run.flows.forEach( f -> run.requirements.add( runner.requirements( f ) ) );
		assertThrows( IllegalStateException.class, () -> runner.resources( "late", f -> true, "x" ) );
		assertThrows( IllegalStateException.class, () -> runner.exclusive( "late", f -> true ) );
		run.prepared.countDown();
		Stream<DynamicNode> original = tests.onClose( () -> {
			run.closes.incrementAndGet();
			run.streamCleanup.run();
		} );
		run.original = original;
		return original;
	}
}

/**
 * Sole top-level factory, reused only through distinct supported Launcher
 * pools.
 */
@ExtendWith(NativeResourceAdmissionTest.FixtureContext.class)
@FlowTest
class NativeResourceFixture {
	/**
	 * Supplies the sole native Flow factory selected by the fixture Launcher.
	 * 
	 * @param execution Injected model-free owner
	 * @return Prepared native descriptions
	 */
	@TestFactory
	Stream<DynamicNode> flows( FlowExecution execution ) {
		return NativeResourceAdmissionTest.prepare( execution );
	}
}
