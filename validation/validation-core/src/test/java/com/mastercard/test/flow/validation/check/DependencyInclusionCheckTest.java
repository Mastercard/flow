package com.mastercard.test.flow.validation.check;

import static java.util.Collections.emptySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Dependency;
import com.mastercard.test.flow.FieldAddress;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;

/**
 * Exercises {@link DependencyInclusionCheck}
 */
class DependencyInclusionCheckTest extends AbstractValidationTest {

	/***/
	DependencyInclusionCheckTest() {
		super( new DependencyInclusionCheck(),
				"Dependency inclusion",
				"Dependency sources must be included in the system model" );
	}

	/**
	 * No flows
	 */
	@Test
	void empty() {
		test( mdl( flws().values() ) );
	}

	/**
	 * A single flow
	 */
	@Test
	void lonesome() {
		test( mdl( flws( "lonely" ).values() ) );
	}

	/**
	 * Two flows, but no dependencies
	 */
	@Test
	void noDependencies() {
		test( mdl( flws( "abc", "def" ).values() ) );
	}

	/**
	 * Two flows, a dependency, the sink is included in the model
	 */
	@Test
	void included() {
		test( mdl( flws( "abc", "def abc" ).values() ),
				"abc [] → def [] : pass" );
	}

	/**
	 * Two flows, a dependency, the sink is <i>not</i> included in the model
	 */
	@Test
	void excluded() {
		Map<String, Flow> flows = flws( "abc", "def abc" );
		flows.remove( "abc" );
		test( mdl( flows.values() ),
				"  details: Dependency source 'abc []' not presented in system model\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: def []\n"
						+ "null" );
	}

	/**
	 * One valid dependency, one violation
	 */
	@Test
	void mixed() {
		Map<String, Flow> flows = flws(
				"abc", "def abc",
				"ghi", "jkl ghi" );
		flows.remove( "ghi" );
		test( mdl( flows.values() ),
				"abc [] → def [] : pass",
				"  details: Dependency source 'ghi []' not presented in system model\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: jkl []\n"
						+ "null" );
	}

	/**
	 * Multiple valid dependencies
	 */
	@Test
	void multiIncluded() {
		test( mdl( flws( "abc", "def abc", "ghi abc def", "jkl abc def ghi" ).values() ),
				"abc [] → def [] : pass",
				"abc [] → ghi [] : pass",
				"def [] → ghi [] : pass",
				"abc [] → jkl [] : pass",
				"def [] → jkl [] : pass",
				"ghi [] → jkl [] : pass" );
	}

	/**
	 * Multiple invalid dependencies
	 */
	@Test
	void multiExcluded() {
		Map<String, Flow> flows = flws( "abc", "def abc", "ghi abc def", "jkl abc def ghi" );
		flows.remove( "def" );
		test( mdl( flows.values() ),
				"abc [] → ghi [] : pass",
				"  details: Dependency source 'def []' not presented in system model\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: ghi []\n"
						+ "null",
				"abc [] → jkl [] : pass",
				"  details: Dependency source 'def []' not presented in system model\n"
						+ " expected: null\n"
						+ "   actual: null\n"
						+ "offenders: jkl []\n"
						+ "null",
				"ghi [] → jkl [] : pass" );
	}

	/**
	 * @param flows A set of strings, each specifying one flow. The strings are
	 *              space-separated lists. The first element is the flow name, the
	 *              following elements are the names of dependency flows
	 * @return A model, with the specified flows and dependency structure
	 */
	private static Map<String, Flow> flws( String... flows ) {
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
				when( src.getFlow() ).thenReturn( Optional.ofNullable( names.get( depName ) ) );
				when( src.flow() ).thenAnswer( a -> names.get( depName ) );

				FieldAddress snk = mock( FieldAddress.class );
				when( snk.getFlow() ).thenReturn( Optional.of( flw ) );
				when( snk.flow() ).thenReturn( flw );

				Dependency dep = mock( Dependency.class );
				when( dep.source() ).thenReturn( src );
				when( dep.sink() ).thenReturn( snk );

				deps.add( dep );
			}
			when( flw.dependencies() ).thenAnswer( a -> deps.stream() );

			names.put( tkns[0], flw );
		}

		return names;
	}

	/**
	 * @param flows Some {@link Flow}s
	 * @return a {@link Model} that produces the supplied {@link Flow}s
	 */
	private static Model mdl( Collection<Flow> flows ) {
		Model mdl = Mockito.mock( Model.class );
		when( mdl.flows() ).thenAnswer( a -> flows.stream() );
		return mdl;
	}
}
