package com.mastercard.test.flow.report.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Context;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Residue;

/**
 * Encapsulates a {@link Flow}'s data in the report
 */
public class FlowData {

	/**
	 * @see Metadata#description()
	 */
	@JsonProperty("description")
	public final String description;
	/**
	 * @see Metadata#tags()
	 */
	@JsonProperty("tags")
	public final Set<String> tags;
	/**
	 * @see Metadata#motivation()
	 */
	@JsonProperty("motivation")
	public final String motivation;
	/**
	 * @see Metadata#tags()
	 */
	@JsonProperty("trace")
	public final String trace;
	/**
	 * @see Flow#basis()
	 */
	@JsonProperty("basis")
	public final String basis;
	/**
	 * @see Flow#dependencies()
	 */
	@JsonProperty("dependencies")
	public final Map<String, DependencyData> dependencies;
	/**
	 * @see Flow#root()
	 */
	@JsonProperty("root")
	public final InteractionData root;

	/**
	 * @see Flow#context()
	 */
	@JsonProperty("context")
	public final Map<String, Object> context;

	/**
	 * @see Flow#residue()
	 */
	@JsonProperty("residue")
	public final List<ResidueData> residue;

	/**
	 * A record of system events that happened when the {@link Flow} was exercised
	 */
	@JsonProperty("logs")
	public final List<LogEvent> logs;

	/**
	 * @param description  Disambiguation for {@link Flow}s with the same tags
	 * @param tags         {@link Flow} identity
	 * @param motivation   Why the {@link Flow} exists
	 * @param trace        Where the {@link Flow} is created
	 * @param basis        The parent {@link Flow}
	 * @param dependencies A map from dependency path to dependency human ID
	 * @param root         First interaction in the {@link Flow}
	 * @param context      A map from {@link Context#name()} to {@link Context}
	 *                     object. We'll leave jackson to serialise the contents
	 * @param residue      A map from {@link Residue} name to residue data
	 * @param logs         A record of system events that happened when the
	 *                     {@link Flow} was exercised
	 */
	public FlowData(
			@JsonProperty("description") String description,
			@JsonProperty("tags") Set<String> tags,
			@JsonProperty("motivation") String motivation,
			@JsonProperty("trace") String trace,
			@JsonProperty("basis") String basis,
			@JsonProperty("dependencies") Map<String, DependencyData> dependencies,
			@JsonProperty("root") InteractionData root,
			@JsonProperty("context") Map<String, Object> context,
			@JsonProperty("residue") List<ResidueData> residue,
			@JsonProperty("logs") List<LogEvent> logs ) {
		this.description = description;
		this.tags = new TreeSet<>( tags );
		this.motivation = motivation;
		this.trace = trace;
		this.basis = basis;
		this.dependencies = dependencies;
		this.root = root;
		this.context = new TreeMap<>( context );
		this.residue = new ArrayList<>( residue );
		this.logs = logs;
	}

}
