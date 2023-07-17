# Example application test coverage

This project contains a set of submodules that implement a simple application, used as a motivating example for the test framework itself.

The test coverage breaks down like so:

## System model

The [`app-model` module](../example/app-model) contains the flow definitions that describe how we believe data moves through the system. For example, the "hello" flow contains the following interactions:

![Test data](model.drawio.svg)

## Integration tests

The application is tested with all component services working together in the [`app-itest` module](../example/app-itest).
The `IntegrationTest` class will start an instance of each of the component services, exercise it with requests from the model, then assert on the response from the system - checking it against the expected response documented in the model. For the hello flow, that look like this:

![Integration test](integration.drawio.svg)

[Integration-test execution report](https://mastercard.github.io/flow/execution/latest/example/app-itest/target/mctf/latest/detail/482730F5C81EA0249B98F6DA9D9780EA.html)

Integration testing provides excellent confidence that the system is working as expected, but when an unexpected result is observed it can be difficult to track down the source of the problem. The system under test is large and we have limited visibility into its internal behaviour.

## Unit tests

Each of the component services has a test that exercises it in isolation. Here the test will launch two services:
 * The service under test
 * A mock service that takes the place of the rest of the system

The service under test is provoked with requests taken from the system model. The mock service will capture any requests that the service sends to it, and responds with data from the model.

The "hello" flow is thus exercised in three unit test suites:

![UI test](ui.drawio.svg)

[UI test execution report](https://mastercard.github.io/flow/execution/latest/example/app-ui/target/mctf/latest/detail/482730F5C81EA0249B98F6DA9D9780EA.html)

![Core test](core.drawio.svg)

[Core test execution report](https://mastercard.github.io/flow/execution/latest/example/app-core/target/mctf/latest/detail/482730F5C81EA0249B98F6DA9D9780EA.html)

![Histogram test](histogram.drawio.svg)

[Histogram test execution report](https://mastercard.github.io/flow/execution/latest/example/app-histogram/target/mctf/latest/detail/482730F5C81EA0249B98F6DA9D9780EA.html)
