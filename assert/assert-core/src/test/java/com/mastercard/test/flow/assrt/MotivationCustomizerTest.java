package com.mastercard.test.flow.assrt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.FlowData;
import com.mastercard.test.flow.report.data.Index;

/**
 * Demonstrates the execution {@link MotivationCustomizer}
 */
class MotivationCustomizerTest {

	/**
	 * Update motivation in the report even when flow is not processed due to some
	 * exception
	 */
	@Test
	void motivationNoBehaviour() {
		TestFlocessor tf = new TestFlocessor( "motivation without behaviour", TestModel.abc() )
				.motivation( ( motivation, assertion ) -> motivation + "Common motivation" )
				.reporting( Reporting.QUIETLY )
				.system( AbstractFlocessor.State.LESS, TestModel.Actors.B );

		tf.execute();

		assertEquals( "abc [] error No test behaviour specified", tf.events() );

		// This is also recorded to the report
		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		FlowData fd = r.detail( ie );
		assertEquals( "Common motivation", fd.motivation );
	}

	/**
	 * Motivation can be updated in the report after the flow is processed
	 */
	@Test
	void motivation() {
		TestFlocessor tf = new TestFlocessor( "motivation", TestModel.abc() )
				.motivation( ( motivation, assertion ) -> {
					String baseUrl = "https://www.google.com/search?q=";
					String queryToken = new String( assertion.expected().request().content() ).substring( 0,
							1 );
					queryToken += new String( assertion.actual().response() ).substring( 0, 1 );
					String logLink = baseUrl + queryToken;
					return motivation + "\n\n[View Logs](" + logLink + ")";
				} )
				.behaviour( assrt -> {
					assrt.actual().response( assrt.expected().response().content() );
				} )
				.reporting( Reporting.QUIETLY )
				.system( AbstractFlocessor.State.LESS, TestModel.Actors.B );

		tf.execute();

		// This is also recorded to the report
		Reader r = new Reader( tf.report() );
		Index index = r.read();
		Entry ie = index.entries.get( 0 );
		FlowData fd = r.detail( ie );
		assertEquals( "\n\n[View Logs](https://www.google.com/search?q=AB)", fd.motivation );
	}

}
