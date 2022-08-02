package com.mastercard.test.flow.example.app.model.rsd;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastercard.test.flow.Residue;

/**
 * Represents the changes to the items table that we expect a flow to make
 */
public class DBItems implements Residue {

	@JsonProperty("updates")
	private final Map<String, Update> updates = new TreeMap<>();

	/***/
	public DBItems() {
	}

	private DBItems( DBItems toCopy ) {
		updates.putAll( toCopy.updates );
	}

	@Override
	public String name() {
		return "DB items";
	}

	/**
	 * Adds an expected item addition
	 *
	 * @param id    item id
	 * @param value item value
	 * @return <code>this</code>
	 */
	public DBItems added( String id, String value ) {
		updates.put( id, new Update( null, value ) );
		return this;
	}

	/**
	 * Adds an expected item update
	 *
	 * @param id     item id
	 * @param before old item value
	 * @param after  new item value
	 * @return <code>this</code>
	 */
	public DBItems updated( String id, String before, String after ) {
		updates.put( id, new Update( before, after ) );
		return this;
	}

	/**
	 * Adds an expected item removal
	 *
	 * @param id    item id
	 * @param value item value
	 * @return <code>this</code>
	 */
	public DBItems removed( String id, String value ) {
		updates.put( id, new Update( value, null ) );
		return this;
	}

	/**
	 * @return The updates
	 */
	public Map<String, Update> updates() {
		return updates;
	}

	@Override
	public Residue child() {
		return new DBItems( this );
	}

	/***/
	public static class Update {
		/**
		 * Old value
		 */
		@JsonProperty("before")
		public final String before;
		/**
		 * New value
		 */
		@JsonProperty("after")
		public final String after;

		/**
		 * @param before Old value
		 * @param after  New value
		 */
		public Update( String before, String after ) {
			this.before = before;
			this.after = after;
		}
	}
}
