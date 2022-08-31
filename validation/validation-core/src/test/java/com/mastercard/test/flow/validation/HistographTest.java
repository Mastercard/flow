package com.mastercard.test.flow.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Histograph}
 */
@SuppressWarnings("static-method")
class HistographTest {
	private static final Map<Integer, Integer> FLAT = IntStream.range( 0, 10 )
			.mapToObj( Integer::valueOf )
			.collect( Collectors.toMap( k -> k, v -> 1 ) );

	private static final Map<Integer, Integer> LINEAR = IntStream.range( 0, 10 )
			.mapToObj( Integer::valueOf )
			.collect( Collectors.toMap( k -> k, v -> v ) );

	/**
	 * Demonstrates graph height
	 */
	@Test
	void height() {
		assertEquals( ""
				+ "       45  100.00%",
				new Histograph( 0, 10, 1 ).graph( LINEAR ) );

		assertEquals( ""
				+ "        1   2.22%\n"
				+ "        5  11.11%\n"
				+ "        9  20.00%\n"
				+ "       13  28.89%\n"
				+ "       17  37.78%",
				new Histograph( 0, 10, 5 ).graph( LINEAR ) );

		assertEquals( ""
				+ "        0   0.00%\n"
				+ "        1   2.22%\n"
				+ "        2   4.44%\n"
				+ "        3   6.67%\n"
				+ "        4   8.89%\n"
				+ "        5  11.11%\n"
				+ "        6  13.33%\n"
				+ "        7  15.56%\n"
				+ "        8  17.78%\n"
				+ "        9  20.00%",
				new Histograph( 0, 10, 10 ).graph( LINEAR ) );
	}

	/**
	 * Shows what happens when the graph range is larger than the data range
	 */
	@Test
	void overRange() {
		assertEquals( ""
				+ "        0   0.00%\n"
				+ "        0   0.00%\n"
				+ "        0   0.00%\n"
				+ "        3   6.67%\n"
				+ "        7  15.56%\n"
				+ "       11  24.44%\n"
				+ "       15  33.33%\n"
				+ "        9  20.00%\n"
				+ "        0   0.00%\n"
				+ "        0   0.00%",
				new Histograph( -5, 15, 10 ).graph( LINEAR ) );

		assertEquals( ""
				+ "        0   0.00%\n"
				+ "        0   0.00%\n"
				+ "        1  10.00%\n"
				+ "        2  20.00%\n"
				+ "        2  20.00%\n"
				+ "        2  20.00%\n"
				+ "        2  20.00%\n"
				+ "        1  10.00%\n"
				+ "        0   0.00%\n"
				+ "        0   0.00%",
				new Histograph( -5, 15, 10 ).graph( FLAT ) );
	}

	/**
	 * Shows what happens when the graph range is smaller than the data range
	 */
	@Test
	void underRange() {

		assertEquals( ""
				+ "        0   0.00%\n"
				+ "        1   2.22%\n"
				+ "        2   4.44%\n"
				+ "        3   6.67%\n"
				+ "        4   8.89%\n"
				+ ">      30  66.67%",
				new Histograph( 0, 5, 5 ).graph( LINEAR ) );

		assertEquals( ""
				+ "<      10  22.22%\n"
				+ "        5  11.11%\n"
				+ "        6  13.33%\n"
				+ "        7  15.56%\n"
				+ "        8  17.78%\n"
				+ "        9  20.00%",
				new Histograph( 5, 10, 5 ).graph( LINEAR ) );

		assertEquals( ""
				+ "<       3   6.67%\n"
				+ "        3   6.67%\n"
				+ "        4   8.89%\n"
				+ "        5  11.11%\n"
				+ "        6  13.33%\n"
				+ "        7  15.56%\n"
				+ ">      17  37.78%",
				new Histograph( 3, 7, 5 ).graph( LINEAR ) );

		assertEquals( ""
				+ "<       3  30.00%\n"
				+ "        1  10.00%\n"
				+ "        1  10.00%\n"
				+ "        1  10.00%\n"
				+ "        1  10.00%\n"
				+ "        1  10.00%\n"
				+ ">       2  20.00%",
				new Histograph( 3, 7, 5 ).graph( FLAT ) );

	}
}
