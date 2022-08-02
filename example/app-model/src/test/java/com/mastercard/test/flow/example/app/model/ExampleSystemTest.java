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
import com.mastercard.test.flow.example.app.model.ExampleSystem.Actors;
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
	void hashes() {
		MessageHash mh = new MessageHash( Assertions::assertEquals )
				.hashingEverything();

		for( Actor actor : Actors.values() ) {
			mh.hashing( actor, REQUESTS )
					.hashing( actor, RESPONSES );
		}

		mh.expect( ExampleSystem.MODEL,
				"ALL MESSAGES",
				"6E27928FF7F3CA1660428481D0E9992F 0122 20.1 KiB",
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
				"CF6B155D7B0A245BB4F70287D505A1FF 0014 2.6 KiB",
				"REQUESTS --> CORE",
				"681B2BB38FB90027F44C8467727F8D58 0015 2.3 KiB",
				"RESPONSES <-- CORE",
				"6B63812707D1065A373A94671277F7ED 0015 3.7 KiB",
				"REQUESTS --> QUEUE",
				"3A79459ABD8CA52EA136B5DC00F15787 0007 975 B",
				"RESPONSES <-- QUEUE",
				"2A284FCC8537E55891293DC464AEE98D 0007 1.3 KiB",
				"REQUESTS --> STORE",
				"A01276774007C68063E237FE73FC1C95 0006 939 B",
				"RESPONSES <-- STORE",
				"7F3F5A755E6D0BB79D7FC2889E3070DA 0006 1.1 KiB",
				"REQUESTS --> DB",
				"BE62FAF29D6D754D7657AB3DC35CA42E 0008 1.6 KiB",
				"RESPONSES <-- DB",
				"68EE6A87C77ADA45EBB0CD073CD0CEC3 0008 943 B",
				"REQUESTS --> HISTOGRAM",
				"51B9735B70AE590E97230E245263CC7B 0009 1.1 KiB",
				"RESPONSES <-- HISTOGRAM",
				"CFBF0D8C4CE6D198429014452B3E6A68 0009 1.6 KiB" );
	}
}
