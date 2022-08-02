package com.mastercard.test.flow.example.app.store;

import static com.mastercard.test.flow.assrt.Reporting.FAILURES;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Actors.DB;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.BORING;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.CLOCK;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.HOST;
import static com.mastercard.test.flow.example.app.model.ExampleSystem.Unpredictables.RNG;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.Interaction;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.Consequests;
import com.mastercard.test.flow.assrt.Options;
import com.mastercard.test.flow.assrt.Replay;
import com.mastercard.test.flow.assrt.junit5.Flocessor;
import com.mastercard.test.flow.example.app.Store;
import com.mastercard.test.flow.example.app.assrt.HttpClient;
import com.mastercard.test.flow.example.app.assrt.Util;
import com.mastercard.test.flow.example.app.model.ExampleSystem;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.msg.http.HttpReq;
import com.mastercard.test.flow.msg.sql.Query;
import com.mastercard.test.flow.msg.sql.Result;

/**
 * Test that exercises the {@link Store} service in isolation by standing up an
 * instance of it and hitting it with requests. In contrast to
 * {@link StoreTest}, this time we're mocking the database to gain visibility of
 * the queries that the app issues.
 */
@SuppressWarnings("static-method")
class QueryTest {
	private static final Logger LOG = LoggerFactory.getLogger( QueryTest.class );

	private static final DataSource db = Mockito.mock( DataSource.class );
	private static final Consequests queries = new Consequests();
	private static final Instance service = new Main( db ).build();

	static {
		if( Options.REPORT_NAME.value() == null ) {
			Options.REPORT_NAME.set( "query_latest" );
		}
	}

	/**
	 * Starts the service
	 */
	@BeforeAll
	public static void startService() {
		if( !Replay.isActive() ) {
			service.start();
		}
	}

	/**
	 * Stops the service
	 */
	@AfterAll
	public static void stopService() {
		if( !Replay.isActive() ) {
			service.stop();
		}
	}

	/**
	 * @return Test instances
	 */
	@TestFactory
	Stream<DynamicNode> flows() {
		Flocessor flocessor = new Flocessor( "Query test", ExampleSystem.MODEL )
				.reporting( FAILURES )
				.system( State.LESS, Actors.STORE )
				.masking( BORING, HOST, CLOCK, RNG )
				.logs( Util.LOG_CAPTURE )
				.behaviour( assrt -> {
					LOG.warn( "Exercising {}->{}:{} of '{}'", assrt.expected().requester(),
							assrt.expected().responder(), assrt.expected().tags(), assrt.flow().meta().id() );

					LOG.info( "Mocking DB responses for {}->{} {}",
							assrt.expected().requester(), assrt.expected().responder(),
							assrt.expected().tags() );
					Runnable verifies = mockDBInteractions( assrt.expected().children() );

					// send the request
					LOG.info( "Sending\n{}", assrt.expected().request().assertable() );
					byte[] response = HttpClient.send(
							"http", "localhost", service.port(),
							(HttpReq) assrt.expected().request() );
					LOG.info( "Received\n{}", new String( response, StandardCharsets.UTF_8 ) );

					LOG.info( "Capturing DB requests" );
					verifies.run();

					// supply the response for assertion
					assrt.actual().response( response );
					// assert downstream requests
					assrt.assertConsequests( queries );
					LOG.warn( "Complete" );
				} );
		return flocessor
				.tests();
	}

	/**
	 * This was not a lot of fun to write. I do <i>not</i> recommend mocking DB
	 * interaction primitives like this.
	 *
	 * @param dbntr A stream of interactions that might hit the {@link DB} actor
	 * @return A runnable that should be invoked after the system interaction, this
	 *         will populate {@link #queries}
	 */
	@SuppressWarnings("resource")
	private static Runnable mockDBInteractions( Stream<Interaction> dbntr ) {
		Deque<Connection> connections = new ArrayDeque<>();
		List<Runnable> verifies = new ArrayList<>();
		dbntr
				.filter( i -> i.responder() == DB )
				.forEach( ntr -> {
					Query q = (Query) ntr.request();
					List<Object> bv = new ArrayList<>();
					String sql = q.get( bv );

					try {
						Connection c = mock( Connection.class );
						connections.add( c );

						PreparedStatement ps = mock( PreparedStatement.class );
						when( c.prepareStatement( anyString() ) ).thenReturn( ps );

						if( sql.startsWith( "SELECT" ) ) {

							Result r = (Result) ntr.response();
							Deque<Map<String, Object>> rows = new ArrayDeque<>( r.get() );

							AtomicReference<Map<String, Object>> current = new AtomicReference<>();

							ResultSet rs = mock( ResultSet.class );
							when( ps.executeQuery() ).thenReturn( rs );
							when( rs.next() ).then( i -> {
								boolean available = !rows.isEmpty();
								current.set( rows.removeFirst() );
								return available;
							} );
							when( rs.getString( anyString() ) )
									.then( i -> current.get().get( i.getArgument( 0 ) ) );
						}

						verifies.add( () -> {
							try {
								ArgumentCaptor<String> qc = ArgumentCaptor.forClass( String.class );
								verify( c, times( 1 ) ).prepareStatement( qc.capture() );
								ArgumentCaptor<Integer> ic = ArgumentCaptor.forClass( Integer.class );
								ArgumentCaptor<Object> bvc = ArgumentCaptor.forClass( Object.class );
								verify( ps, times( bv.size() ) ).setObject( ic.capture(), bvc.capture() );

								Query captured = new Query( qc.getValue() );
								Iterator<Integer> ii = ic.getAllValues().iterator();
								Iterator<Object> vi = bvc.getAllValues().iterator();
								while( ii.hasNext() && vi.hasNext() ) {
									captured.set( String.valueOf( ii.next() ), vi.next() );
								}

								queries.capture( DB, captured.content() );
							}
							catch( Exception e ) {
								throw new IllegalStateException( e );
							}
						} );
					}
					catch( Exception e ) {
						throw new IllegalStateException( e );
					}
				} );

		Mockito.reset( db );

		if( !connections.isEmpty() ) {
			Connection first = connections.removeFirst();
			Connection[] subsequent = connections.toArray( new Connection[connections.size()] );

			try {
				when( db.getConnection() ).thenReturn( first, subsequent );
			}
			catch( Exception e ) {
				throw new IllegalStateException( e );
			}
		}

		return () -> verifies.forEach( Runnable::run );
	}
}
