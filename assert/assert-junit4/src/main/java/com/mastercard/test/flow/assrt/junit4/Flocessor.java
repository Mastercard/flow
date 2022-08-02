package com.mastercard.test.flow.assrt.junit4;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Rule;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.AbstractFlocessor;

/**
 * Integrates {@link Flow} processing into junit 4. This should be used to
 * produce the parameters for a <a href=
 * "https://github.com/junit-team/junit4/wiki/parameterized-tests">parameterised
 * test</a>, e.g.:
 *
 * <pre>
 * &#64;RunWith(Parameterized.class)
 * public class MyTest {
 *
 * 	private static final Flocessor flows = new Flocessor( "My test name", MY_SYSTEM_MODEL )
 * 			.system( State.LESS, MY_ACTORS_UNDER_TEST )
 * 			.behaviour( asrt -&gt; {
 * 				// test behaviour
 * 			} );
 *
 * 	&#64;Parameters(name = "{0}")
 * 	public static Collection&lt;Object[]&gt; flows() {
 * 		return flows.parameters();
 * 	}
 *
 * 	&#64;Parameter(0)
 * 	public String name;
 *
 * 	&#64;Parameter(1)
 * 	public Flow flow;
 *
 * 	&#64;Rule
 * 	public FlowRule flowRule = flows.rule( () -&gt; flow );
 *
 * 	&#64;Test
 * 	public void test() {
 * 		flows.process( flow );
 * 	}
 * }
 * </pre>
 */
public class Flocessor extends AbstractFlocessor<Flocessor> {

	/**
	 * @param title A meaningful name for the test
	 * @param model The system model to exercise
	 */
	public Flocessor( String title, Model model ) {
		super( title, model );
	}

	/**
	 * Produces test parameters
	 *
	 * @return Test parameters
	 */
	public Collection<Object[]> parameters() {
		return flows()
				.map( f -> new Object[] { f.meta().id(), f } )
				.collect( Collectors.toList() );
	}

	/**
	 * Produces the {@link Rule} that skips hopeless {@link Flow}s
	 *
	 * @param flow Access to the current flow parameter
	 * @return The test {@link Rule}
	 */
	public FlowRule rule( Supplier<Flow> flow ) {
		return new FlowRule( history, flow );
	}

	@Override
	protected void skip( String reason ) {
		throw new AssumptionViolatedException( reason );
	}

	@Override
	protected void compare( String message, String expected, String actual ) {
		Assert.assertEquals( message, expected, actual );
	}

	/**
	 * Call this in your test method
	 */
	@Override
	public void process( Flow flow ) {
		super.process( flow );
	}
}
