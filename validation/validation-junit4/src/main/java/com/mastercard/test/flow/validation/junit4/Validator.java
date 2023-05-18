package com.mastercard.test.flow.validation.junit4;

import static java.util.stream.Collectors.toList;

import java.util.Collection;

import org.junit.Assert;
import org.junit.runners.Parameterized;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.AbstractValidator;
import com.mastercard.test.flow.validation.Check;

/**
 * Validates a {@link Model} using JUnit4. This should be used to produce the
 * parameters for a <a href=
 * "https://github.com/junit-team/junit4/wiki/parameterized-tests">parameterised
 * test</a>, e.g.:
 *
 * <pre>
 * &#64;RunWith(Parameterized.class)
 * public class MyTest {
 * 	&#64;Parameters(name = "{0}")
 * 	public static Collection&lt;Object[]&gt; flows() {
 * 		return new Validator()
 * 				.checking( MY_SYSTEM_MODEL )
 * 				.with( AbstractValidator.defaultChecks() )
 * 				.parameters();
 * 	}
 *
 * 	&#64;Parameter(0)
 * 	public String name;
 *
 * 	&#64;Parameter(1)
 * 	public Runnable check;
 *
 * 	&#64;Test
 * 	public void test() {
 * 		check.run();
 * 	}
 * }
 * </pre>
 */
public class Validator extends AbstractValidator<Validator> {

	/**
	 * Call this to supply the parameters for your {@link Parameterized} test
	 *
	 * @return A list of <code>{test name, runnable}</code> test parameter pairs
	 */
	public Collection<Object[]> parameters() {
		return validations()
				.flatMap( this::batchedChecks )
				.map( this::parameterPair )
				.collect( toList() );
	}

	private Object[] parameterPair( Check check ) {
		return new Object[] {
				check.name(),
				(Runnable) () -> test( check ) };
	}

	private void test( Check check ) {
		check.check()
				.filter( v -> !accepted( v ) )
				.ifPresent( violation -> {
					String message = String.format(
							"%s\n%s\n%s",
							check.validation().explanation(),
							violation.details(),
							violation.offenderString() );
					if( violation.expected() != null && violation.actual() != null ) {
						Assert.assertEquals(
								message,
								violation.expected(),
								violation.actual() );
					}
					else {
						Assert.fail( message );
					}
				} );
	}
}
