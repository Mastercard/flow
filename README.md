# flow pages

This branch holds our pages content.
That used to be a rotating set of build artifacts maintained by [this script](regen_index.pl), but since [#940](https://github.com/Mastercard/flow/pull/940) all we need is:
 * The report content from [#568](https://github.com/Mastercard/flow/pull/568), used as a demo for the report-diffing feature
 * A static set of execution reports. These are updated by [the `static_artifacts` workflow](https://github.com/Mastercard/flow/actions/workflows/static_artifacts.yml) whenever something significant changes in the report. We link to these in our documentation as we don't _entirely_ trust the bowlby instance to always be available.

