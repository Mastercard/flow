package com.mastercard.test.flow.example.app.model;

import static com.mastercard.test.flow.validation.AbstractValidator.defaultChecks;
import static com.mastercard.test.flow.validation.MessageHash.Include.REQUESTS;
import static com.mastercard.test.flow.validation.MessageHash.Include.RESPONSES;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.mastercard.test.flow.Actor;
import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
import com.mastercard.test.flow.validation.InheritanceHealth;
import com.mastercard.test.flow.validation.MessageHash;
import com.mastercard.test.flow.validation.junit5.Validator;

/**
 * Validates that the model is well-formed
 */
@SuppressWarnings("static-method")
class ExampleSystemTest {

	/**
	 * @return test instances
	 */
	@TestFactory
	Stream<DynamicNode> checks() {
		return new Validator()
				.checking( ExampleSystem.MODEL )
				.with( defaultChecks() )
				.tests();
	}

	/**
	 * Checks message content against known-good hash values. This allows us to:
	 * <ul>
	 * <li>Detect unexpected updates to test behaviour: if a hash value changes then
	 * the reviewer needs to understand why.</li>
	 * <li>Refactor the model while having confidence that test behaviour has not
	 * changed: if the hashes are the same then test behaviour is the same.</li>
	 * </ul>
	 */
	@Test
	void messageHashes() {
		MessageHash mh = new MessageHash( Assertions::assertEquals )
				.hashingEverything();

		for( Actor actor : Actors.values() ) {
			mh.hashing( actor, REQUESTS )
					.hashing( actor, RESPONSES );
		}

		mh.expect( ExampleSystem.MODEL,
				"ALL MESSAGES",
				"D1FA41700482366067D91A38D0C4E233 0122 21.3 KiB",
				"REQUESTS --> USER",
				"00000000000000000000000000000000 0000 0 B",
				"RESPONSES <-- USER",
				"00000000000000000000000000000000 0000 0 B",
				"REQUESTS --> OPS",
				"00000000000000000000000000000000 0000 0 B",
				"RESPONSES <-- OPS",
				"00000000000000000000000000000000 0000 0 B",
				"REQUESTS --> WEB_UI",
				"689868331E13925ECA7F0DFB9ABEE023 0002 175 B",
				"RESPONSES <-- WEB_UI",
				"7F6603B1CDD0593C7CF9771790C07911 0002 151 B",
				"REQUESTS --> UI",
				"323C8E3297DC898D2793EDDF60D8C5EC 0014 1.9 KiB",
				"RESPONSES <-- UI",
				"A670C078235AEB12636A0D130438A0BF 0014 2.6 KiB",
				"REQUESTS --> CORE",
				"681B2BB38FB90027F44C8467727F8D58 0015 2.3 KiB",
				"RESPONSES <-- CORE",
				"11BFA337817BB5B4C8AE414F410F271E 0015 3.7 KiB",
				"REQUESTS --> QUEUE",
				"3A79459ABD8CA52EA136B5DC00F15787 0007 975 B",
				"RESPONSES <-- QUEUE",
				"63C8D46EC4602B3FDC93973FCC286190 0007 1.3 KiB",
				"REQUESTS --> STORE",
				"A01276774007C68063E237FE73FC1C95 0006 939 B",
				"RESPONSES <-- STORE",
				"50EF3C22818B95725B78A802470AD28B 0006 1.1 KiB",
				"REQUESTS --> DB",
				"A06D030C1A8192CFE446CD5893217196 0008 2.6 KiB",
				"RESPONSES <-- DB",
				"3F7C757B6981B4A5844357C399720641 0008 1.1 KiB",
				"REQUESTS --> HISTOGRAM",
				"51B9735B70AE590E97230E245263CC7B 0009 1.1 KiB",
				"RESPONSES <-- HISTOGRAM",
				"4C0832B1D6CB88891B5D6C902D35F7B1 0009 1.6 KiB" );
	}

	/**
	 * <p>
	 * Checks the health of the inheritance structure. Choosing an inappropriate
	 * inheritance basis for a new {@link Flow} creates technical debt - the
	 * extraneous message updates that could have been avoided with a better basis
	 * choice.
	 * </p>
	 * <p>
	 * This test will be updated every time a flow is added. If the increase in the
	 * "Actual" cost metric is higher than the rise in the "Optimal" metric, then
	 * that's an indicator that a better basis choice is available.
	 * </p>
	 * <p>
	 * Note that this is only a very rough guide - human considerations of code
	 * organisation almost certainly take precedence.
	 * </p>
	 */
	@Test
	void inheritanceHealth() {
		new InheritanceHealth( 0, 150, 20, Assertions::assertEquals )
				.expect( ExampleSystem.MODEL,
						"┌───────────────────────────────────┐",
						"│Total Debt :                    196│",
						"├─────Actual──────┬─────Optimal─────┤",
						"|roots         362│roots         120|",
						"|edges         398│edges         444|",
						"|total         760│total         564|",
						"|        3  25.00%│        3  21.43%|",
						"|        1   8.33%│        2  14.29%|",
						"|        1   8.33%│        3  21.43%|",
						"|        2  16.67%│        3  21.43%|",
						"|        1   8.33%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        2  16.67%│        0   0.00%|",
						"|        1   8.33%│        1   7.14%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        1   7.14%|",
						"|        0   0.00%│        0   0.00%|",
						"|        1   8.33%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        1   7.14%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"└─────────────────┴─────────────────┘" );
	}
}
