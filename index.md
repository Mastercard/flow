# Build artifacts

One of the main features of the [flow testing framework](https://github.com/Mastercard/flow) is the production of rich execution reports.
Until such a time as [upload-artifact#14](https://github.com/actions/upload-artifact/issues/14) is addressed, we're using the [bowlby](https://github.com/therealryan/bowlby) instance at [https://bowlby.flowty.dev/flow/](https://bowlby.flowty.dev/flow/) to serve our workflow results.

See the latest results here:
 * [Testing](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml)
   * [Integrated system](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/flow_execution_reports/app-itest/target/mctf/latest/index.html)
   * [Core service](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/flow_execution_reports/app-core/target/mctf/latest/index.html)
   * [Histogram service](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/flow_execution_reports/app-histogram/target/mctf/latest/index.html)
   * [Queue service](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/flow_execution_reports/app-queue/target/mctf/latest/index.html)
   * [Store service](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/flow_execution_reports/app-store/target/mctf/latest/index.html)
   * [UI service](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/flow_execution_reports/app-ui/target/mctf/latest/index.html)
   * [Web UI service](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/flow_execution_reports/app-web-ui/target/mctf/latest/index.html)
   * [Report UI](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/test.yml/angular_coverage/report/index.html)
 * [Mutation](https://bowlby.flowty.dev/flow/latest/Mastercard/flow/mutation.yml/mutation_report/index.html)

Look at the workflow summaries for the results of historic runs.

