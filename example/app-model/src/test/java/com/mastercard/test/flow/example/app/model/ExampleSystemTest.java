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
				"784EFE9A55B4B970A5274DC5EE8D1D93 0128 22.3 KiB",
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
				"3C3FA1A6F3144575E21FCA47BF0F8EF3 0015 2.0 KiB",
				"RESPONSES <-- UI",
				"9C7456257F8441A7D5CD1385B67FB145 0015 2.8 KiB",
				"REQUESTS --> CORE",
				"08684251E6AEA75BD70E05CE9293894C 0016 2.5 KiB",
				"RESPONSES <-- CORE",
				"5A821401A40419A55E1BF938B30150EC 0016 3.9 KiB",
				"REQUESTS --> QUEUE",
				"CB06D74485A51EE84AA11C58B7BBE764 0007 975 B",
				"RESPONSES <-- QUEUE",
				"C4D61888FE5F3D71D0A2EBCF9894CAC8 0007 1.3 KiB",
				"REQUESTS --> STORE",
				"D845CE16593FB203652EBE26E523DA00 0006 939 B",
				"RESPONSES <-- STORE",
				"3DD66292776363BB0FC0E5F2443DDD7C 0006 1.1 KiB",
				"REQUESTS --> DB",
				"71DA3C2EA19EA00838FF1D3D2897DDBA 0008 2.5 KiB",
				"RESPONSES <-- DB",
				"CF399FF557548CC74F579F5193CE2264 0008 1.1 KiB",
				"REQUESTS --> HISTOGRAM",
				"735E7CB118574BAD7C1829513A3BE27F 0010 1.2 KiB",
				"RESPONSES <-- HISTOGRAM",
				"BAEF88E9F3765D7D563B1563C34255C6 0010 1.8 KiB" );
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
						"|edges         400│edges         446|",
						"|total         762│total         566|",
						"|        4  30.77%│        4  26.67%|",
						"|        1   7.69%│        2  13.33%|",
						"|        1   7.69%│        3  20.00%|",
						"|        2  15.38%│        3  20.00%|",
						"|        1   7.69%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        2  15.38%│        0   0.00%|",
						"|        1   7.69%│        1   6.67%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        1   6.67%|",
						"|        0   0.00%│        0   0.00%|",
						"|        1   7.69%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        1   6.67%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"|        0   0.00%│        0   0.00%|",
						"└─────────────────┴─────────────────┘" );
	}
}
