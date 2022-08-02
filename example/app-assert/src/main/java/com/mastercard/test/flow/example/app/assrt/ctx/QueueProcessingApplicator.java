package com.mastercard.test.flow.example.app.assrt.ctx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Comparator;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mastercard.test.flow.assrt.Applicator;
import com.mastercard.test.flow.example.app.Queue;
import com.mastercard.test.flow.example.app.assrt.HttpClient;
import com.mastercard.test.flow.example.app.model.Messages;
import com.mastercard.test.flow.example.app.model.ctx.QueueProcessing;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.msg.json.Json;

/**
 * Applies {@link QueueProcessing} contexts to the system under test
 */
public class QueueProcessingApplicator extends Applicator<QueueProcessing> {
	private static final Logger LOG = LoggerFactory.getLogger( QueueProcessingApplicator.class );
	private static final QueueProcessing DEFAULT = new QueueProcessing();

	private final Instance queue;

	/**
	 * @param queue The locally-running {@link Queue} instance
	 */
	public QueueProcessingApplicator( Instance queue ) {
		super( QueueProcessing.class, 1 );
		this.queue = queue;
	}

	@Override
	public Comparator<QueueProcessing> order() {
		// we only need to group enabled/non-enabled flows
		return ( a, b ) -> (a.active() ? 1 : 0) - (b.active() ? 1 : 0);
		// the `cleared` and `exhausted` flags don't set a persistent aspect of the
		// environment, so no need to group on those
	}

	@Override
	public void transition( QueueProcessing source, QueueProcessing destination ) {
		QueueProcessing from = Optional.ofNullable( source ).orElse( DEFAULT );
		QueueProcessing to = Optional.ofNullable( destination ).orElse( DEFAULT );
		if( from.active() != to.active()
				|| to.cleared()
				|| to.exhausted() ) {
			Json config = new Json()
					.set( "active", String.valueOf( to.active() ) )
					.set( "clear", String.valueOf( to.cleared() ) )
					.set( "process", to.exhausted() ? "1000" : "0" );

			LOG.info( "Applying queue context {}", new String( config.content(), UTF_8 ) );

			try {
				HttpClient.send( "http", "localhost", queue.port(),
						Messages.httpReq( "POST", "/queue/configure", config ) );
			}
			catch( Exception e ) {
				LOG.error( "Failed to configure queue", e );
				throw e;
			}
		}
	}
}
