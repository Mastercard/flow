package com.mastercard.test.flow.msg.sql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Preserves value typing during jackson serialisation
 *
 * @param <K> The key type
 */
@JsonInclude(Include.NON_NULL)
class TypedKVP<K> {
	@JsonProperty("key")
	private final K key;

	@JsonProperty("bit")
	private final Boolean bit;

	@JsonProperty("tinyint")
	private final Byte tinyint;

	@JsonProperty("smallint")
	private final Short smallint;

	@JsonProperty("integer")
	private final Integer integer;

	@JsonProperty("bigint")
	private final Long bigint;

	@JsonProperty("biggerint")
	private final BigInteger biggerint;

	@JsonProperty("real")
	private final Float real;

	@JsonProperty("double")
	private final Double _double;

	@JsonProperty("decimal")
	private final BigDecimal decimal;

	@JsonProperty("character")
	private final Character character;

	@JsonProperty("varchar")
	private final String varchar;

	@JsonProperty("blob")
	private final byte[] blob;

	@JsonProperty("date")
	private final Date date;

	@JsonProperty("time")
	private final Time time;

	@JsonProperty("timestamp")
	private final Timestamp timestamp;

	@JsonProperty("value")
	private final Object value;

	/**
	 * @param key   The pair key
	 * @param value The pair value
	 */
	TypedKVP( K key, Object value ) {
		this.key = key;

		bit = value instanceof Boolean ? (Boolean) value : null;
		tinyint = value instanceof Byte ? (Byte) value : null;
		smallint = value instanceof Short ? (Short) value : null;
		integer = value instanceof Integer ? (Integer) value : null;
		bigint = value instanceof Long ? (Long) value : null;
		biggerint = value instanceof BigInteger ? (BigInteger) value : null;
		real = value instanceof Float ? (Float) value : null;
		_double = value instanceof Double ? (Double) value : null;
		decimal = value instanceof BigDecimal ? (BigDecimal) value : null;
		character = value instanceof Character ? (Character) value : null;
		varchar = value instanceof String ? (String) value : null;
		blob = value instanceof byte[] ? (byte[]) value : null;
		date = value instanceof Date ? (Date) value : null;
		time = value instanceof Time ? (Time) value : null;
		timestamp = value instanceof Timestamp ? (Timestamp) value : null;

		this.value = Stream.of(
				bit, tinyint, smallint, integer, bigint, biggerint,
				real, _double, decimal,
				character, varchar,
				blob,
				date, time, timestamp )
				.anyMatch( Objects::nonNull ) ? null : value;
	}

	/**
	 * For jackson's benefit
	 */
	@SuppressWarnings("javadoc")
	TypedKVP(
			@JsonProperty("key") K key,
			@JsonProperty("bit") Boolean bit,
			@JsonProperty("tinyint") Byte tinyint,
			@JsonProperty("smallint") Short smallint,
			@JsonProperty("integer") Integer integer,
			@JsonProperty("bigint") Long bigint,
			@JsonProperty("biggerint") BigInteger biggerint,
			@JsonProperty("real") Float real,
			@JsonProperty("double") Double _double,
			@JsonProperty("decimal") BigDecimal decimal,
			@JsonProperty("character") Character character,
			@JsonProperty("varchar") String varchar,
			@JsonProperty("blob") byte[] blob,
			@JsonProperty("date") Date date,
			@JsonProperty("time") Time time,
			@JsonProperty("timestamp") Timestamp timestamp,
			@JsonProperty("value") Object value ) {
		this.key = key;
		this.bit = bit;
		this.tinyint = tinyint;
		this.smallint = smallint;
		this.integer = integer;
		this.bigint = bigint;
		this.biggerint = biggerint;
		this.real = real;
		this._double = _double;
		this.decimal = decimal;
		this.character = character;
		this.varchar = varchar;
		this.blob = blob;
		this.date = date;
		this.time = time;
		this.timestamp = timestamp;
		this.value = value;
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
		return Stream.of(
				bit, tinyint, smallint, integer, bigint, biggerint,
				real, _double, decimal,
				character, varchar,
				blob,
				date, time, timestamp,
				value )
				.filter( Objects::nonNull )
				.findFirst()
				.orElse( null );
	}
}
