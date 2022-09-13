package com.mastercard.test.flow.validation.graph;

/**
 * Used to exercise the other classes
 */
class Item {
	private final int value;

	/**
	 * @param value Item value
	 */
	public Item( int value ) {
		this.value = value;
	}

	@Override
	public String toString() {
		return String.valueOf( value );
	}

	/**
	 * The distance between two {@link Item}s
	 *
	 * @param a An {@link Item}
	 * @param b Another {@link Item}
	 * @return The distance between the {@link Item}s
	 */
	public static int distance( Item a, Item b ) {
		return Math.abs( a.value - b.value );
	}

	/**
	 * The distance between two stringified {@link Item}s
	 *
	 * @param a The result of calling {@link #toString()} on an {@link Item}
	 * @param b The result of calling {@link #toString()} on an {@link Item}
	 * @return The distance between the {@link Item}s
	 */
	public static int distance( String a, String b ) {
		return Math.abs( Integer.parseInt( a ) - Integer.parseInt( b ) );
	}
}
