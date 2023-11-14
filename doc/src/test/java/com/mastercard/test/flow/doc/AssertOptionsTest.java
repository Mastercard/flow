package com.mastercard.test.flow.doc;

import static java.util.stream.Collectors.joining;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.filter.FilterOptions;
import com.mastercard.test.flow.autodoc.Docs;
import com.mastercard.test.flow.util.Option;

/**
 * Ensures that the table of system properties in the assert-core readme is
 * accurate
 */
@SuppressWarnings("static-method")
class AssertOptionsTest {

	/**
	 * Keeps the system property table in the assert-core readme fresh
	 *
	 * @throws Exception If something goes wrong
	 */
	@Test
	void core() throws Exception {
		List<String> table = new ArrayList<>();
		table.add( "| property | description |" );
		table.add( "| -------- | ----------- |" );

		// There are options defined in multiple projects and some unavoidable
		// overlaps, collate them together before regenerating the documentation
		Map<String, Option> aggregated = new TreeMap<>();
		Stream.of(
				AssertionOptions.values(),
				FilterOptions.values() )
				.flatMap( Stream::of )
				.forEach( o -> aggregated.put( o.property(), o ) );

		for( Option o : aggregated.values() ) {
			table.add( String.format( "| `%s` | %s |",
					o.property(), o.description() ) );
		}

		Docs.insert( Paths.get( "../assert/assert-core/README.md" ),
				"<!-- start_property_table -->",
				c -> table.stream().collect( joining( "\n" ) ),
				"<!-- end_property_table -->",
				Assertions::assertEquals );
	}

	/**
	 * Keeps the system property table in the assert-filter readme fresh
	 *
	 * @throws Exception If something goes wrong
	 */
	@Test
	void filter() throws Exception {
		List<String> table = new ArrayList<>();
		table.add( "| property | description |" );
		table.add( "| -------- | ----------- |" );

		for( Option o : FilterOptions.values() ) {
			table.add( String.format( "| `%s` | %s |",
					o.property(), o.description() ) );
		}

		Docs.insert( Paths.get( "../assert/assert-filter/README.md" ),
				"<!-- start_property_table -->",
				c -> table.stream().collect( joining( "\n" ) ),
				"<!-- end_property_table -->",
				Assertions::assertEquals );
	}

}
