package consumer;

import java.net.URI;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Diagnostic control: genuine Jupiter dynamic tests without any Flow extension.
 */
class SurefireSourceProbe {
	@TestFactory
	Stream<DynamicTest> dynamic() {
		return Stream.of( DynamicTest.dynamicTest( "native source control",
				Boolean.getBoolean( "consumer.class.source" )
						? URI.create( "class:consumer.BindingModel?line=1" )
						: null,
				() -> {
				} ) );
	}
}
