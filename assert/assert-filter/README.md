
<!-- title start -->

# assert-filter

Flow selection components

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/assert-filter/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/assert-filter)

 * [../assert](..) Comparing models against systems

<!-- title end -->

## Overview

The package provides a mechanism by which flows can be chosen at runtime based on their tag values. This mechanism attempts to minimise the number of unwanted flows that are constructed.

## Usage

It is unlikely that you'll need to depend directly on this module, it will be supplied transitively by `assert-core`.

## System properties

Some aspects of filtering behaviour can be controlled by system properties:

<!-- start_property_table -->

| property | description |
| -------- | ----------- |
| `mctf.filter.exclude` | A comma-separated list of tags values that flows must not have |
| `mctf.filter.include` | A comma-separated list of tags values that flows must have |
| `mctf.filter.indices` | A comma-separated list of indices and index ranges for flows to process |
| `mctf.filter.update` | Supply `true` to update filter values at runtime in the most appropriate interface.Supply `cli` to force use of the command-line interface or `gui` to force use of the graphical interface |
| `mctf.filter.repeat` | Supply `true` to use the previous filters again |
| `mctf.filter.fails` | Configures filters to repeat flows that did not pass assertion in a previous run. Supply the location of a report from which to extract results, or `latest` to extract from the most recent local report |
| `mctf.dir` | The path to the dir where assertion artifacts are saved |
| `mctf.filter.cli.min_width` | The minimum width of the command-line interface |

<!-- end_property_table -->

## GUI

Calling [`blockForUpdates()`][Filter!.blockForUpdates()] on a `Filter` instance while system property `mctf.filter.update` is `gui` (or `true` in a GUI environment) will cause a graphical interface to be displayed to the user to allow them to update the include and exclude tag sets, and then to choose which of the flows that pass those filters should be exercised.

<!-- code_link_start -->

[Filter!.blockForUpdates()]: src/main/java/com/mastercard/test/flow/assrt/filter/Filter.java#L158-L171,158-171

<!-- code_link_end -->

## CLI

Calling [`blockForUpdates()`][Filter!.blockForUpdates()] on a `Filter` instance while system property `mctf.filter.update` is `cli` (or `true` in a headless environment) will cause a command-line interface to be displayed to the user to allow them to update the include and exclude tag sets, and then to choose which of the flows that pass those filters should be exercised.

## Persistence

Calling [`save()`][Filter!.save()] on a filter will cause the filter configuration to be saved to disk.
Calling [`load()`][Filter!.load()] on a filter (when system property `mctf.filter.repeat` is `true`) will cause configuration to be loaded from disk.

<!-- code_link_start -->

[Filter!.save()]: src/main/java/com/mastercard/test/flow/assrt/filter/Filter.java#L182-L188,182-188
[Filter!.load()]: src/main/java/com/mastercard/test/flow/assrt/filter/Filter.java#L128-L142,128-142

<!-- code_link_end -->
