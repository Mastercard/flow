package com.mastercard.test.flow.validation.check;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.util.TaggedGroup;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that <code>EagerModel</code> implementations report their tags
 * accurately via reflection and via method call
 */
public class LazyModelTaggingCheck implements Validation {

	/**
	 * The name of the <code>public static final TaggedGroup</code> field that
	 * lazily constructed model types are assumed to have
	 */
	public static final String MODEL_TAGS_FIELD_NAME = "MODEL_TAGS";

	@Override
	public String name() {
		return "Lazy model tagging";
	}

	@Override
	public String explanation() {
		return "Lazily constructed models report tags correctly";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		return buildChecks( model, new ArrayList<>() ).stream();
	}

	private List<Check> buildChecks( Model model, List<Check> checks ) {
		Optional<Field> mf = Stream.of( model.getClass().getDeclaredFields() )
				.filter( f -> Modifier.isPublic( f.getModifiers() ) )
				.filter( f -> Modifier.isStatic( f.getModifiers() ) )
				.filter( f -> Modifier.isFinal( f.getModifiers() ) )
				.filter( f -> TaggedGroup.class.isAssignableFrom( f.getType() ) )
				.filter( f -> "MODEL_TAGS".equals( f.getName() ) )
				.findFirst();

		if( mf.isPresent() ) {
			try {
				TaggedGroup reflective = (TaggedGroup) mf.get().get( null );
				TaggedGroup method = model.tags();

				checks.add( new Check( this, model.title(), () -> reflective != method
						? new Violation( this, String.format(
								"%s.tags() should just return %s",
								model.getClass().getName(), MODEL_TAGS_FIELD_NAME ) )
						: null ) );
			}
			catch( Exception e ) {
				throw new IllegalStateException( "Failed to access " + mf.get(), e );
			}
		}

		model.subModels().forEach( child ->

		buildChecks( child, checks ) );

		return checks;
	}

}
