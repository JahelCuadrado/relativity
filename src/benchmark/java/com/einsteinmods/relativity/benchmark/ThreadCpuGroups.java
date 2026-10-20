package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Dev-only CPU time of every live JVM thread, grouped by name without its number suffix ("Worker-
 * Main-3" and "Worker-Main-7" are one group), read between rounds from the script thread. Gives the
 * denominator for off-thread work such as chunk generation, whatever pool runs it.
 */
final class ThreadCpuGroups {
  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
  private static final Pattern NUMBER_SUFFIX = Pattern.compile("[\\s#-]*\\d+$");
  private static final double NANOS_PER_MILLI = 1_000_000.0;
  private static final long UNKNOWN_CPU = -1L;

  private ThreadCpuGroups() {}

  /** CPU nanoseconds per thread id, with each id's group name. */
  static Map<Long, ThreadCpu> snapshot() {
    Map<Long, ThreadCpu> threads = new HashMap<>();
    if (!THREADS.isThreadCpuTimeSupported()) {
      return threads;
    }
    for (ThreadInfo info : THREADS.getThreadInfo(THREADS.getAllThreadIds())) {
      if (info == null) {
        continue;
      }
      long cpu = THREADS.getThreadCpuTime(info.getThreadId());
      if (cpu != UNKNOWN_CPU) {
        threads.put(info.getThreadId(), new ThreadCpu(group(info.getThreadName()), cpu));
      }
    }
    return threads;
  }

  /**
   * CPU milliseconds per wall second of each group between two snapshots, busiest first. Threads
   * born during the interval count from zero; threads that died are lost (worker pools persist).
   */
  static JsonObject since(
      Map<Long, ThreadCpu> earlier, Map<Long, ThreadCpu> later, double seconds) {
    Map<String, Long> byGroup = new HashMap<>();
    for (Map.Entry<Long, ThreadCpu> thread : later.entrySet()) {
      ThreadCpu before = earlier.get(thread.getKey());
      long delta = thread.getValue().cpuNanos() - (before == null ? 0 : before.cpuNanos());
      byGroup.merge(thread.getValue().group(), delta, Long::sum);
    }
    JsonObject json = new JsonObject();
    byGroup.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
        .forEach(
            entry ->
                json.addProperty(
                    entry.getKey(),
                    seconds <= 0 ? 0 : entry.getValue() / NANOS_PER_MILLI / seconds));
    return json;
  }

  private static String group(String name) {
    String group = name;
    String previous;
    do {
      previous = group;
      group = NUMBER_SUFFIX.matcher(group).replaceAll("");
    } while (!group.equals(previous));
    return group.isEmpty() ? name : group;
  }

  /** One thread's group and CPU time so far. */
  record ThreadCpu(String group, long cpuNanos) {}
}
