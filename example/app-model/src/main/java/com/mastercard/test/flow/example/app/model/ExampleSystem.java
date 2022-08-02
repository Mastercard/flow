
package com.mastercard.test.flow.example.app.model;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.Unpredictable;
import com.mastercard.test.flow.example.app.Core;
import com.mastercard.test.flow.example.app.Histogram;
import com.mastercard.test.flow.example.app.Queue;
import com.mastercard.test.flow.example.app.Store;
import com.mastercard.test.flow.example.app.Ui;
import com.mastercard.test.flow.example.app.WebUi;
import com.mastercard.test.flow.example.framework.Service;
import com.mastercard.test.flow.model.LazyModel;

/**
 * Entry point into the {@link Flow} model of the example system
 */
public class ExampleSystem {

	/**
	 * The system model
	 */
	public static final Model MODEL = new LazyModel(
			"Flow testing example system" )
					.with( Direct.class )
					.with( Deferred.class )
					.with( Implicit.class )
					.with( Failures.class )
					.with( Web.class );

	/**
	 * The components of the system
	 */
	public enum Actors implements Actor {
		/**
		 * The user of the application
		 */
		USER(null),

		/**
		 * The administrator of the application
		 */
		OPS(null),

		/**
		 * The browseable face of the application
		 */
		WEB_UI(WebUi.class),

		/**
		 * The entry point of the application
		 */
		UI(Ui.class),

		/**
		 * The core service, orchestrates processing of the requests
		 */
		CORE(Core.class),

		/**
		 * Handles deferred processing
		 */
		QUEUE(Queue.class),

		/**
		 * Key/value data store
		 */
		STORE(Store.class),

		/**
		 * The database that backs {@link #STORE}
		 */
		DB(null),

		/**
		 * The histogram service, it counts characters
		 */
		HISTOGRAM(Histogram.class);

		/**
		 * The interface of the system component
		 */
		public final Class<? extends Service> service;

		Actors( Class<? extends Service> service ) {
			this.service = service;
		}
	}

	/**
	 * The sources of unpredictability in the system
	 */
	public enum Unpredictables implements Unpredictable {
		/**
		 * Stuff that we just don't want to bother accurately modelling
		 */
		BORING,
		/**
		 * The system that is running the test and applications
		 */
		HOST,
		/**
		 * The system clock
		 */
		CLOCK,
		/**
		 * Randomly-generated data
		 */
		RNG;
	}

}
