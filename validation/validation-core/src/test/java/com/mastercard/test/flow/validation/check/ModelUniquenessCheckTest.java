/*
 * Copyright (c) 2024 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.check;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastercard.test.flow.Model;

/**
 * Exercises {@link ModelUniquenessCheck}
 */
class ModelUniquenessCheckTest extends AbstractValidationTest {

	ModelUniquenessCheckTest() {
		super( new ModelUniquenessCheck(), "Model uniqueness",
				"Models should be uniquely identified by their title" );
	}

	private List<Model> createMockModels( List<String> titles ) {
		return titles.stream().map( title -> {
			Model model = mock( Model.class );
			when( model.title() ).thenReturn( title );
			when( model.subModels() ).thenReturn( Stream.empty() );
			return model;
		} ).collect( Collectors.toList() );
	}

	@Test
	void testModelUniquenessFailureAssertion() {
		// Create a list of model titles
		List<String> titles = Arrays.asList( "Model1", "Model2", "Model1" );

		// Create mock models
		List<Model> models = createMockModels( titles );

		// Create a parent model and set its subModels
		Model parentModel = mock( Model.class );
		when( parentModel.title() ).thenReturn( "ParentModel" );
		when( parentModel.subModels() ).thenReturn( models.stream() );

		test( parentModel, "  details: Duplicate model title found: Model1\n"
				+ " expected: null\n"
				+ "   actual: null\n"
				+ "offenders: " );

	}

	@Test
	void testModelUniquenessSuccessAssertion() {
		// Create a list of model titles
		List<String> titles = Arrays.asList( "Model1", "Model2", "Model3" );

		// Create mock models
		List<Model> models = createMockModels( titles );

		// Create a parent model and set its subModels
		Model parentModel = mock( Model.class );
		when( parentModel.title() ).thenReturn( "ParentModel" );
		when( parentModel.subModels() ).thenReturn( models.stream() );

		test( parentModel );

	}

}
