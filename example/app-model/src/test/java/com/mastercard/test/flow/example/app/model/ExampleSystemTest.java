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
				"091D205B75B0D8044227E52CCC7B70D6 0122 21.4 KiB",
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
				"7B274E372CB060D8AD2E171A1308684D 0014 1.9 KiB",
				"RESPONSES <-- UI",
				"2A8921FF7B8A91F7503AD3A6CD8F11D6 0014 2.6 KiB",
				"REQUESTS --> CORE",
				"1DF594DDAA7A732ED57E86BBF5A46348 0015 2.3 KiB",
				"RESPONSES <-- CORE",
				"D4A0C1DF9D0851FE08B0D622C9A911C9 0015 3.7 KiB",
				"REQUESTS --> QUEUE",
				"CB06D74485A51EE84AA11C58B7BBE764 0007 975 B",
				"RESPONSES <-- QUEUE",
				"C4D61888FE5F3D71D0A2EBCF9894CAC8 0007 1.3 KiB",
				"REQUESTS --> STORE",
				"774BC6DDD7772EE419E8705E1A0BBA34 0006 939 B",
				"RESPONSES <-- STORE",
				"3DD66292776363BB0FC0E5F2443DDD7C 0006 1.1 KiB",
				"REQUESTS --> DB",
				"DFB253E31C3C812A60E039379CCFA64D 0008 2.5 KiB",
				"RESPONSES <-- DB",
				"B42E7EBB481EBD84DAC357E9178E09A4 0008 1.3 KiB",
				"REQUESTS --> HISTOGRAM",
				"BF8DDDB8D319B876FADD333BCB149E4F 0009 1.1 KiB",
				"RESPONSES <-- HISTOGRAM",
				"1744EA709AEE2A8718AE343B15C399BC 0009 1.6 KiB" );
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
						"|edges         415│edges         461|",
						"|total         777│total         581|",
						"|        3  25.00%│        3  21.43%|",
						"|        1   8.33%│        2  14.29%|",
						"|        0   0.00%│        2  14.29%|",
						"|        1   8.33%│        2  14.29%|",
						"|        3  25.00%│        2  14.29%|",
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
