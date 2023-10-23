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

## GUI

Right-click the system tray icon to:
 * `Duct index` - View duct's index page. Served reports will be listed here
 * `Add report...` - Add a report to be served and open it in the browser
 * `Exit` - Shut duct down.

## HTTP

Duct's server runs at port `2276`, and offers the following endpoints:

 * `/add` - Send a `POST` request where the request body is the absolute path of an execution report. The report will be copied to duct's content directory and the response body will contain the URL to browse it
 * `/heartbeat` - Send a `GET` request to extend duct's lifespan by 90 seconds
 * `/shutdown` - Send a `GET` request to shut duct down.
