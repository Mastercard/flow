package com.mastercard.test.flow.aggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.assrt.AssertionOptions;
import com.mastercard.test.flow.assrt.Order;
import com.mastercard.test.flow.builder.Chain;
import com.mastercard.test.flow.model.LazyModel;
import com.mastercard.test.flow.report.Writer;
import com.mastercard.test.flow.report.duct.Duct;
import com.mastercard.test.flow.util.Option;
import com.mastercard.test.flow.validation.check.ChainOverlapCheck;
import com.mastercard.test.flow.validation.check.ReflectiveModelTaggingCheck;
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

	/**
	 * We've got a validation check that exercises aspects of a particular model
	 * implementation.
	 */
	@Test
	void lazyModelStrings() {
		Assertions.assertEquals( LazyModel.MODEL_TAGS_FIELD_NAME,
				ReflectiveModelTaggingCheck.MODEL_TAGS_FIELD_NAME );
	}

	/**
	 * The <code>duct</code> module offers {@link Duct#GUI_SUPPRESS}, and we've got
	 * an alias for that in {@link AssertionOptions#DUCT_GUI_SUPPRESS} so that it
	 * gets picked up by the documentation automation. However, <code>duct</code> is
	 * an <i>optional</i> dependency of <code>assert-core</code>, so we can depend
	 * on it being available when {@link AssertionOptions} is initialised. Hence we
	 * have to maintain a static copy of the {@link Option} values.
	 */
	@Test
	void ductSuppressionOption() {
		assertEquals( Duct.GUI_SUPPRESS.property(), AssertionOptions.DUCT_GUI_SUPPRESS.property(),
				"property name" );
		assertEquals( Duct.GUI_SUPPRESS.description(), AssertionOptions.DUCT_GUI_SUPPRESS.description(),
				"property description" );
	}
}
