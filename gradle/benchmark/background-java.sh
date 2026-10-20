#!/bin/sh
# Dev-only: runs the benchmark client's JVM at macOS background priority (taskpolicy -b,
# PRIO_DARWIN_BG: efficiency cores, throttled disk I/O). The Gradle run task passes the toolchain's
# java in RELATIVITY_BENCHMARK_JAVA; without it (Gradle probing the executable) the system Java 25.
java="${RELATIVITY_BENCHMARK_JAVA:-$(/usr/libexec/java_home -v 25)/bin/java}"
exec /usr/sbin/taskpolicy -b "$java" "$@"
