package com.mastercard.test.flow.assrt.filter.cli;

import static com.mastercard.test.flow.assrt.filter.Util.copypasta;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Demonstrates {@link Corner} values
 */
@SuppressWarnings("static-method")
class CornerTest {

	/**
	 * Illustrates corner types
	 */
	@Test
	void boxen() {
		String fmt = ""
				+ "%s\n"
				+ "%s%s%s\n"
				+ "%s%s%s\n"
				+ "%s%s%s\n";
		String expected = copypasta(
				"EMPTY",
				"   ",
				"   ",
				"   ",
				"SINGLE",
				"┌┬┐",
				"├ ┤",
				"└┴┘",
				"ROUND",
				"╭┬╮",
				"├ ┤",
				"╰┴╯",
				"DOUBLE",
				"╔╦╗",
				"╠ ╣",
				"╚╩╝" );

		assertEquals(
				expected,
				copypasta( Stream.of( Corner.values() )
						.map( c -> String.format( fmt,
								c.name(),
								c.tl, c.tTee, c.tr,
								c.lTee, " ", c.rTee,
								c.bl, c.bTee, c.br ) ) ),
				"chars" );

		assertEquals(
				expected,
				copypasta( Stream.of( Corner.values() )
						.map( c -> String.format( fmt,
								c.name(),
								c.tl(), c.tTee(), c.tr(),
								c.lTee(), " ", c.rTee(),
								c.bl(), c.bTee(), c.br() ) ) ),
				"strings" );
	}
}
