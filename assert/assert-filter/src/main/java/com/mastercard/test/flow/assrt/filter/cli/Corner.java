package com.mastercard.test.flow.assrt.filter.cli;

/**
 * Corner strokes
 */
public enum Corner {
	/***/
	EMPTY(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
	/***/
	SINGLE('┌', '┐', '└', '┘', '├', '┤', '┬', '┴'),
	/***/
	ROUND('╭', '╮', '╰', '╯', '├', '┤', '┬', '┴'),
	/***/
	DOUBLE('╔', '╗', '╚', '╝', '╠', '╣', '╦', '╩');

	/**
	 * Top-right
	 */
	public final char tr;
	/**
	 * Top-left
	 */
	public final char tl;
	/**
	 * Bottom-right
	 */
	public final char br;
	/**
	 * Bottom-left
	 */
	public final char bl;

	/**
	 * Tee from left edge
	 */
	public final char lTee;
	/**
	 * T from right edge
	 */
	public final char rTee;
	/**
	 * T from top edge
	 */
	public final char tTee;
	/**
	 * T from bottom edge
	 */
	public final char bTee;

	Corner( char tl, char tr, char bl, char br,
			char lTee, char rTee, char tTee, char bTee ) {
		this.tr = tr;
		this.tl = tl;
		this.br = br;
		this.bl = bl;
		this.lTee = lTee;
		this.rTee = rTee;
		this.tTee = tTee;
		this.bTee = bTee;
	}

	/**
	 * As-a-string accessor
	 *
	 * @return Top-right
	 */
	public String tr() {
		return String.valueOf( tr );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return Top-left
	 */
	public String tl() {
		return String.valueOf( tl );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return Bottom-right
	 */
	public String br() {
		return String.valueOf( br );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return Bottom-left
	 */
	public String bl() {
		return String.valueOf( bl );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return Tee from left edge
	 */
	public String lTee() {
		return String.valueOf( lTee );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return T from right edge
	 */
	public String rTee() {
		return String.valueOf( rTee );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return T from top edge
	 */
	public String tTee() {
		return String.valueOf( tTee );
	}

	/**
	 * As-a-string accessor
	 *
	 * @return T from bottom edge
	 */
	public String bTee() {
		return String.valueOf( bTee );
	}
}
