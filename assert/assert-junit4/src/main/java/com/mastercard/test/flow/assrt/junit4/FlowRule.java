package com.mastercard.test.flow.assrt.junit4;

import java.util.function.Supplier;

import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.assrt.History;
import com.mastercard.test.flow.assrt.History.Result;

/**
 * Have a public non-static instance of this class annotated as a {@link Rule}
 * in your test to skip processing {@link Flow} that have no chance of success
 */
public class FlowRule implements TestRule {

	/**
	 * Where to record processing outcomes to
	 */
	final History history;
	/**
	 * How to access the {@link Flow} under consideration
	 */
	final Supplier<Flow> currentFlow;

	/**
	 * @param history     Where to record processing outcomes to
	 * @param currentFlow How to access the {@link Flow} under consideration
	 */
	public FlowRule( History history, Supplier<Flow> currentFlow ) {
		this.history = history;
		this.currentFlow = currentFlow;
	}

	@Override
	public Statement apply( Statement base, Description description ) {

		Flow flow = currentFlow.get();
		return new Statement() {

			@Override
			public void evaluate() throws Throwable {
				try {
					base.evaluate();
					history.recordResult( flow, Result.SUCCESS );
				}
				catch( AssumptionViolatedException ave ) {
					history.recordResult( flow, Result.SKIP );
					throw ave;
				}
				catch( AssertionError ae ) {
					history.recordResult( flow, Result.UNEXPECTED );
					throw ae;
				}
				catch( Throwable t ) {
					history.recordResult( flow, Result.ERROR );
					throw t;
				}
			}
		};
	}
}
