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
	 * Base case
	 */
	@Test
	void empty() {
		test( leaf( "empty", new TaggedGroup() ),
				"empty : pass" );
	}

	/**
	 * Submodels are checked in isolation
	 */
	@Test
	void recursion() {
		test( branch( "branch", new TaggedGroup(),
				leaf( "left", new TaggedGroup() ),
				leaf( "right", new TaggedGroup() ) ),
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
				new TaggedGroup(),
				"a,b,c" ),
				"  details: Inaccurate tagging\n"
						+ " expected: new TaggedGroup(\"a\", \"b\", \"c\");\n"
						+ "   actual: new TaggedGroup();\n"
						+ "offenders: " );

		test( leaf( "empty flow tags",
				new TaggedGroup( "a", "b", "c" ).union( "d" ) ),
				"  details: Inaccurate tagging\n"
						+ " expected: new TaggedGroup();\n"
						+ "   actual: new TaggedGroup(\"a\", \"b\", \"c\")\n"
						+ "         .union(\"d\");\n"
						+ "offenders: " );

		test( leaf( "mismatch",
				new TaggedGroup( "a", "b", "c" ).union( "d" ),
				"a,b,c", "b,c,d" ),
				"  details: Inaccurate tagging\n"
						+ " expected: new TaggedGroup(\"b\", \"c\")\n"
						+ "         .union(\"a\", \"d\");\n"
						+ "   actual: new TaggedGroup(\"a\", \"b\", \"c\")\n"
						+ "         .union(\"d\");\n"
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
				"  details: Inaccurate tagging\n"
						+ " expected: new TaggedGroup(\"b\", \"c\", \"d\");\n"
						+ "   actual: new TaggedGroup(\"b\", \"c\");\n"
						+ "offenders: ",
				"  details: Inaccurate tagging\n"
						+ " expected: new TaggedGroup(\"b\")\n"
						+ "         .union(\"a\", \"c\", \"d\");\n"
						+ "   actual: new TaggedGroup(\"b\")\n"
						+ "         .union(\"a\", \"c\", \"x\");\n"
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
