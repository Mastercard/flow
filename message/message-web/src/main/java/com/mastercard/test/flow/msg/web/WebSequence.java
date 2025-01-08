package com.mastercard.test.flow.msg.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openqa.selenium.WebDriver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.test.flow.msg.AbstractMessage;

/**
 * <p>
 * Represents a series of web browser interactions.
 * </p>
 * <p>
 * Usage:
 * </p>
 * <ul>
 * <li>Build up the browser operations of the sequence with
 * {@link #operation(String, BiConsumer)}</li>
 * <li>Use {@link #set(String, Object)} to build up the parameter map that will
 * be supplied to the operations.</li>
 * <li>The same parameter map will be shared between all operations, so feel
 * free to update it in one operation and then use the updated parameter in a
 * later operation.</li>
 * <li>In your assertion component use {@link #process(WebDriver)} to execute
 * the operations.</li>
 * </ul>
 */
public class WebSequence extends AbstractMessage<WebSequence> {

	private static final ObjectMapper JSON = new ObjectMapper();

	private final WebSequence parent;

	private final SortedMap<String, BiConsumer<WebDriver, Map<String, String>>> operations = new TreeMap<>();

	private Map<String, String> results;

	/**
	 * Builds an empty sequence
	 */
	public WebSequence() {
		parent = null;
	}

	private WebSequence(WebSequence parent) {
		this.parent = parent;
	}

	@Override
	public WebSequence child() {
		return copyMasksTo(new WebSequence(self()));
	}

	@Override
	public WebSequence peer(byte[] bytes) {
		WebSequence peer = copyMasksTo(new WebSequence(parent));
		peer.operations.putAll(operations);
		try {
			((Map<String, String>) JSON.readValue(bytes, Map.class))
					.forEach(peer::set);
		} catch (IOException ioe) {
			throw new UncheckedIOException(String.format(
					"Failed to parse '%s' (%s)",
					new String(bytes, UTF_8), Arrays.toString(bytes)),
					ioe);
		}
		return peer;
	}

	@Override
	public byte[] content() {
		try {
			return JSON.writeValueAsBytes(parameters());
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	protected String asHuman() {
		// Get the parameters to use. If 'results' is not null, use 'results', otherwise
		// use 'parameters()'.
		Map<String, String> params = results != null ? results : parameters();

		// Find the maximum length of the parameter names. This is used for formatting.
		int nameWidth = params.keySet().stream()
				.mapToInt(String::length)
				.max().orElse(1);

		// Define the format for name-value pairs and padding for multi-line values.
		String nvpFmt = " │ %" + nameWidth + "s │ %s │";
		String padFmt = "\n │ %" + nameWidth + "s   ";
		String pad = String.format(padFmt, "");

		// Create the formatted string for operations.
		String operationsStr = operations().keySet().stream()
				.map(o -> " │ " + o + " │")
				.collect(Collectors.joining("\n"));

		// Create the formatted string for parameters.
		String paramsStr = params.entrySet().stream()
				.map(e -> String.format(nvpFmt,
						e.getKey(),
						Stream.of(e.getValue().split("\n"))
								.collect(Collectors.joining(pad))))
				.collect(Collectors.joining("\n"));

		// Create a borderline for the box drawing.
		String border = "─".repeat(nameWidth + 4);

		// Return the final formatted string with box drawing characters.
		return String.format("┌%s┐\n"
				+ "│ Operations │\n"
				+ "├%s┤\n"
				+ "%s\n"
				+ "└%s┘\n"
				+ "┌%s┐\n"
				+ "│ Parameters │ Values │\n"
				+ "├%s┤\n"
				+ "%s\n"
				+ "└%s┘",
				border, border, operationsStr, border,
				border, border, paramsStr, border);
	}

	@Override
	public Set<String> fields() {
		return new TreeSet<>(parameters().keySet());
	}

	/**
	 * Adds an operation to this interaction sequence
	 *
	 * @param name The name for the operation. Operations are processed in
	 *             alphabetical order of their name
	 * @param op   The operation, or <code>null</code> to delete an existing
	 *             operation
	 * @return <code>this</code>
	 */
	public WebSequence operation(String name,
			BiConsumer<WebDriver, Map<String, String>> op) {
		operations.put(name, op);
		return self();
	}

	@Override
	protected Object access(String field) {
		return parameters().get(field);
	}

	private SortedMap<String, BiConsumer<WebDriver, Map<String, String>>> operations() {
		SortedMap<String, BiConsumer<WebDriver, Map<String, String>>> op = new TreeMap<>();
		if (parent != null) {
			op.putAll(parent.operations());
		}
		op.putAll(operations);
		return op;
	}

	private Map<String, String> parameters() {
		Map<String, String> p = new TreeMap<>();
		if (parent != null) {
			p.putAll(parent.parameters());
		}
		for (Update update : updates) {
			if (update.value() == DELETE) {
				p.remove(update.field());
			} else {
				p.put(update.field(), String.valueOf(update.value()));
			}
		}
		return p;
	}

	/**
	 * Executes the operations in the sequence
	 *
	 * @param driver the browser to drive
	 * @return The state of the parameters map after all operations have completed
	 */
	public byte[] process(WebDriver driver) {
		Map<String, String> params = parameters();
		operations().forEach((name, op) -> {
			if (op != null) {
				try {
					op.accept(driver, params);
				} catch (Exception e) {
					// our operation has failed!
					String url = "No URL";
					String source = "No page source";
					try {
						url = driver.getCurrentUrl();
						source = driver.getPageSource();
					} catch (Exception f) {
						source = f.getMessage();
					}
					throw new IllegalStateException(String.format(
							"Operation '%s' failed on page '%s'\n%s",
							name, url, source),
							e);
				}
			}
		});
		try {
			return JSON.writeValueAsBytes(params);
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

}
