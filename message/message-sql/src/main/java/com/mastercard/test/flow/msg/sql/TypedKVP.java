package com.mastercard.test.flow.msg.sql;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Preserves typing for byte arrays during jackson serialisation
 *
 * @param <K> The key type
 */
class TypedKVP<K> {
	@JsonProperty("k")
	private final K key;
	@JsonProperty("v")
	private final Object value;
	@JsonProperty("b")
	private final byte[] bytes;

	/**
	 * @param key   The pair key
	 * @param value The pair value
	 */
	TypedKVP( K key, Object value ) {
		this( key,
				value instanceof byte[] ? null : value,
				value instanceof byte[] ? (byte[]) value : null );
	}

	/**
	 * For jackson's benefit
	 *
	 * @param key   The pair key
	 * @param value The non-bytey pair value
	 * @param bytes The bytey pair value
	 */
	TypedKVP(
			@JsonProperty("k") K key,
			@JsonProperty("v") Object value,
			@JsonProperty("b") byte[] bytes ) {
		this.key = key;
		this.value = value;
		this.bytes = bytes;
	}

	/**
	 * @return The key
	 */
	K key() {
		return key;
	}

	/**
	 * @return The value
	 */
	Object value() {
		return value != null ? value : bytes;
	}
}
