
package com.mastercard.test.flow.example.app.assrt;

import static com.mastercard.test.flow.assrt.Reporting.FAILURES;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.BORING;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.CLOCK;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.HOST;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.RNG;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;

import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Assertion;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.Replay;
import com.mastercard.test.flow.assrt.junit5.Flocessor;
import com.mastercard.test.flow.example.app.assrt.ctx.UpnessApplicator;
import com.mastercard.test.flow.example.app.model.ExampleSystem;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.util.Option.Temporary;

/**
 * Each of the applications services can be tested in the same way, so we've
 * implemented it here rather than repeating ourselves in every service's test
 * suite.
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractServiceTest {

	private static MockInstance dependencyInstance = new MockInstance();
	private static MockService dependencies = new MockService();
	private static Temporary reportName;

	private final Supplier<Instance> service;
	private final Actors actor;
	private final Logger logger;
	private Flocessor flocessor;

	/**
	 * @param service Resolves the service after subclass {@code @BeforeAll} setup;
	 *                {@code PER_CLASS} construction happens before that setup
	 * @param actor   The actor in the system model that represents the service
	 * @param logger  Where to log test progress to
	 */
	protected AbstractServiceTest( Supplier<Instance> service, Actors actor, Logger logger ) {
		this.service = service;
		this.actor = actor;
		this.logger = logger;
	}

	/**
	 * Starts the {@link MockInstance} that satisfies system dependencies
	 */
	@BeforeAll
	public static void startDependencies() {
		if( !Replay.isActive() ) {
			// MockInstance.stop() is terminal; each class execution owns a fresh fixture.
			dependencyInstance = new MockInstance();
			dependencies = new MockService();
			dependencyInstance.start( dependencies );
		}
		reportName = AssertionOptions.REPORT_NAME.temporarily( "latest" );
	}

	/**
	 * Stops the {@link MockInstance} that satisfies system dependencies
	 */
	@AfterAll
	public static void stopDependencies() {
		dependencyInstance.stop();
		reportName.close();
	}

	/**
	 * It's pretty easy to create a dependency version mismatch between slf4j and
	 * slf4j-simple that breaks logging behaviour. The end result of this is fairly
	 * subtle - no logs appear in the execution reports. This test will assert that
	 * the log file is being created as expected.
	 */
	@AfterAll
	static void checkLogs() {
		Assertions.assertTrue( Files.exists( Util.LOG_FILE_PATH ),
				"logging behaviour broken!" );
	}

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> flows() {
		flocessor = new Flocessor( actor + " test", ExampleSystem.MODEL )
				.reporting( FAILURES )
				.system( State.LESS, actor )
				.applicators( new UpnessApplicator().with( actor, service.get() ) )
				.masking( BORING, HOST, CLOCK, RNG )
				.logs( Util.LOG_CAPTURE )
				.behaviour( assrt -> {
					logger.warn( "Exercising {}->{}:{} of '{}'", assrt.expected().requester(),
							assrt.expected().responder(), assrt.expected().tags(), assrt.flow().meta().id() );

					logger.info( "Mocking downstream responses" );
					dependencies.exercising( assrt.expected() );

					// send the request
					logger.info( "Sending\n{}", assrt.expected().request().assertable() );
					byte[] response;

					try {
						response = getResponse( assrt );
					}
					catch( UncheckedIOException ioe ) {
						response = ioe.getMessage().getBytes();
					}
					logger.info( "Received\n{}", new String( response, StandardCharsets.UTF_8 ) );

					// supply the response for assertion
					assrt.actual().response( response );
					// assert downstream requests
					assrt.assertConsequests( dependencies.requests()::get );
					logger.warn( "Complete" );
				} );
		custom().accept( flocessor );
		return flocessor
				.tests();
	}

	/**
	 * Closes reporting after the dynamic children, not when their factory returns.
	 */
	@AfterAll
	void completeFlows() {
		if( flocessor != null ) {
			flocessor.close();
		}
	}

	/**
	 * @param assrt The request to hit the system under test with
	 * @return The bytes of the response from the system under test
	 */
	protected byte[] getResponse( Assertion assrt ) {
		return HttpClient.send(
				"http", "localhost", service.get().port(),
				(HttpReq) assrt.expected().request() );
	}

	/**
	 * Allows subclasses to customise {@link Flocessor} configuration
	 *
	 * @return custom configuration actions
	 */
	@SuppressWarnings("static-method")
	protected Consumer<Flocessor> custom() {
		return f -> {
			// no-op
		};
	}

}
