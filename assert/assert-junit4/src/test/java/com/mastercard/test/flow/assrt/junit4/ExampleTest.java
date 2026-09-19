package com.mastercard.test.flow.assrt.junit4;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Collection;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor.State;
import com.mastercard.test.flow.assrt.junit4.mock.Actrs;
import com.mastercard.test.flow.assrt.junit4.mock.Mdl;

/**
 * Illustrates how to consume a {@link Model} in a junit4 test class. This test
 * will fail, so you don't want to run it normally. It is instead invoked by
 * {@link MetaTest}.
 */
@RunWith(Parameterized.class)
public class ExampleTest {

	/**
	 * Set this to true if you want the test to do anything
	 */
	public static boolean active = false;

	/**
	 * Stops this fail-prone test from stinking up normal test runs
	 */
	@BeforeClass
	public static void activation() {
		Assume.assumeTrue( "Test activation", active );
	}

	private static final Flocessor flows = new Flocessor( "junit4 example test", new Mdl() )
			.system( State.FUL, Actrs.BEN )
			.behaviour( asrt -> {
				if( asrt.flow().meta().id().contains( "success" ) ) {
					asrt.actual().response( asrt.expected().response().content() );
				}
				else if( asrt.flow().meta().id().contains( "failure" ) ) {
					asrt.actual().response( "unexpected content!".getBytes( UTF_8 ) );
				}
				else if( asrt.flow().meta().id().contains( "error" ) ) {
					throw new IllegalArgumentException( "no thanks!" );
				}
				else {
					asrt.actual().response( "unsupported behaviour".getBytes( UTF_8 ) );
				}
			} );

	/**
	 * @return The {@link Flow} parameters
	 */
	@Parameters(name = "{0}")
	public static Collection<Object[]> flows() {
		return flows.parameters();
	}

	/**
	 * Human-readable name for the current test case
	 */
	@Parameter(0)
	public String name;

	/**
	 * The current {@link Flow}
	 */
	@Parameter(1)
	public Flow flow;

	/**
	 * Captures test outcome
	 */
	@Rule
	public FlowRule flowRule = flows.rule( () -> flow );

	/**
	 * Exercises the current {@link Flow}
	 */
	@Test
	public void test() {
		flows.process( flow );
	}
}
