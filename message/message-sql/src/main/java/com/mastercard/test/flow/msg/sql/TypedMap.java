package com.mastercard.test.flow.msg.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * We need to preserve the type data for byte arrays during json serialisation,
 * or else we just get the base64 string out on deserialisation
 */
public class TypedMap {

	@JsonProperty("pairs")
	private final List<TypedValue> pairs = new ArrayList<>();

	public TypedMap( Map<String, Object> m ) {
		m.forEach( ( k, v ) -> pairs.add( new TypedValue( k, v ) ) );
	}

	public TypedMap(
			@JsonProperty("pairs") List<TypedValue> pairs ) {
		this.pairs.addAll( pairs );
	}

	Map<String, Object> get( Map<String, Object> m ) {
		pairs.forEach( p -> m.put( p.key(), p.value() ) );
		return m;
	}

	private static class TypedValue {
		@JsonProperty("k")
		private final String key;
		@JsonProperty("v")
		private final Object value;
		@JsonProperty("b")
		private final byte[] bytes;

		protected TypedValue( String key, Object value ) {
			this( key,
					value instanceof byte[] ? null : value,
					value instanceof byte[] ? (byte[]) value : null );
		}

		public TypedValue(
				@JsonProperty("k") String key,
				@JsonProperty("v") Object value,
				@JsonProperty("b") byte[] bytes ) {
			this.key = key;
			this.value = value;
			this.bytes = bytes;
		}

		String key() {
			return key;
		}

		Object value() {
			return value != null ? value : bytes;
		}
	}
}
