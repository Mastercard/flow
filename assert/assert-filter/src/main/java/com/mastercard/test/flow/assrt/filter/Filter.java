package com.mastercard.test.flow.assrt.filter;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.assrt.filter.cli.FilterCli;
import com.mastercard.test.flow.assrt.filter.gui.FilterGui;
import com.mastercard.test.flow.report.Reader;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.data.Entry;
import com.mastercard.test.flow.report.data.Index;

/**
 * Allows the user to select which {@link Flow}s to exercise.
 */
public class Filter {

	/**
	 * The value for {@link FilterOptions#FILTER_FAILS} that provokes a search for
	 * the most recent execution report regardless of its name
	 */
	public static final String LATEST = "latest";

	private final Model model;
	private final Set<String> includeTags = FilterOptions.INCLUDE_TAGS.asList()
			.map( String::trim )
			.collect( toCollection( TreeSet::new ) );
	private final Set<String> excludeTags = FilterOptions.EXCLUDE_TAGS.asList()
			.map( String::trim )
			.collect( toCollection( TreeSet::new ) );
	private static final Pattern INDEX_PTRN = Pattern.compile( "(\\d+)" );
	private static final Pattern RANGE_PTRN = Pattern.compile( "(\\d+)-(\\d+)" );
	private final Set<Integer> indices = parseIndices( FilterOptions.INDICES.value() );

	private static final Set<Integer> parseIndices( String property ) {
		Set<Integer> indices = new TreeSet<>();
		if( property != null ) {
			for( String s : property.split( "," ) ) {
				s = s.trim();
				Matcher im = INDEX_PTRN.matcher( s );
				if( im.matches() ) {
					indices.add( Integer.parseInt( im.group( 1 ) ) );
				}
				else {
					Matcher rm = RANGE_PTRN.matcher( s );
					if( rm.matches() ) {
						IntStream.rangeClosed(
								Integer.parseInt( rm.group( 1 ) ),
								Integer.parseInt( rm.group( 2 ) ) )
								.forEach( indices::add );
					}
				}
			}
		}
		return indices;
	}

	/**
	 * The set of flows that pass the tag filters. This gets used a lot so we cache
	 * the set here rather than querying the model every time.
	 */
	private List<Flow> taggedFlows = null;

	/**
	 * Indicates that we've broken the seal on constructing flows - once this is
	 * <code>true</code> we no longer take care to avoid flow construction
	 */
	private boolean constructingFlows = false;

	/**
	 * Constructs a new filter.
	 *
	 * @param model The model that contains the filters to choose from
	 */
	public Filter( Model model ) {
		this.model = model;
	}

	/**
	 * Attempts to configure the filter from historic executions:
	 * <ul>
	 * <li>If {@link FilterOptions#FILTER_REPEAT} is <code>true</code>, then the
	 * filter settings from the previous call to {@link #save()} are loaded from
	 * storage.</li>
	 * <li>If {@link FilterOptions#FILTER_FAILS} is non-null, then configure the
	 * filters to pass those {@link Flow}s that failed in the indicated
	 * execution.</li>
	 * </ul>
	 * Otherwise this method is a no-op.
	 *
	 * @return <code>this</code>
	 */
	public Filter load() {
		if( FilterOptions.FILTER_REPEAT.isTrue() ) {
			Persistence.load().ifPresent( p -> {
				includeTags.addAll( p.includeTags() );
				excludeTags.addAll( p.excludeTags() );
				indices.addAll( p.indices() );
			} );
		}

		if( FilterOptions.FILTER_FAILS.value() != null ) {
			loadFailureIndices();
		}

		return this;
	}

	/**
	 * <p>
	 * If {@link FilterOptions#FILTER_UPDATE} has been set appropriately, then an
	 * interface is shown to the user to update filter settings. Otherwise this
	 * method is a no-op.
	 * </p>
	 * <p>
	 * If the interface is shown, this method will block until the user has finished
	 * with it.
	 * </p>
	 *
	 * @return <code>this</code>
	 */
	public Filter blockForUpdates() {
		if( FilterGui.requested() ) {
			new FilterGui( this ).blockForInput();
		}
		else if( FilterCli.requested() ) {
			new FilterCli( this ).blockForInput();
		}

		return this;
	}

	/**
	 * Saves the current filter settings to storage, ready for the next time
	 * {@link #load()} is called.
	 *
	 * @return <code>this</code>
	 */
	public Filter save() {
		new Persistence( includeTags, excludeTags, indices ).save();
		return this;
	}

	/**
	 * Gets the tags that flows must have to pass the filter.
	 *
	 * @return The current set of included tags
	 */
	public Set<String> includedTags() {
		return new TreeSet<>( includeTags );
	}

	/**
	 * Sets the tags that flows must have to pass the filter. Materially changing
	 * the tag filters may change selected index values.
	 *
	 * @param tags The new included tag values
	 * @return <code>this</code>
	 */
	public Filter includedTags( Set<String> tags ) {
		if( !tags.equals( includeTags ) ) {
			Set<Flow> current = new HashSet<>();
			if( constructingFlows && !indices.isEmpty() ) {
				flows().forEach( current::add );
			}

			includeTags.clear();
			includeTags.addAll( tags );
			matchIndexSelection( current );
		}
		return this;
	}

	/**
	 * Called when the tag filters have been changed, this method will update the
	 * index filters to retain previously-selected flows
	 *
	 * @param previous The set of flows that used to pass the index filters
	 */
	private void matchIndexSelection( Set<Flow> previous ) {
		indices.clear();
		taggedFlows = null;

		if( constructingFlows ) {
			int idx = 0;
			for( Flow flow : taggedFlows() ) {
				if( previous.contains( flow ) ) {
					indices.add( idx );
				}
				idx++;
			}

			if( indices.size() == idx ) {
				// all flows are included, so no point in the index filter values
				indices.clear();
			}
		}
	}

	/**
	 * Gets the tags that flows must not have to pass the filter.
	 *
	 * @return The current set of excluded tags
	 */
	public Set<String> excludedTags() {
		return new TreeSet<>( excludeTags );
	}

	/**
	 * Sets the tags that flows must not have to pass the filter. Materially
	 * changing the tag filters may change selected index values.
	 *
	 * @param tags The new excluded tag values
	 * @return <code>this</code>
	 */
	public Filter excludedTags( Set<String> tags ) {
		if( !tags.equals( excludeTags ) ) {
			Set<Flow> current = new HashSet<>();
			if( constructingFlows && !indices.isEmpty() ) {
				flows().forEach( current::add );
			}

			excludeTags.clear();
			excludeTags.addAll( tags );
			matchIndexSelection( current );
		}
		return this;
	}

	/**
	 * Gets the indices in the tag-filtered list of flows that pass the filter
	 *
	 * @return The current set of chosen {@link Flow} indices
	 */
	public Set<Integer> indices() {
		return new TreeSet<>( indices );
	}

	/**
	 * Sets the indices in the tag-filtered list of flows that pass the filter
	 *
	 * @param idx The new selected {@link Flow} indices
	 * @return <code>this</code>
	 */
	public Filter indices( Set<Integer> idx ) {
		int flowCount = taggedFlows().size();

		Set<Integer> valid = idx.stream()
				.filter( i -> 0 <= i && i < flowCount )
				.collect( Collectors.toSet() );

		if( valid.size() == flowCount ) {
			valid.clear();
		}

		indices.clear();
		indices.addAll( valid );
		return this;
	}

	/**
	 * Sets the indices in the tag-filtered list of flows to match those flows that
	 * failed in an execution report. The report will be chosen according to the
	 * value of {@link FilterOptions#FILTER_FAILS}, defaulting to the latest report
	 * if that property has no value.
	 *
	 * @return <code>this</code>
	 */
	public Filter loadFailureIndices() {
		Path report = historicReport();
		if( report != null ) {
			loadFailureIndices( report );
		}
		return this;
	}

	/**
	 * Finds the historic execution report that would be loaded if
	 * {@link #loadFailureIndices()} were to be called
	 *
	 * @return The report directory, or <code>null</code> if there is no such report
	 */
	public static Path historicReport() {
		String failReportDir = FilterOptions.FILTER_FAILS.orElse( () -> LATEST );
		Path dir = Paths.get( FilterOptions.ARTIFACT_DIR.value() )
				.resolve( failReportDir );
		Path report = null;
		if( Reader.isReportDir( dir ) ) {
			report = dir;
		}
		else if( LATEST.equals( failReportDir ) ) {
			report = Reader.mostRecent(
					FilterOptions.ARTIFACT_DIR.value(),
					p -> true );
		}
		return report;
	}

	/**
	 * Sets the indices in the tag-filtered list of flows to match those flows that
	 * failed in the supplied report
	 *
	 * @param report The report directory
	 * @return <code>this</code>
	 */
	public Filter loadFailureIndices( Path report ) {
		if( !Reader.isReportDir( report ) ) {
			throw new IllegalArgumentException( "Invalid report directory '" + report + "'" );
		}
		Index index = new Reader( report ).read();
		Set<Entry> unsuccessful = index.entries.stream()
				.filter( e -> !e.tags.contains( Writer.PASS_TAG ) )
				.collect( toSet() );

		Set<Integer> failures = new TreeSet<>();
		int idx = 0;
		for( Flow flow : taggedFlows() ) {
			if( unsuccessful.stream()
					.anyMatch( e -> Reader.matches( e, flow ) ) ) {
				failures.add( idx );
			}
			idx++;
		}

		indices( failures );

		return this;
	}

	/**
	 * Gets all tags in the model
	 *
	 * @return All tags available in the model
	 */
	public Set<String> allTags() {
		return model.tags().union().collect( toCollection( TreeSet::new ) );
	}

	/**
	 * Gets the list of flows that pass the tag filters
	 *
	 * @return All {@link Flow}s that pass the tag filters, in order of ID
	 */
	public List<Flow> taggedFlows() {
		constructingFlows = true;
		if( taggedFlows == null ) {
			taggedFlows = model.flows( includeTags, excludeTags )
					.filter( this::matches )
					.sorted( Comparator.comparing( f -> f.meta().id() ) )
					.collect( toList() );
		}
		return taggedFlows;
	}

	private boolean matches( Flow flow ) {
		boolean included = true;

		if( !includeTags.isEmpty() ) {
			Set<String> includeIntersection = new HashSet<>( flow.meta().tags() );
			includeIntersection.retainAll( includeTags );
			included &= !includeIntersection.isEmpty();
		}

		if( !excludeTags.isEmpty() ) {
			Set<String> excludeIntersection = new HashSet<>( flow.meta().tags() );
			excludeIntersection.retainAll( excludeTags );
			included &= excludeIntersection.isEmpty();
		}

		return included;
	}

	/**
	 * Performs filtering
	 *
	 * @return The {@link Flow}s selected by the user
	 */
	public Stream<Flow> flows() {

		List<Flow> tagged = taggedFlows();

		List<Flow> filtered = new ArrayList<>();
		if( indices.isEmpty() ) {
			filtered.addAll( tagged );
		}
		else {
			for( Integer idx : indices ) {
				filtered.add( tagged.get( idx ) );
			}
		}

		return filtered.stream();
	}

	private static class Persistence {
		private static final Path persistencePath = Paths.get(
				FilterOptions.ARTIFACT_DIR.value(), "filters.json" );

		@JsonProperty("include")
		private final Set<String> includeTags;

		@JsonProperty("exclude")
		private final Set<String> excludeTags;

		@JsonProperty("indices")
		private final Set<Integer> indices;

		Persistence(
				@JsonProperty("include") Set<String> includeTags,
				@JsonProperty("exclude") Set<String> excludeTags,
				@JsonProperty("indices") Set<Integer> indices ) {
			this.includeTags = includeTags;
			this.excludeTags = excludeTags;
			this.indices = indices;
		}

		static Optional<Persistence> load() {
			try {
				return Optional.of( new ObjectMapper()
						.readValue( persistencePath.toFile(), Persistence.class ) );
			}
			catch( @SuppressWarnings("unused") Exception e ) {
				return Optional.empty();
			}
		}

		void save() {
			try {
				Files.createDirectories( persistencePath.getParent() );
				new ObjectMapper()
						.enable( SerializationFeature.INDENT_OUTPUT )
						.enable( SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS )
						.writeValue( persistencePath.toFile(), this );
			}
			catch( @SuppressWarnings("unused") Exception e ) {
				// oh well
			}
		}

		Set<String> includeTags() {
			return includeTags;
		}

		Set<String> excludeTags() {
			return excludeTags;
		}

		Set<Integer> indices() {
			return indices;
		}
	}
}
