
package com.mastercard.test.flow.validation.check;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;

/**
 * Exercises {@link ModelTaggingCheck}
 */
class ModelTaggingCheckTest extends AbstractValidationTest {

	/***/
	ModelTaggingCheckTest() {
		super( new ModelTaggingCheck(), "Model tagging",
				"Models have the minimal superset of flow tags" );
	}

	/**
	 * Base case - models with no flows should have null tags
	 */
	@Test
	void empty() {
		test( leaf( "empty", null ),
				"empty : pass" );
	}

	/**
	 * Submodels are checked in isolation
	 */
	@Test
	void recursion() {
		test( branch( "branch", null,
				leaf( "left", null ),
				leaf( "right", null ) ),
				"left : pass",
				"right : pass",
				"branch : pass" );
	}

	/**
	 * Accurate tagging raises no violation
	 */
	@Test
	void accurate() {
		test( leaf( "leaf",
				new TaggedGroup( "b", "c" ).union( "a", "d" ),
				"a,b,c", "b,c,d" ),
				"leaf : pass" );
	}

	/**
	 * Bad tagging raises a violation
	 */
	@Test
	void violation() {
		test( leaf( "empty model tags",
				null,
				"a,b,c" ),
				"  details: Inaccurate tagging in model: empty model tags\n"
						+ " expected: null;\n"
						+ "   actual: new TaggedGroup(\"a\", \"b\", \"c\");\n"
						+ "offenders: " );

		test( leaf( "empty flow tags",
				new TaggedGroup( "a", "b", "c" ).union( "d" ) ),
				"  details: Inaccurate tagging in model: empty flow tags\n"
						+ " expected: new TaggedGroup(\"a\", \"b\", \"c\")\n"
						+ "         .union(\"d\");\n"
						+ "   actual: null;\n"
						+ "offenders: " );

		test( leaf( "mismatch",
				new TaggedGroup( "a", "b", "c" ).union( "d" ),
				"a,b,c", "b,c,d" ),
				"  details: Inaccurate tagging in model: mismatch\n"
						+ " expected: new TaggedGroup(\"a\", \"b\", \"c\")\n"
						+ "         .union(\"d\");\n"
						+ "   actual: new TaggedGroup(\"b\", \"c\")\n"
						+ "         .union(\"a\", \"d\");\n"
						+ "offenders: " );
	}

	/**
	 * Meta-model tagging is also checked against the flows
	 */
	@Test
	void recursiveViolation() {

		test( branch( "branch", new TaggedGroup( "b" ).union( "a", "c", "x" ),
				leaf( "left", new TaggedGroup( "a", "b" ), "a,b" ),
				leaf( "right", new TaggedGroup( "b", "c" ), "b,c,d" ) ),
				"left : pass",
				"  details: Inaccurate tagging in model: right\n"
						+ " expected: new TaggedGroup(\"b\", \"c\");\n"
						+ "   actual: new TaggedGroup(\"b\", \"c\", \"d\");\n"
						+ "offenders: ",
				"  details: Inaccurate tagging in model: branch\n"
						+ " expected: new TaggedGroup(\"b\")\n"
						+ "         .union(\"a\", \"c\", \"x\");\n"
						+ "   actual: new TaggedGroup(\"b\")\n"
						+ "         .union(\"a\", \"c\", \"d\");\n"
						+ "offenders: " );
	}

	private static Model branch( String title, TaggedGroup tags, Model... children ) {
		Model mdl = Mockito.mock( Model.class );
		Mockito.when( mdl.title() ).thenReturn( title );
		Mockito.when( mdl.tags() ).thenReturn( tags );
		Mockito.when( mdl.subModels() ).thenReturn( Stream.of( children ) );
		Mockito.when( mdl.flows() ).thenReturn( Stream.of( children ).flatMap( Model::flows ) );
		return mdl;
	}

	private static Model leaf( String title, TaggedGroup tags, String... flowTags ) {
		Model mdl = Mockito.mock( Model.class );
		Mockito.when( mdl.title() ).thenReturn( title );
		Mockito.when( mdl.tags() ).thenReturn( tags );

		List<Flow> flows = Stream.of( flowTags )
				.map( t -> {
					Metadata meta = Mockito.mock( Metadata.class );
					Mockito.when( meta.tags() )
							.thenReturn( Stream.of( t.split( "," ) ).collect( Collectors.toSet() ) );
					Flow flw = Mockito.mock( Flow.class );
					Mockito.when( flw.meta() ).thenReturn( meta );
					return flw;
				} )
				.collect( Collectors.toList() );

		Mockito.when( mdl.flows() ).thenAnswer( i -> flows.stream() );

		return mdl;
	}
}
