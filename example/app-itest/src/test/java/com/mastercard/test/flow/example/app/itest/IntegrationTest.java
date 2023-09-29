
package com.mastercard.test.flow.example.app.itest;

import static com.mastercard.test.flow.assrt.Reporting.FAILURES;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.CORE;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.DB;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.HISTOGRAM;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.QUEUE;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.STORE;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.UI;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.WEB_UI;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.BORING;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.CLOCK;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.HOST;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.RNG;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.Replay;
import com.mastercard.test.flow.assrt.junit5.Flocessor;
import com.mastercard.test.flow.example.app.assrt.Browser;
import com.mastercard.test.flow.example.app.assrt.HttpClient;
import com.mastercard.test.flow.example.app.assrt.Util;
import com.mastercard.test.flow.example.app.assrt.ctx.QueueProcessingApplicator;
import com.mastercard.test.flow.example.app.assrt.ctx.UpnessApplicator;
import com.mastercard.test.flow.example.app.assrt.rsd.DBItemsChecker;
import com.mastercard.test.flow.example.app.model.ExampleSystem;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.msg.web.WebSequence;

import spark.Service;

/**
 * Spins up a complete instance of the application and compares its behaviour
 * against our model
 */
@SuppressWarnings("static-method")
@ExtendWith(Browser.class)
class IntegrationTest {

	private static final Logger LOG = LoggerFactory.getLogger( IntegrationTest.class );

	private static final ClusterManager clusterManager = new ClusterManager();

	static {
		if( AssertionOptions.REPORT_NAME.value() == null ) {
			AssertionOptions.REPORT_NAME.set( "latest" );
		}
	}

	private static Supplier<Path> reportLocation = () -> null;

	/**
	 * Creates an instance of the application
	 */
	@BeforeAll
	public static void startApp() {

		if( Replay.isActive() ) {
			// no point running the services in this case!
			return;
		}

		clusterManager.startCluster();

	}

	/**
	 * Stops the instance
	 *
	 * @throws Exception if something goes wrong
	 */
	@AfterAll
	public static void stopApp() throws Exception {

		clusterManager.stopCluster();

		if( "true".equals( System.getProperty( "mctf.itest.report.serve" ) ) ) {
			// serve the report
			String reportDir = reportLocation.get().toString();
			System.out.println( "Serving " + reportDir );
			Service service = Service.ignite()
					.port( 0 )
					.externalStaticFileLocation( reportDir );
			service.staticFiles.header( "Access-Control-Allow-Origin", "*" );
			service.init();
			service.awaitInitialization();

			// open the browser
			URI uri = new URI( "http://localhost:" + service.port() );
			if( Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported( Action.BROWSE ) ) {
				System.out.println( "Opening browser to " + uri );
				Desktop.getDesktop().browse( uri );
			}
			else {
				System.out.println( "Open your browser to " + uri );
			}

			// wait it till the human is done with it
			System.out.println( "Hit enter to shutdown the server" );
			// noinspection ResultOfMethodCallIgnored
			System.in.read();

			// shut it down
			service.stop();
			service.awaitStop();
			System.out.println( "Shutdown complete" );
		}
	}

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> flows() {
		Flocessor f = new Flocessor( "Integration test", ExampleSystem.MODEL )
				.reporting( FAILURES )
				.system( State.FUL, WEB_UI, UI, CORE, QUEUE, HISTOGRAM, STORE, DB )
				.masking( BORING, CLOCK, HOST, RNG )
				.logs( Util.LOG_CAPTURE )
				.applicators(
						new QueueProcessingApplicator( clusterManager.queue ),
						new UpnessApplicator()
								.with( WEB_UI, clusterManager.webUi )
								.with( UI, clusterManager.ui )
								.with( CORE, clusterManager.core )
								.with( QUEUE, clusterManager.queue )
								.with( STORE, clusterManager.store )
								.with( HISTOGRAM, clusterManager.histogram ) )
				.checkers( new DBItemsChecker() )
				.behaviour( assrt -> {
					LOG.warn( "Exercising {}->{}:{} of '{}'", assrt.expected().requester(),
							assrt.expected().responder(), assrt.expected().tags(), assrt.flow().meta().id() );

					byte[] response;
					// snippet-start:browser_invocation
					if( assrt.expected().request() instanceof WebSequence
							&& assrt.expected().response() instanceof WebSequence ) {
						WebSequence actions = (WebSequence) assrt.expected().request().child();
						actions.set( "web_ui_url",
								"http://localhost:" + clusterManager.getWebUiPort() + "/web" );
						WebDriver driver = Browser.get();
						byte[] actionResults = actions.process( driver );
						assrt.actual().request( actionResults );

						WebSequence results = (WebSequence) assrt.expected().response();
						response = results.process( driver );
					}
					// snippet-end:browser_invocation
					else {
						// Assume it's a simple http request
						LOG.info( "Sending\n{}", assrt.expected().request().assertable() );
						response = HttpClient.send(
								"http", "localhost", port( assrt.expected().responder() ),
								(HttpReq) assrt.expected().request() );
					}

					LOG.info( "Received\n{}", new String( response, StandardCharsets.UTF_8 ) );

					// supply the response for assertion
					assrt.actual().response( response );
					LOG.warn( "Complete" );
				} );

		// we need to know where the report went in order to serve it, but that only
		// becomes apparent *after* flow processing
		reportLocation = () -> f.report();

		return f.tests();
	}

	private int port( Actor rx ) {
		if( rx == Actors.UI ) {
			return clusterManager.ui.port();
		}
		if( rx == Actors.QUEUE ) {
			return clusterManager.queue.port();
		}
		throw new IllegalArgumentException( "Unknown endpoint " + rx );
	}
}
