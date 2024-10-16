# Build artifacts

One of the main features of the [flow testing framework](https://github.com/Mastercard/flow) is the production of rich execution reports.
Until such a time as [upload-artifact#14](https://github.com/actions/upload-artifact/issues/14) is addressed, we're maintaining a set of static reports for demo purposes here.

These reports are generated whenever [the `static_artifacts` workflow](https://github.com/Mastercard/flow/actions/workflows/static_artifacts.yml) is run: 

 * [Integrated system](static/app-itest/target/mctf/latest/index.html)
 * [Core service](static/app-core/target/mctf/latest/index.html)
 * [Histogram service](static/app-histogram/target/mctf/latest/index.html)
 * [Queue service](static/app-queue/target/mctf/latest/index.html)
 * [Store service](static/app-store/target/mctf/latest/index.html)
 * [UI service](static/app-ui/target/mctf/latest/index.html)
 * [Web UI service](static/app-web-ui/target/mctf/latest/index.html)

The same content (along with mutation testing and angular unit testing reports) is available as artifacts from the appropriate workflows.
