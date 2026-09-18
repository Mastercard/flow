package com.mastercard.test.flow.assrt;

import static com.mastercard.test.flow.assrt.TestModel.Actors.A;
import static com.mastercard.test.flow.assrt.TestModel.Actors.B;
import static com.mastercard.test.flow.assrt.TestModel.Actors.D;
import static com.mastercard.test.flow.util.Transmission.Type.REQUEST;
import static com.mastercard.test.flow.util.Transmission.Type.RESPONSE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Message;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.TestModel.Actors;
import com.mastercard.test.flow.assrt.mock.Mdl;
import com.mastercard.test.flow.assrt.mock.TestContext;
import com.mastercard.test.flow.assrt.mock.TestResidue;
import com.mastercard.test.flow.builder.Creator;
import com.mastercard.test.flow.msg.txt.Text;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Exercises the generic functionality of {@link AbstractFlocessor} via the
 * trivial {@link TestFlocessor} subclass
 */
@SuppressWarnings("static-method")
class AbstractFlocessorTest {
	/** A prepared subclass can reject every inherited fluent mutation. */
	@ParameterizedTest
	@ValueSource(strings = { "reporting", "masking", "system", "autonomous", "applicators",
			"checkers", "logs", "listening", "filtering", "exercising", "behaviour", "motivation" })
	void preparedSubclassGuardsInheritedConfiguration( String setting ) {
		AtomicBoolean prepared = new AtomicBoolean();
		var rejection = new IllegalStateException( "configuration is frozen" );
		Consumer<TestFlocessor> configure = switch( setting ) {
			case "reporting" -> runner -> runner.reporting( Reporting.NEVER );
			case "masking" -> runner -> runner.masking( CheckerTest.Nprdct.DIGITS );
			case "system" -> runner -> runner.system( State.FUL, B );
			case "autonomous" -> runner -> runner.autonomous( B );
			case "applicators" -> runner -> runner.applicators( ApplicatorTest.APPLICATOR );
			case "checkers" -> runner -> runner.checkers( new CheckerTest.TestChecker() );
			case "logs" -> runner -> runner.logs( LogCapture.NO_OP );
			case "listening" -> runner -> runner.listening( new Listener() {
			} );
			case "filtering" -> runner -> runner.filtering( filter -> {
			} );
			case "exercising" -> runner -> runner.exercising( flow -> true, ignored -> {
			} );
			case "behaviour" -> runner -> runner.behaviour( a -> a.actual()
					.response( "B response to A".getBytes( UTF_8 ) ) );
			case "motivation" -> runner -> runner.motivation( ( text, assertion ) -> text );
			default -> throw new AssertionError( setting );
		};
		try( TestFlocessor runner = new TestFlocessor( "guarded configuration", TestModel.abc() ) {
			@Override
			protected void beforeConfiguration() {
				if( prepared.get() )
					throw rejection;
			}
		}.system( State.FUL, B ) ) {
			configure.accept( runner );
			try( var flows = runner.prepareFlows() ) {
				assertEquals( 1, flows.count() );
			}
			prepared.set( true );
			assertSame( rejection,
					assertThrows( IllegalStateException.class, () -> configure.accept( runner ) ) );
			assertEquals( Set.of( B ), runner.system(),
					"rejected mutation retains the configured scope" );
			assertEquals( "", runner.events(), "configuration must not invoke processing" );
		}
	}

	/**
	 * The shared prepared seam snapshots registrations even though this legacy test
	 * adapter still permits mutation of its original configuration.
	 */
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preparedProcessingRetainsFixtureCallbacks( boolean resolveDeclarations ) {
		List<String> calls = new ArrayList<>();
		var model = TestModel.withBoth();
		Flow flow = model.flows().findFirst().orElseThrow();
		try( TestFlocessor runner = new TestFlocessor( "prepared callbacks", model )
				.system( State.FUL, B )
				.applicators( new Applicator<TestContext>( TestContext.class, 0 ) {
					@Override
					public void transition( TestContext from, TestContext to ) {
						assertNull( from );
						calls.add( "context " + to.value() );
					}

					@Override
					public Comparator<TestContext> order() {
						return Comparator.comparing( TestContext::value );
					}
				} )
				.checkers( new CheckerTest.TestChecker() {
					@Override
					public byte[] actual( TestResidue residue, List<Assertion> behaviour ) {
						assertEquals( 2, behaviour.size(), "observed parent and unobserved child" );
						calls.add( "residue " + residue.value() );
						return "1st residue".getBytes( UTF_8 );
					}
				} )
				.behaviour( a -> {
					calls.add( "SUT" );
					a.actual().response( "B response to A".getBytes( UTF_8 ) );
				} )
				.listening( new Listener() {
					@Override
					public void flowComplete( Flow completed ) {
						assertSame( flow, completed );
						calls.add( "complete" );
					}
				} ) ) {
			List<Flow> resolved = new ArrayList<>();
			try( Stream<Flow> prepared = resolveDeclarations
					? runner.prepareFlows( resolved::add )
					: runner.prepareFlows() ) {
				assertEquals( List.of( flow ), prepared.toList() );
			}
			assertEquals( resolveDeclarations ? List.of( flow ) : List.of(), resolved );
			assertEquals( List.of(), calls, "preparation must not execute fixture work" );
			runner.system( State.FUL, A )
					.applicators( new Applicator<TestContext>( TestContext.class, 0 ) {
						@Override
						public void transition( TestContext from, TestContext to ) {
							throw new AssertionError( "replacement applicator" );
						}

						@Override
						public Comparator<TestContext> order() {
							return Comparator.comparing( TestContext::value );
						}
					} )
					.checkers( new CheckerTest.TestChecker() )
					.behaviour( a -> {
						throw new AssertionError( "replacement behaviour" );
					} )
					.listening( new Listener() {
						@Override
						public void flowComplete( Flow completed ) {
							throw new AssertionError( "replacement listener" );
						}
					} );
			runner.execute();
			assertEquals( "abc [] SUCCESS", runner.results(), runner::events );
			assertEquals( List.of( "context ctx", "SUT", "residue 1st residue", "complete" ), calls );
		}
	}

	/**
	 * Interval-based capture cannot serve concurrent flows; correlated capture can.
	 */
	@Test
	void concurrentConfigurationRejectsIntervalCaptureWithoutStartingIt() {
		CaptureScopeTest.Source capture = new CaptureScopeTest.Source();
		try( TestFlocessor runner = new TestFlocessor( "concurrent capture guard", TestModel.abc() )
				.reporting( Reporting.QUIETLY ).logs( capture ) ) {
			assertThrows( IllegalStateException.class, runner::requireConcurrentConfiguration );
			assertEquals( List.of(), capture.events );
			runner.logs( LogCapture.NO_OP );
			assertDoesNotThrow( runner::requireConcurrentConfiguration );
		}
	}

	@Test
	void scopedCompletionAllowsReportReuseAfterRepeatedExecution() throws Exception {
		try( Temporary name = AssertionOptions.REPORT_NAME.temporarily( "reused" ) ) {
			assertReportReuse();
			Path legacyClaim = Path.of( AssertionOptions.ARTIFACT_DIR.value(), "scoped-completion",
					".reused.flow-writer.lock" );
			Files.createDirectories( legacyClaim.getParent() );
			try {
				try( FileChannel channel = FileChannel.open( legacyClaim, StandardOpenOption.CREATE,
						StandardOpenOption.WRITE ); FileLock ignored = channel.lock() ) {
					assertReportReuse();
				}
			}
			finally {
				Files.deleteIfExists( legacyClaim );
			}
		}
	}

	private static void assertReportReuse() {
		try( TestFlocessor tf = new TestFlocessor( "scoped completion", TestModel.abc() )
				.system( State.LESS, B ).reporting( Reporting.QUIETLY, "scoped-completion" )
				.behaviour( a -> a.actual().response( a.expected().response().content() ) ) ) {
			tf.execute();
			assertEquals( "abc [] SUCCESS", tf.results(), tf::events );
			Reader report = new Reader( tf.report() );
			assertEquals( Set.of( "PASS" ), report.read().entries.get( 0 ).tags );
			tf.behaviour( a -> {
			} );
			tf.execute();
			assertEquals( "abc [] SKIP", tf.results() );
			assertTrue( report.read().entries.get( 0 ).tags.contains( "SKIP" ) );
		}
	}

	/**
	 * Completion is terminal even when there is no execution or report to finish.
	 */
	@ParameterizedTest
	@EnumSource(value = Reporting.class, names = { "NEVER", "QUIETLY" })
	void completionWithoutExecutionIsTerminalAndDoesNotCreateReport( Reporting reporting ) {
		List<String> calls = new ArrayList<>();
		TestFlocessor runner = new TestFlocessor( "closed before execution", TestModel.abc() )
				.system( State.LESS, B ).reporting( reporting )
				.behaviour( a -> calls.add( "SUT" ) );
		Flow flow = runner.flows().findFirst().orElseThrow();
		runner.completeProcessing();
		runner.completeProcessing();
		assertThrows( IllegalStateException.class, () -> runner.process( flow ) );
		assertEquals( List.of(), calls );
		assertNull( runner.report() );
	}

	/**
	 * A rejected close must neither wait for nor invalidate an in-flight SUT call.
	 */
	@Test
	void completionRejectsActiveInvocationWithoutClosingIt() throws Exception {
		CountDownLatch entered = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		TestFlocessor runner = new TestFlocessor( "active completion", TestModel.abc() )
				.system( State.LESS, B ).reporting( Reporting.NEVER )
				.behaviour( a -> {
					entered.countDown();
					try {
						assertTrue( release.await( 5, TimeUnit.SECONDS ) );
					}
					catch( InterruptedException e ) {
						Thread.currentThread().interrupt();
						throw new AssertionError( e );
					}
					a.actual().response( a.expected().response().content() );
				} );
		Flow flow = runner.flows().findFirst().orElseThrow();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> invocation = executor.submit( () -> runner.process( flow ) );
			assertTrue( entered.await( 5, TimeUnit.SECONDS ) );
			assertTimeoutPreemptively( Duration.ofSeconds( 2 ),
					() -> assertThrows( IllegalStateException.class, runner::completeProcessing ) );
			release.countDown();
			invocation.get( 5, TimeUnit.SECONDS );
			runner.process( flow );
			runner.completeProcessing();
			assertThrows( IllegalStateException.class, () -> runner.process( flow ) );
		}
		finally {
			release.countDown();
			executor.shutdownNow();
			assertTrue( executor.awaitTermination( 5, TimeUnit.SECONDS ) );
		}
	}

	/**
	 * Default test behaviour is to fail noisily
	 */
	@Test
	void noBehaviour() {
		try( TestFlocessor tf = new TestFlocessor( "noBehaviour", TestModel.abc() )
				.reporting( Reporting.QUIETLY )
				.system( State.LESS, Actors.B ) ) {

			tf.execute();

			assertEquals( "abc [] error No test behaviour specified", tf.events() );

			// This is also recorded to the report
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			FlowData fd = r.detail( ie );
			String msg = fd.logs.get( 0 ).message;

			assertTrue( ie.tags.contains( "ERROR" ), ie.tags.toString() );
			assertTrue( fd.tags.contains( "ERROR" ), fd.tags.toString() );
			assertEquals(
					"Encountered error: java.lang.IllegalStateException: No test behaviour specified",
					msg.replaceAll( "\tat .*", "" ).trim() );
		}
	}

	/**
	 * Construction methods are fluent
	 */
	@Test
	void fluency() {
		TestFlocessor tf = new TestFlocessor( "fluency", TestModel.abc() );
		assertSame( tf, tf.behaviour( null ) );
		assertSame( tf, tf.system( null ) );
		assertSame( tf, tf.masking() );
		assertSame( tf, tf.applicators() );
		assertSame( tf, tf.checkers() );
		assertSame( tf, tf.logs( (LogCapture) null ) );
		assertSame( tf, tf.logs( (CorrelatedCapture) null ) );
		assertSame( tf, tf.correlation( null ) );
		assertSame( tf, tf.autonomous() );
		assertSame( tf, tf.motivation( null ) );
	}

	/**
	 * Enumeration is not execution or completion, and legacy configuration remains
	 * live after enumeration and between invocations.
	 */
	@Test
	void enumerationKeepsLegacyConfigurationLive() {
		List<String> events = new ArrayList<>();
		TestFlocessor tf = new TestFlocessor( "enumeration", TestModel.abcWithChild() )
				.system( State.LESS, B )
				.reporting( Reporting.QUIETLY, "enumeration" )
				.listening( new Listener() {
					@Override
					public void flowComplete( Flow flow ) {
						events.add( "complete " + flow.meta().description() );
					}
				} );
		try( tf ) {
			List<Flow> selected;
			try( Stream<Flow> flows = tf.flows() ) {
				selected = flows.collect( Collectors.toList() );
			}
			assertEquals( 2, selected.size() );
			assertTrue( events.isEmpty() );
			assertNull( tf.report() );

			tf.behaviour( a -> {
				events.add( "first " + a.flow().meta().description() );
				a.actual().response( a.expected().response().content() );
			} );
			tf.process( selected.get( 0 ) );
			Reader report = new Reader( tf.report() );
			assertEquals( 1, report.read().entries.size() );

			tf.behaviour( a -> {
				events.add( "second " + a.flow().meta().description() );
				a.actual().response( a.expected().response().content() );
			} );
			tf.process( selected.get( 1 ) );
			assertEquals( 2, report.read().entries.size() );
			assertEquals( List.of( "first abc", "complete abc", "second child", "complete child" ),
					events );
			assertEquals( 2, tf.flows().count() );
		}
	}

	/**
	 * An accumulated execution error takes priority over a comparison failure, but
	 * neither the failures nor harvested message evidence belong to the next flow.
	 */
	@Test
	void invocationEvidenceAndFailuresAreLocal() {
		Thread caller = Thread.currentThread();
		List<List<Assertion>> evidence = new ArrayList<>();
		TestFlocessor tf = new TestFlocessor( "invocation-local", TestModel.withResidue() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY, "invocation-local" )
				.checkers( new Checker<TestResidue>( TestResidue.class ) {
					@Override
					public Message expected( TestResidue residue ) {
						assertSame( caller, Thread.currentThread() );
						return new Text( "residue" );
					}

					@Override
					public byte[] actual( TestResidue residue, List<Assertion> actual ) {
						assertSame( caller, Thread.currentThread() );
						evidence.add( List.copyOf( actual ) );
						if( evidence.size() == 1 ) {
							throw new IllegalStateException( "checker failed" );
						}
						return "residue".getBytes( UTF_8 );
					}
				} )
				.behaviour( a -> {
					assertSame( caller, Thread.currentThread() );
					a.actual().response( a.flow().meta().description().equals( "abc" )
							? "unexpected".getBytes( UTF_8 )
							: a.expected().response().content() );
				} );
		try( tf ) {
			tf.execute();

			assertEquals( "abc [] ERROR\ndef [] SUCCESS", tf.results() );
			assertEquals( 2, evidence.size() );
			// Legacy checkers receive the harvested assertions once per message type.
			assertEquals( List.of( "abc", "abc" ), evidence.get( 0 ).stream()
					.map( a -> a.flow().meta().description() ).collect( Collectors.toList() ) );
			assertEquals( List.of( "def", "def" ), evidence.get( 1 ).stream()
					.map( a -> a.flow().meta().description() ).collect( Collectors.toList() ) );
			Reader report = new Reader( tf.report() );
			Index index = report.read();
			assertEquals( 2, index.entries.size() );
			FlowData first = report.detail( index.entries.get( 0 ) );
			FlowData second = report.detail( index.entries.get( 1 ) );
			assertEquals( Set.of( "ERROR" ), first.tags );
			assertTrue( first.logs.get( 0 ).message.contains( "checker failed" ) );
			assertEquals( Set.of( "PASS" ), second.tags );
			assertTrue( second.logs.isEmpty() );
			assertEquals( "B response to A", second.root.response.full.actual );
		}
	}

	/**
	 * Selection includes the producer once, and actual data is published before
	 * comparison. Immediate failure stops later publication; accumulated failure
	 * publishes both messages, without replaying binding mutations or callbacks.
	 *
	 * @param reporting Immediate or accumulated comparison mode
	 */
	@ParameterizedTest
	@EnumSource(value = Reporting.class, names = { "NEVER", "QUIETLY" })
	void synchronousPublicationPreservesFailureMode( Reporting reporting ) {
		Thread caller = Thread.currentThread();
		List<String> publications = new ArrayList<>();
		List<String> bodies = new ArrayList<>();
		List<String> completed = new ArrayList<>();
		Flow producer = Creator.build( f -> f
				.meta( m -> m.description( "producer" ) )
				.call( i -> i.from( A ).to( B )
						.request( new Text( "request" ) ).response( new Text( "response" ) ) ) );
		Flow sink = Creator.build( f -> f
				.meta( m -> m.description( "sink" ) )
				.call( i -> i.from( A ).to( B )
						.request( new Text( "request" ) ).response( new Text( "response" ) ) )
				.dependency( producer, d -> d.from( i -> i.responder() == B, REQUEST, ".+" )
						.mutate( value -> {
							assertSame( caller, Thread.currentThread() );
							publications.add( "request " + value );
							return value;
						} ).to( i -> i.responder() == B, REQUEST, ".+" ) )
				.dependency( producer, d -> d.from( i -> i.responder() == B, RESPONSE, ".+" )
						.mutate( value -> {
							assertSame( caller, Thread.currentThread() );
							publications.add( "response " + value );
							return value;
						} ).to( i -> i.responder() == B, RESPONSE, ".+" ) ) );
		TestFlocessor tf = new TestFlocessor( "publication", new Mdl().withFlows( sink, producer ) )
				.system( State.FUL, B )
				.reporting( reporting, "publication-" + reporting )
				.exercising( f -> f == sink, rejection -> {
					// The required producer must still be included.
				} )
				.listening( new Listener() {
					@Override
					public void flowComplete( Flow flow ) {
						assertSame( caller, Thread.currentThread() );
						completed.add( flow.meta().description() );
					}
				} )
				.behaviour( a -> {
					assertSame( caller, Thread.currentThread() );
					bodies.add( a.flow().meta().description() );
					if( a.flow() == producer ) {
						a.actual().request( "actual-request".getBytes( UTF_8 ) )
								.response( "actual-response".getBytes( UTF_8 ) );
					}
					else {
						assertEquals( "actual-request", a.expected().request().assertable() );
						assertEquals( reporting == Reporting.NEVER ? "response" : "actual-response",
								a.expected().response().assertable() );
						a.actual().request( a.expected().request().content() )
								.response( a.expected().response().content() );
					}
				} );
		try( tf ) {
			tf.execute();

			assertEquals( "producer [] UNEXPECTED\nsink [] SUCCESS", tf.results() );
			assertEquals( List.of( "producer", "sink" ), bodies );
			if( reporting == Reporting.NEVER ) {
				assertEquals( List.of( "request actual-request" ), publications );
				assertEquals( List.of( "sink" ), completed );
				assertEquals( 3, tf.events().lines().filter( l -> l.startsWith( "COMPARE" ) ).count() );
				assertNull( tf.report() );
			}
			else {
				assertEquals( List.of( "request actual-request", "response actual-response" ),
						publications );
				assertEquals( List.of( "producer", "sink" ), completed );
				assertEquals( 4, tf.events().lines().filter( l -> l.startsWith( "COMPARE" ) ).count() );
				Reader report = new Reader( tf.report() );
				Index index = report.read();
				assertEquals( 2, index.entries.size() );
				assertEquals( Set.of( "FAIL" ), report.detail( index.entries.get( 0 ) ).tags );
				assertEquals( Set.of( "PASS" ), report.detail( index.entries.get( 1 ) ).tags );
			}
		}
	}

	/**
	 * A binding error stops that publication but reporting still compares later
	 * messages. Earlier writes and mutation side effects are never rolled back. The
	 * operation-level fault sequences are proven in the API module's
	 * DependenciesTest; this checks the caller's handling of a fault before any
	 * write and of one after a partial write.
	 *
	 * @param reporting Immediate or accumulated mode
	 * @param fault     The synchronous publication operation that fails
	 */
	@ParameterizedTest
	@CsvSource({ "NEVER,peer", "NEVER,set-after", "QUIETLY,peer", "QUIETLY,set-after" })
	void publicationErrorRetainsEarlierWritesAndAccumulatesOnlyWhenConfigured( Reporting reporting,
			String fault ) {
		Thread caller = Thread.currentThread();
		RuntimeException original = new IllegalStateException( "publication " + fault );
		List<String> operations = new ArrayList<>();
		List<String> cleanup = new ArrayList<>();
		AtomicInteger gets = new AtomicInteger();
		AtomicInteger mutations = new AtomicInteger();
		AtomicInteger sets = new AtomicInteger();
		AtomicBoolean armed = new AtomicBoolean();
		Text request = new Text( "request" ) {
			@Override
			public Text child() {
				return this;
			}

			@Override
			public Text peer( byte[] bytes ) {
				assertSame( caller, Thread.currentThread() );
				operations.add( "peer" );
				if( fault.equals( "peer" ) )
					throw original;
				return new Text( bytes ) {
					@Override
					protected Object access( String field ) {
						assertSame( caller, Thread.currentThread() );
						int call = gets.incrementAndGet();
						operations.add( "get" + call );
						return "write" + call;
					}
				};
			}
		};
		Text destination = new Text( "initial" ) {
			@Override
			public Text child() {
				return this;
			}

			@Override
			public Text set( String field, Object value ) {
				if( !armed.get() )
					return super.set( field, value );
				assertSame( caller, Thread.currentThread() );
				int call = sets.incrementAndGet();
				operations.add( "set" + call );
				super.set( field, value );
				if( call == 2 && fault.equals( "set-after" ) )
					throw original;
				return this;
			}
		};
		Flow producer = Creator.build( f -> f.meta( m -> m.description( "producer" ) )
				.call( i -> i.from( A ).to( B ).request( request )
						.response( new Text( "response" ) ) ) );
		Flow sink = Creator.build( f -> {
			f.meta( m -> m.description( "sink" ) ).call( i -> i.from( A ).to( B )
					.request( destination ).response( new Text( "pending" ) ) );
			for( int binding = 1; binding <= 3; binding++ ) {
				f.dependency( producer, d -> d.from( i -> true, REQUEST, ".+" ).mutate( value -> {
					assertSame( caller, Thread.currentThread() );
					operations.add( "mutation" + mutations.incrementAndGet() );
					return value;
				} ).to( i -> true, REQUEST, ".+" ) );
			}
			f.dependency( producer, d -> d.from( i -> true, RESPONSE, ".+" ).mutate( value -> {
				assertSame( caller, Thread.currentThread() );
				operations.add( "response" );
				return "later write";
			} ).to( i -> true, RESPONSE, ".+" ) );
		} );
		destination.set( ".+", "initial" );
		armed.set( true );
		try( TestFlocessor runner = new TestFlocessor( "binding error",
				new Mdl().withFlows( producer, sink ) )
						.system( State.FUL, B )
						.reporting( reporting, "binding-error-" + reporting + "-" + fault )
						.logs( new LogCapture() {
							@Override
							public void start( Flow flow ) {
								assertSame( producer, flow );
								assertSame( caller, Thread.currentThread() );
								cleanup.add( "start" );
							}

							@Override
							public Stream<com.mastercard.test.flow.report.data.LogEvent> end( Flow flow ) {
								assertSame( producer, flow );
								assertSame( caller, Thread.currentThread() );
								cleanup.add( "end" );
								return Stream.<com.mastercard.test.flow.report.data.LogEvent>empty()
										.onClose( () -> {
											assertSame( caller, Thread.currentThread() );
											cleanup.add( "close" );
										} );
							}
						} ).listening( new Listener() {
							@Override
							public void flowComplete( Flow flow ) {
								assertSame( producer, flow );
								assertSame( caller, Thread.currentThread() );
								cleanup.add( "complete" );
							}
						} )
						.behaviour( a -> {
							assertSame( caller, Thread.currentThread() );
							operations.add( "body" );
							a.actual().request( "request".getBytes( UTF_8 ) )
									.response( "unexpected response".getBytes( UTF_8 ) );
						} ) ) {
			assertEquals( List.of( producer, sink ), runner.flows().toList() );
			var failure = assertThrows( IllegalArgumentException.class,
					() -> runner.process( producer ) );
			assertSame( original, failure.getCause().getCause(),
					"execution error outranks later comparison failure" );
			assertEquals( fault.equals( "peer" ) ? "initial" : "write2",
					sink.root().request().assertable() );
			assertEquals( reporting == Reporting.NEVER ? "response" : "later write",
					sink.root().response().assertable() );
			List<String> expected = new ArrayList<>( fault.equals( "peer" )
					? List.of( "body", "peer" )
					: List.of( "body", "peer", "get1", "mutation1", "set1", "get2", "mutation2",
							"set2" ) );
			if( reporting == Reporting.QUIETLY )
				expected.add( "response" );
			assertEquals( expected, operations, "no third binding, retry or replay" );
			assertEquals( 4, sink.dependencies().count() );
			assertEquals( reporting == Reporting.NEVER ? 0 : 1,
					runner.events().lines().filter( l -> l.startsWith( "COMPARE" ) ).count() );
			assertEquals( reporting == Reporting.NEVER ? List.of()
					: List.of( "start", "end", "close", "complete" ), cleanup );
			if( reporting == Reporting.NEVER )
				assertNull( runner.report() );
			else {
				Reader reader = new Reader( runner.report() );
				Index index = reader.read();
				assertEquals( 1, index.entries.size() );
				FlowData detail = reader.detail( index.entries.get( 0 ) );
				assertEquals( Set.of( "ERROR" ), detail.tags );
				assertEquals( "unexpected response", detail.root.response.full.actual );
				assertTrue( detail.logs.stream()
						.anyMatch( log -> log.message.contains( "publication " + fault ) ) );
			}
			runner.close();
			assertThrows( IllegalStateException.class, () -> runner.process( producer ) );
			assertEquals( expected, operations, "completion does not rerun publication" );
		}
	}

	/** Binding reads the unmasked peer before comparing a masked request. */
	@Test
	void intraFlowBindingPublishesUnmaskedActualBeforeResponseComparison() {
		com.mastercard.test.flow.Unpredictable token = () -> "token";
		Thread caller = Thread.currentThread();
		List<Object> values = new ArrayList<>();
		Flow flow = Creator.build( f -> f.meta( m -> m.description( "self" ) )
				.call( i -> i.from( A ).to( B )
						.request(
								new Text( "expected-token" ).masking( token, m -> m.replace( ".+", "masked" ) ) )
						.response( new Text( "pending" ) ) )
				.dependency( null, d -> d.from( i -> true, REQUEST, ".+" ).mutate( value -> {
					assertSame( caller, Thread.currentThread() );
					values.add( value );
					return value;
				} ).to( i -> true, RESPONSE, ".+" ) ) );
		try( TestFlocessor runner = new TestFlocessor( "masked self publication",
				new Mdl().withFlows( flow ) )
						.system( State.FUL, B ).reporting( Reporting.NEVER ).masking( token )
						.behaviour( a -> a.actual().request( "raw-token".getBytes( UTF_8 ) )
								.response( "raw-token".getBytes( UTF_8 ) ) ) ) {
			runner.execute();
			assertEquals( "self [] SUCCESS", runner.results() );
			assertEquals( List.of( "raw-token" ), values );
			assertEquals( "raw-token", flow.root().response().assertable() );
			assertEquals( 2, runner.events().lines().filter( l -> l.startsWith( "COMPARE" ) ).count() );
		}
	}

	/**
	 * Illustrates how defining the system under test influences the entrypoint
	 * interaction
	 */
	@Test
	void entryPoint() {
		TestFlocessor tf = new TestFlocessor( "entryPoint", TestModel.abc() )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );

		BiConsumer<Actor, String> test = ( in, out ) -> {
			tf.system( State.LESS, in );
			tf.execute();
			assertEquals( out,
					copypasta(
							"events", tf.events(),
							"results", tf.results() ),
					"for " + in );
		};

		test.accept( Actors.A, copypasta(
				"events",
				"SKIP No interactions with system [A]",
				"results",
				"abc [] SKIP" ) );

		test.accept( Actors.B, copypasta(
				"events",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |",
				"results",
				"abc [] SUCCESS" ) );

		test.accept( Actors.C, copypasta(
				"events",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] response",
				" | C response to B | C response to B |",
				"results",
				"abc [] SUCCESS" ) );
	}

	/**
	 * Shows that test behaviours that make no assertions result in the flow being
	 * marked as skipped
	 */
	@Test
	void emptyTest() {
		try( TestFlocessor tf = new TestFlocessor( "emptyTest", TestModel.abc() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					// no assertions!
				} ) ) {

			tf.execute();

			assertEquals( "SKIP No assertions made", tf.events() );

			// This is also recorded to the report
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			FlowData fd = r.detail( ie );
			String msg = fd.logs.get( 0 ).message;

			assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
			assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
			assertEquals( "No assertions made", msg );
		}
	}

	/**
	 * Shows that the child is skipped if the basis flow fails. We do this to cut
	 * down on failure spam - the child is very likely to fail in exactly the same
	 * way as the basis.
	 */
	@Test
	void failedBasis() {
		try( TestFlocessor tf = new TestFlocessor( "failedBasis", TestModel.abcWithChild() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					assrt.actual().response( "fail!".getBytes( UTF_8 ) );
				} ) ) {
			tf.execute();

			assertEquals( copypasta(
					"COMPARE abc []",
					"com.mastercard.test.flow.assrt.TestModel.abcWithChild(TestModel.java:_) A->B [] response",
					" | B response to A | fail! |",
					"",
					"SKIP Ancestor failed" ),
					copypasta( tf.events() ) );

			// This is also recorded to the report
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 1 );
			FlowData fd = r.detail( ie );
			String msg = fd.logs.get( 0 ).message;

			assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
			assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
			assertEquals( "Skipping flow: Ancestor failed", msg );
		}
	}

	/**
	 * Shows that the child is skipped if a dependency flow suffers an error
	 */
	@Test
	void failedDependency() {
		try( TestFlocessor tf = new TestFlocessor( "failedDependency", TestModel.abcWithDependency() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					throw new RuntimeException( "kaboom!" );
				} ) ) {
			tf.execute();

			assertEquals( copypasta(
					"dependency [] error kaboom!",
					"SKIP Missing dependency" ),
					copypasta( tf.events() ) );

			// This is also recorded to the report
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 1 );
			FlowData fd = r.detail( ie );
			String msg = fd.logs.get( 0 ).message;

			assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
			assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
			assertEquals( "Skipping flow: Missing dependency", msg );
		}
	}

	/**
	 * Shows that flows are processed when they have implicit dependencies that are
	 * included in the system under test
	 */
	@Test
	void includedImplicit() {
		TestFlocessor tf = new TestFlocessor( "includedImplicit", TestModel.abcWithImplicit() )
				.system( State.FUL, B, D )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abcWithImplicit(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * Shows that flows are skipped if they have implicit dependencies that are not
	 * included in the system under test
	 */
	@Test
	void missingImplicit() {
		try( TestFlocessor tf = new TestFlocessor( "missingImplicit", TestModel.abcWithImplicit() )
				.system( State.FUL, B )
				.reporting( Reporting.QUIETLY )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} ) ) {
			tf.execute();

			assertEquals( copypasta(
					"SKIP Implicitly depends on D, which is not part of the system under test" ),
					copypasta( tf.events() ) );

			// This is also recorded to the report
			Reader r = new Reader( tf.report() );
			Index index = r.read();
			Entry ie = index.entries.get( 0 );
			FlowData fd = r.detail( ie );
			String msg = fd.logs.get( 0 ).message;

			assertTrue( ie.tags.contains( "SKIP" ), ie.tags.toString() );
			assertTrue( fd.tags.contains( "SKIP" ), fd.tags.toString() );
			assertEquals( "Skipping flow: "
					+ "Implicitly depends on D, which is not part of the system under test",
					msg );
		}
	}

	/**
	 * Shows that we assert on request messages <i>before</i> responses. This is a
	 * sneaky wee usability tweak that is enormously helpful when you're chasing a
	 * message change through a system
	 */
	@Test
	void messageTypeOrder() {
		TestFlocessor tf = new TestFlocessor( "", TestModel.abc() )
				.system( State.FUL, B )
				.behaviour( assrt -> {
					assrt.assertChildren( i -> true ).findFirst()
							.ifPresent( a -> a.actual().request( a.expected().request().content() ) );
					assrt.actual().response( assrt.expected().response().content() );
				} );
		tf.execute();

		assertEquals( copypasta(
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) B->C [] request",
				" | B request to C | B request to C |",
				"",
				"COMPARE abc []",
				"com.mastercard.test.flow.assrt.TestModel.abc(TestModel.java:_) A->B [] response",
				" | B response to A | B response to A |" ),
				copypasta( tf.events() ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	static String copypasta( String... content ) {
		return copypasta( Stream.of( content ) );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	static String copypasta( Collection<String> content ) {
		return copypasta( content.stream() );
	}

	/**
	 * @param content Some strings
	 * @return A string that can be trivially copy/pasted into java source
	 */
	static String copypasta( Stream<String> content ) {
		return content
				.map( s -> s.replaceAll( "\r", "" ) )
				.flatMap( s -> Stream.of( s.split( "\n" ) ) )
				.map( s -> s.replaceAll( "\"", "'" ) )
				.collect( Collectors.joining( "\",\n\"", "\"", "\"" ) );
	}
}
