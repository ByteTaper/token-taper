;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.observability.runtime-metrics
  (:import
   [java.lang.management ManagementFactory MemoryMXBean ThreadMXBean]))

(defn- memory-mx-bean ^MemoryMXBean []
  (ManagementFactory/getMemoryMXBean))

(defn- thread-mx-bean ^ThreadMXBean []
  (ManagementFactory/getThreadMXBean))

(defn jvm-memory-used-bytes
  []
  (let [^MemoryMXBean bean (memory-mx-bean)
        heap (.getHeapMemoryUsage bean)
        non-heap (.getNonHeapMemoryUsage bean)]
    {:heap (.getUsed heap)
     :non-heap (.getUsed non-heap)
     :total (+ (.getUsed heap) (.getUsed non-heap))}))

(defn jvm-memory-committed-bytes
  []
  (let [^MemoryMXBean bean (memory-mx-bean)
        heap (.getHeapMemoryUsage bean)
        non-heap (.getNonHeapMemoryUsage bean)]
    {:heap (.getCommitted heap)
     :non-heap (.getCommitted non-heap)
     :total (+ (.getCommitted heap) (.getCommitted non-heap))}))

(defn jvm-threads-live
  []
  (.getThreadCount (thread-mx-bean)))
