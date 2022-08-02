package com.mastercard.test.flow.validation.check;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that models correctly report the tags of their flows. This is
 * important for:
 * <dl>
 * <dt>Correctness</dt>
 * <dd>If the model fails to report a tag that a constituent flow has then that
 * flow may not be built, and hence exercised, when it should be.</dd>
 * <dt>Performance</dt>
 * <dd>If the model reports tags that the constituent flows do not have then we
 * could waste time building flows that will not be exercised.</dd>
 * </dl>
 */
public class ModelTaggingCheck implements Validation {

	@Override
	public String name() {
		return "Model tagging";
	}

	@Override
	public String explanation() {
		return "Models have the minimal superset of flow tags";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		return buildChecks( model, new ArrayList<>() ).stream();
	}

	private List<Check> buildChecks( Model model, List<Check> checks ) {
		// best if we check the leaves of the model tree first
		model.subModels().forEach( child -> buildChecks( child, checks ) );

		checks.add( new Check( this, model.title(), () -> {
			Set<String> flowUnionTags = new TreeSet<>();
			Set<String> flowIntersectionTags = new TreeSet<>();
			AtomicBoolean intersectionInitiated = new AtomicBoolean( false );

			model.flows().forEach( flow -> {
				flowUnionTags.addAll( flow.meta().tags() );
				if( intersectionInitiated.get() ) {
					flowIntersectionTags.retainAll( flow.meta().tags() );
				}
				else {
					flowIntersectionTags.addAll( flow.meta().tags() );
					intersectionInitiated.set( true );
				}
			} );
			flowUnionTags.removeAll( flowIntersectionTags );

			Set<String> modelUnionTags = model.tags().union()
					.collect( Collectors.toCollection( TreeSet::new ) );
			Set<String> modelIntersectionTags = model.tags().intersection()
					.collect( Collectors.toCollection( TreeSet::new ) );
			modelUnionTags.removeAll( modelIntersectionTags );

			String expected = formatCopypasta( modelUnionTags, modelIntersectionTags );
			String actual = formatCopypasta( flowUnionTags, flowIntersectionTags );

			if( !expected.equals( actual ) ) {
				return new Violation( this, "Inaccurate tagging", expected, actual );
			}

			return null;
		} ) );

		return checks;
	}

	private static final String QUOTE_COMMA_QUOTE = "\", \"";
	private static final String QUOTE = "\"";

	private static String formatCopypasta( Set<String> union, Set<String> intersection ) {
		StringBuilder sb = new StringBuilder( "new TaggedGroup(" );
		if( !intersection.isEmpty() ) {
			sb.append( intersection.stream()
					.collect( joining( QUOTE_COMMA_QUOTE, QUOTE, QUOTE ) ) );
		}
		if( !union.isEmpty() ) {
			sb.append( ")\n         .union(" );
			sb.append( union.stream()
					.collect( joining( QUOTE_COMMA_QUOTE, QUOTE, QUOTE ) ) );
			sb.append( ")" );
		}
		sb.append( ";" );
		return sb.toString();
	}

}
