/*
 * Copyright (c) 2024 Mastercard. All rights reserved.
 */

package com.mastercard.test.flow.validation.check;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.Check;
import com.mastercard.test.flow.validation.Validation;
import com.mastercard.test.flow.validation.Violation;

/**
 * Checks that all {@link Model} have a unique title
 */
public final class ModelUniquenessCheck implements Validation {
	@Override
	public String name() {
		return "Model uniqueness";
	}

	@Override
	public String explanation() {
		return "Models should be uniquely identified by their title";
	}

	@Override
	public Stream<Check> checks( Model model ) {
		Set<String> modelTitles = new HashSet<>();
		return checkModelTitles( model, modelTitles ).stream();
	}

	private Set<Check> checkModelTitles( Model model, Set<String> modelTitles ) {
		Set<Check> checks = new HashSet<>();
		if( !modelTitles.add( model.title() ) ) {
			checks.add( new Check( this, model.title(),
					() -> new Violation( this, "Duplicate model title found: " + model.title() ) ) );
		}
		model.subModels()
				.forEach( subModel -> checks.addAll( checkModelTitles( subModel, modelTitles ) ) );
		return checks;
	}
}
