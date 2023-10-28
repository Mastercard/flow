<!-- title start -->

# duct

Report server

[![javadoc](https://javadoc.io/badge2/com.mastercard.test.flow/duct/javadoc.svg)](https://javadoc.io/doc/com.mastercard.test.flow/duct)

 * [../report](..) Visualising assertion results

<!-- title end -->

Viewing flow execution reports by browsing the filesystem is convenient, but you miss out on the functionality that requires AJAX calls, e.g.:
 * The model diff tool
 * The basis diff view on the detail pages
 * The system diagram on the index

Duct is a standalone executable that sits in the system tray and runs an HTTP server to which execution reports can be added.
It will keep running as along as someone is viewing the index or a served report, and then shut down after 90 seconds of non-use.

## Usage

After [importing the `bom`](../../bom):

```xml
<dependency>
  <!-- local report server -->
  <groupId>com.mastercard.test.flow</groupId>
  <artifactId>duct</artifactId>
</dependency>
```

When running your test, set system property `mctf.report.serve=true` to have reports (if any are produced) viewed via a duct instance rather than the filesystem.

## GUI

Right-click the system tray icon to:
 * `Index` - View duct's index page. Served reports will be listed here
 * `Add...` - Choose a directory in which to search (recursively). All reports found will be added to the index and opened in the browser
 * `Clear` - Removes all served reports
 * `Logs` - Opens the directory where duct's log file is written
 * `Exit` - Shut duct down.

## HTTP

Duct's server runs at port `2276`, and offers the following endpoints:

 * `/heartbeat` - Send a `GET` request to extend duct's lifespan by 90 seconds
 * `/add` - Send a `POST` request where the request body is the absolute path of an execution report. The report will be added to the index and the response body will contain the path under `http://127.0.0.1:2276` to browse it
 * `/list` - Send a `GET` request to retrieve a JSON summary of served reports
 * `/shutdown` - Send a `GET` request to shut duct down.

Duct is only accessible on loopback addresses, i.e.: `http://127.0.0.1:2276`, `http://localhost:2276` and `http://[::1]:2276`.
