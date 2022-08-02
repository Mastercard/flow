package com.mastercard.test.flow.aggregator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.Order;
import com.mastercard.test.flow.builder.Chain;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.validation.check.ChainOverlapCheck;
import com.mastercard.test.flow.validation.check.ResultTagCheck;

/**
 * There are a few constant values that span submodules, but that also don't
 * really belong in the api module. Rather than introduce yet <i>another</i>
 * module to define these constants in one place we're just going to define them
 * where they are used and then test that all of those definitions agree with
 * each other here.
 */
@SuppressWarnings("static-method")
class SharedConstantTest {

	/**
	 * We build chain tags with {@link Chain}, but then those tags are consumed by
	 * {@link Order} and validated by {@link ChainOverlapCheck}. These classes exist
	 * in different submodules, but must obviously use the same tag prefix
	 */
	@Test
	void chainTagPrefix() {
		Assertions.assertEquals( Chain.PREFIX, Order.CHAIN_TAG_PREFIX );
		Assertions.assertEquals( Chain.PREFIX, ChainOverlapCheck.TAG_PREFIX );
	}

	/**
	 * The result tags are added by the report {@link Writer}, but we also want the
	 * {@link ResultTagCheck} to ensure that those tag values are not used as
	 * generic tags
	 */
	@Test
	void resultTags() {
		Assertions.assertEquals( Writer.ERROR_TAG, ResultTagCheck.ERROR_TAG );
		Assertions.assertEquals( Writer.FAIL_TAG, ResultTagCheck.FAIL_TAG );
		Assertions.assertEquals( Writer.SKIP_TAG, ResultTagCheck.SKIP_TAG );
		Assertions.assertEquals( Writer.ERROR_TAG, ResultTagCheck.ERROR_TAG );
	}
}
