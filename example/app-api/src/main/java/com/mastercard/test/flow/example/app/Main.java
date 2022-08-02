package com.mastercard.test.flow.example.app;

import java.util.function.Supplier;

import com.mastercard.test.flow.example.app.rmt.RemoteCore;
import com.mastercard.test.flow.example.app.rmt.RemoteHistogram;
import com.mastercard.test.flow.example.app.rmt.RemoteQueue;
import com.mastercard.test.flow.example.app.rmt.RemoteStore;
import com.mastercard.test.flow.example.app.rmt.RemoteUi;
import com.mastercard.test.flow.example.framework.Instance;
import com.mastercard.test.flow.example.framework.Remote;
import com.mastercard.test.flow.example.framework.Service;

/**
 * This intermediary superclass just makes sure that {@link Remote}
 * implementations are registered
 */
public abstract class Main extends com.mastercard.test.flow.example.framework.Main {

	static {
		RemoteUi.register();
		RemoteCore.register();
		RemoteHistogram.register();
		RemoteQueue.register();
		RemoteStore.register();
	}

	/**
	 * @param services The {@link Service}s to host in the resulting
	 *                 {@link Instance}
	 */
	@SafeVarargs
	protected Main( Supplier<Service>... services ) {
		super( services );
	}
}
