package com.mastercard.test.flow.validation.check;

import static java.util.Collections.emptySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;

/**
 * Exercises {@link DependencyLoopCheck}
 */
class DependencyLoopCheckTest extends AbstractValidationTest {

	/***/
	DependencyLoopCheckTest() {
		super( new DependencyLoopCheck(),
				"Dependency loop",
				"Dependency loops cannot be honoured during execution" );
	}

	/**
	 * No checks on the empty model
	 */
	@Test
	void empty() {
		test( mdl() );
	}

	/**
	 * There are no dependencies at all, so obviously little chance of loops
	 */
	@Test
	void noDependencies() {
		test( mdl( "a", "b", "c", "d" ),
				"a [] : pass",
				"b [] : pass",
				"c [] : pass",
				"d [] : pass" );
	}

	/**
	 * A simple straight chain of dependencies
	 */
	@Test
	void linear() {
		test( mdl( "a b", "b c", "c d", "d" ),
				"a [] : pass",
				"b [] : pass",
				"c [] : pass",
				"d [] : pass" );
	}

	/**
	 * A fork and a convergence
	 */
	@Test
	void diamond() {
		test( mdl( "a b c", "b d", "c d", "d" ),
				"a [] : pass",
				"b [] : pass",
				"c [] : pass",
				"d [] : pass" );
	}

	/**
	 * Being self-reliant is fine as it doesn't present any problem in scheduling an
	 * execution order
	 */
	@Test
	void self() {
		test( mdl( "a a" ),
				"a [] : pass" );
	}

	/**
	 * The smallest-possible bad configuration:
	 * <ul>
	 * <li>We can't schedule <code>a</code> first as it depends on
	 * <code>b</code>.</li>
	 * <li>We can't schedule <code>b</code> first as it depends on
	 * <code>a</code>.</li>
	 * </ul>
	 */
	@Test
	void codependency() {
		test( mdl( "a b", "b a" ),
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: a []\n"
						+ "b []\n"
						+ "a []\n"
						+ "offenders: a []\n"
						+ "null",
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: b []\n"
						+ "a []\n"
						+ "b []\n"
						+ "offenders: b []\n"
						+ "null" );
	}

	/**
	 * The addition of another party doesn't improve matters
	 */
	@Test
	void loopedTriple() {
		test( mdl( "a b", "b c", "c a" ),
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: a []\n"
						+ "b []\n"
						+ "c []\n"
						+ "a []\n"
						+ "offenders: a []\n"
						+ "null",
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: b []\n"
						+ "c []\n"
						+ "a []\n"
						+ "b []\n"
						+ "offenders: b []\n"
						+ "null",
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: c []\n"
						+ "a []\n"
						+ "b []\n"
						+ "c []\n"
						+ "offenders: c []\n"
						+ "null" );
	}

	/**
	 * Flows that are not strictly part of the loop still report it
	 */
	@Test
	void lollipop() {
		test( mdl( "a b", "b c", "c b" ),
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: a []\n"
						+ "b []\n"
						+ "c []\n"
						+ "b []\n"
						+ "offenders: a []\n"
						+ "null",
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: b []\n"
						+ "c []\n"
						+ "b []\n"
						+ "offenders: b []\n"
						+ "null",
				"  details: Dependency loop\n"
						+ " expected: \n"
						+ "   actual: c []\n"
						+ "b []\n"
						+ "c []\n"
						+ "offenders: c []\n"
						+ "null" );
	}

	/**
	 * @param flows A set of strings, each specifying one flow. The strings are
	 *              space-separated lists. The first element is the flow name, the
	 *              following elements are the names of dependency flows
	 * @return A model, with the specified flows and dependency structure
	 */
	private static Model mdl( String... flows ) {
		Map<String, Flow> names = new HashMap<>();

		for( String flow : flows ) {
			String[] tkns = flow.split( " " );

			Flow flw = Mockito.mock( Flow.class );
			Metadata mtdt = Mockito.mock( Metadata.class );

			when( mtdt.description() ).thenReturn( tkns[0] );
			when( mtdt.tags() ).thenReturn( emptySet() );
			when( mtdt.id() ).thenCallRealMethod();
			when( flw.meta() ).thenReturn( mtdt );

			List<Dependency> deps = new ArrayList<>();
			for( int i = 1; i < tkns.length; i++ ) {
				String depName = tkns[i];
				FieldAddress src = mock( FieldAddress.class );
				when( src.flow() ).thenAnswer( a -> names.get( depName ) );
				Dependency dep = mock( Dependency.class );
				when( dep.source() ).thenReturn( src );
				deps.add( dep );
			}
			when( flw.dependencies() ).thenAnswer( a -> deps.stream() );

			names.put( tkns[0], flw );
		}

		Model mdl = Mockito.mock( Model.class );
		when( mdl.flows() ).thenReturn( names.values().stream() );
		return mdl;
	}
}
