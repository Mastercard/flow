#!/usr/bin/env bash
# Run after the focused reactor install documented in README.md.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
: "${JAVA_HOME:?Set JAVA_HOME to Java 17}"
mkdir -p target/evidence

for junit in 5.10.0 6.0.3; do
  platform=1.10.0
  if [[ "$junit" == 6.0.3 ]]; then platform=6.0.3; fi
  for parallel in false true; do
    point="$junit-$parallel"
    log="target/evidence/$point.log"
    report="target/surefire-reports/TEST-consumer.PackagedConsumerTest.xml"
    rm -f "$report"
    mvn -nsu -B -f pom.xml -Djunit.version="$junit" -Dplatform.version="$platform" \
      -Dconsumer.parallel="$parallel" -DskipTests=false -Dmaven.test.skip=false \
      -Dmaven.test.failure.ignore=false -Dtest=PackagedConsumerTest \
      org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree -Dverbose \
      org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom \
      test > "$log" 2>&1 || { cat "$log"; exit 1; }
    if [[ ! -s "$report" ]] || [[ $(grep -c '^[[:space:]]*<testsuite ' "$report") != 1 ]]; then
      echo "Missing fresh single-suite report: $report"; exit 1
    fi
    header=$(grep '^[[:space:]]*<testsuite ' "$report")
    for count in tests=2 failures=0 errors=0 skipped=0; do
      if ! grep -Eq "[[:space:]]${count%=*}=\"${count#*=}\"([[:space:]]|>)" <<< "$header"; then
        echo "Expected $count in fresh Surefire report: $header"; exit 1
      fi
    done
    cp "$report" "target/evidence/$point.xml"
    trace="target/classload-$point.log"
    grep '^FLOW-FILE ' "$log" | sort > "target/evidence/$point.sha256"
    cmp target/evidence/5.10.0-false.sha256 "target/evidence/$point.sha256"
    if [[ "$parallel" == false ]]; then
      if grep -E 'com\.mastercard\.test\.flow\.assrt\.junit5\.(FlowLauncherInterceptor|FlowNativeCall|FlowParallelOwner|FlowLauncherSix)(\$[^ ]*)? source:' "$trace"; then
        echo "Unexpected parallel provider/owner loading in serial run"; exit 1
      fi
    else
      grep -q 'com.mastercard.test.flow.assrt.junit5.FlowLauncherInterceptor source:.*\.jar' "$trace"
      grep -q 'com.mastercard.test.flow.assrt.junit5.FlowNativeCall source:.*\.jar' "$trace"
    fi
    if [[ "$junit" == 5.10.0 ]]; then
      if grep 'com.mastercard.test.flow.assrt.junit5.FlowLauncherSix source:' "$trace"; then
        echo "JUnit 6 helper loaded on baseline"; exit 1
      fi
    elif [[ "$parallel" == true ]]; then
      grep -q 'com.mastercard.test.flow.assrt.junit5.FlowLauncherSix source:.*\.jar' "$trace"
    fi
    echo "PASS JUnit=$junit Platform=$platform parallel=$parallel (fresh XML: 2 tests, 0 failures/errors/skips; native UIDs/counts, artifacts, class loading)"
  done
done
