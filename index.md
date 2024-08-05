# Build artifacts

One of the main features of the [flow testing framework](https://github.com/Mastercard/flow) is the production of rich execution reports.
Until such a time as [upload-artifact#14](https://github.com/actions/upload-artifact/issues/14) is addressed, we're reduced to abusing github pages to show these artifacts to best effect.
See [test.yml](https://github.com/Mastercard/flow/blob/main/.github/workflows/test.yml), [mutation.yml](https://github.com/Mastercard/flow/blob/main/.github/workflows/mutation.yml) and [regen_index.pl](https://github.com/Mastercard/flow/blob/pages/regen_index.pl) for the gory details.

## Execution reports

These reports are the result of comparing a unified model of system behaviour against:
 * an instance of the complete system (The "app-itest" report)
 * system components in isolation (everything else)

<!-- start:execution -->
<table>
	<tbody>
		<tr> <th><code>latest</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-22.1.0</code></th>
			<td><a href="execution/latest/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/latest/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/latest/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/latest/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/latest/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/latest/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/latest/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:53:09</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-22.1.0</code></th>
			<td><a href="execution/1722819189/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722819189/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722819189/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722819189/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722819189/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722819189/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722819189/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:52:29</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.25.2</code></th>
			<td><a href="execution/1722819149/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722819149/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722819149/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722819149/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722819149/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722819149/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722819149/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:51:19</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/karma-6.4.4</code></th>
			<td><a href="execution/1722819079/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722819079/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722819079/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722819079/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722819079/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722819079/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722819079/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:09:47</code></th>
			 <th><code>dependabot/github_actions/actions/upload-artifact-4.3.5</code></th>
			<td><a href="execution/1722816587/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722816587/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722816587/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722816587/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722816587/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722816587/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722816587/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:13:33</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1722237213/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722237213/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722237213/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722237213/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722237213/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722237213/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722237213/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:12:18</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1722237138/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722237138/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722237138/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722237138/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722237138/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722237138/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722237138/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:11:57</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1722237117/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722237117/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722237117/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722237117/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722237117/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722237117/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722237117/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:10:55</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1722237055/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722237055/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722237055/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722237055/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722237055/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722237055/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722237055/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:51:36</code></th>
			 <th><code>dependabot/maven/io.github.bonigarcia-webdrivermanager-5.9.2</code></th>
			<td><a href="execution/1722214296/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722214296/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722214296/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722214296/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722214296/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722214296/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722214296/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:36:13</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-22.0.0</code></th>
			<td><a href="execution/1722213373/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722213373/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722213373/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722213373/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722213373/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722213373/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722213373/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:15:52</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.15</code></th>
			<td><a href="execution/1722212152/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722212152/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722212152/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722212152/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722212152/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722212152/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722212152/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:15:30</code></th>
			 <th><code>dependabot/github_actions/ossf/scorecard-action-2.4.0</code></th>
			<td><a href="execution/1722212130/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1722212130/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1722212130/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1722212130/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1722212130/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1722212130/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1722212130/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T15:02:47</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721660567/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721660567/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721660567/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721660567/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721660567/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721660567/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721660567/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:42:05</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="execution/1721659325/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721659325/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721659325/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721659325/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721659325/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721659325/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721659325/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:41:43</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721659303/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721659303/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721659303/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721659303/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721659303/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721659303/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721659303/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:37:17</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="execution/1721659037/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721659037/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721659037/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721659037/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721659037/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721659037/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721659037/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:36:43</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721659003/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721659003/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721659003/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721659003/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721659003/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721659003/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721659003/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:36:17</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721658977/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721658977/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721658977/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721658977/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721658977/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721658977/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721658977/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:35:55</code></th>
			 <th><code>main</code></th>
			<td><a href="execution/1721658955/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721658955/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721658955/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721658955/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721658955/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721658955/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721658955/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:01:05</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.9</code></th>
			<td><a href="execution/1721610065/app-core/target/mctf/latest/index.html">app-core</a></td>
			<td><a href="execution/1721610065/app-histogram/target/mctf/latest/index.html">app-histogram</a></td>
			<td><a href="execution/1721610065/app-itest/target/mctf/latest/index.html">app-itest</a></td>
			<td><a href="execution/1721610065/app-queue/target/mctf/latest/index.html">app-queue</a></td>
			<td><a href="execution/1721610065/app-store/target/mctf/latest/index.html">app-store</a></td>
			<td><a href="execution/1721610065/app-ui/target/mctf/latest/index.html">app-ui</a></td>
			<td><a href="execution/1721610065/app-web-ui/target/mctf/latest/index.html">app-web-ui</a></td>
		</tr>
	</tbody>
</table>
<!-- end:execution -->

## Mutation testing

Test quality metrics for framework packages.

<!-- start:mutation -->
<table>
	<tbody>
		<tr> <th><code>latest</code></th>
			 <th><code>dependabot/github_actions/actions/upload-artifact-4.3.5</code></th>
			<td><a href="mutation/latest/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:16:00</code></th>
			 <th><code>dependabot/github_actions/actions/upload-artifact-4.3.5</code></th>
			<td><a href="mutation/1722816960/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:19:26</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1722237566/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:19:08</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1722237548/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:18:49</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1722237529/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:17:27</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1722237447/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:57:26</code></th>
			 <th><code>dependabot/maven/io.github.bonigarcia-webdrivermanager-5.9.2</code></th>
			<td><a href="mutation/1722214646/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:42:00</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-22.0.0</code></th>
			<td><a href="mutation/1722213720/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:21:44</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.15</code></th>
			<td><a href="mutation/1722212504/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:21:11</code></th>
			 <th><code>dependabot/github_actions/ossf/scorecard-action-2.4.0</code></th>
			<td><a href="mutation/1722212471/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T15:08:57</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721660937/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:48:48</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="mutation/1721659728/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:46:08</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721659568/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:44:02</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="mutation/1721659442/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:43:19</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721659399/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:41:18</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721659278/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:40:58</code></th>
			 <th><code>main</code></th>
			<td><a href="mutation/1721659258/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:08:55</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.9</code></th>
			<td><a href="mutation/1721610535/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:07:20</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/zone.js-0.14.8</code></th>
			<td><a href="mutation/1721610440/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:06:12</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-20.14.11</code></th>
			<td><a href="mutation/1721610372/mutation_report/index.html">mutation</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:04:45</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="mutation/1721610285/mutation_report/index.html">mutation</a></td>
		</tr>
	</tbody>
</table>
<!-- end:mutation -->

## Angular coverage

Test coverage for the report application.

<!-- start:ng_coverage -->
<table>
	<tbody>
		<tr> <th><code>latest</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-22.1.0</code></th>
			<td><a href="ng_coverage/latest/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:53:09</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-22.1.0</code></th>
			<td><a href="ng_coverage/1722819189/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:52:29</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.25.2</code></th>
			<td><a href="ng_coverage/1722819149/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:51:19</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/karma-6.4.4</code></th>
			<td><a href="ng_coverage/1722819079/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-08-05T00:09:47</code></th>
			 <th><code>dependabot/github_actions/actions/upload-artifact-4.3.5</code></th>
			<td><a href="ng_coverage/1722816587/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:13:33</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1722237213/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:12:18</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1722237138/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:11:57</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1722237117/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T07:10:55</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1722237055/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:51:36</code></th>
			 <th><code>dependabot/maven/io.github.bonigarcia-webdrivermanager-5.9.2</code></th>
			<td><a href="ng_coverage/1722214296/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:36:13</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/types/node-22.0.0</code></th>
			<td><a href="ng_coverage/1722213373/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:15:52</code></th>
			 <th><code>dependabot/github_actions/github/codeql-action-3.25.15</code></th>
			<td><a href="ng_coverage/1722212152/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-29T00:15:30</code></th>
			 <th><code>dependabot/github_actions/ossf/scorecard-action-2.4.0</code></th>
			<td><a href="ng_coverage/1722212130/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T15:02:47</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721660567/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:42:05</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="ng_coverage/1721659325/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:41:43</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721659303/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:37:17</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/jasmine-core-5.2.0</code></th>
			<td><a href="ng_coverage/1721659037/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:36:43</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721659003/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:36:17</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721658977/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T14:35:55</code></th>
			 <th><code>main</code></th>
			<td><a href="ng_coverage/1721658955/report/index.html">ng_coverage</a></td>
		</tr>
		<tr> <th><code>2024-07-22T01:01:05</code></th>
			 <th><code>dependabot/npm_and_yarn/report/report-ng/babel/core-7.24.9</code></th>
			<td><a href="ng_coverage/1721610065/report/index.html">ng_coverage</a></td>
		</tr>
	</tbody>
</table>
<!-- end:ng_coverage -->