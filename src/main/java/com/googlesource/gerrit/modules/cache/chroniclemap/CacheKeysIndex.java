// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.modules.cache.chroniclemap;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CompatibleWith;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

class CacheKeysIndex<T> {
  /** As opposed to TimedValue keys are equal when their key equals. */
  class TimedKey {
    private final T key;
    private final long created;

    private TimedKey(T key, long created) {
      this.key = key;
      this.created = created;
    }

    T getKey() {
      return key;
    }

    long getCreated() {
      return created;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof CacheKeysIndex.TimedKey) {
        @SuppressWarnings("unchecked")
        TimedKey other = (TimedKey) o;
        return Objects.equals(key, other.key);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(key);
    }
  }

  private class Metrics {
    private final Timer0 addLatency;
    private final Timer0 removeAndConsumeOlderThanLatency;
    private final Timer0 removeAndConsumeLruKeyLatency;

    private Metrics(MetricMaker metricMaker, String name) {
      String sanitizedName = metricMaker.sanitizeMetricName(name);

      metricMaker.newCallbackMetric(
          "cache/chroniclemap/keys_index_size_" + sanitizedName,
          Integer.class,
          new Description(
              String.format(
                  "The number of cache index keys for %s cache that are currently in memory",
                  name)),
          keys::size);

      addLatency =
          metricMaker.newTimer(
              "cache/chroniclemap/keys_index_add_latency_" + sanitizedName,
              new Description(
                      String.format("The latency of adding key to the index for %s cache", name))
                  .setCumulative()
                  .setUnit(Units.NANOSECONDS));

      removeAndConsumeOlderThanLatency =
          metricMaker.newTimer(
              "cache/chroniclemap/keys_index_remove_and_consume_older_than_latency_"
                  + sanitizedName,
              new Description(
                      String.format(
                          "The latency of removing and consuming all keys older than expiration"
                              + " time for the index for %s cache",
                          name))
                  .setCumulative()
                  .setUnit(Units.NANOSECONDS));

      removeAndConsumeLruKeyLatency =
          metricMaker.newTimer(
              "cache/chroniclemap/keys_index_remove_lru_key_latency_" + sanitizedName,
              new Description(
                      String.format(
                          "The latency of removing and consuming LRU key from the index for %s"
                              + " cache",
                          name))
                  .setCumulative()
                  .setUnit(Units.NANOSECONDS));
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Set<TimedKey> keys;
  private final Metrics metrics;
  private final String name;
  private final File indexFile;
  private final File tempIndexFile;

  CacheKeysIndex(MetricMaker metricMaker, String name, File indexFile) {
    this.keys = Collections.synchronizedSet(new LinkedHashSet<>());
    this.metrics = new Metrics(metricMaker, name);
    this.name = name;
    this.indexFile = indexFile;
    this.tempIndexFile = new File(String.format("%s.tmp", indexFile.getPath()));
    restore();
  }

  @SuppressWarnings("unchecked")
  void add(@CompatibleWith("T") Object key, long created) {
    Objects.requireNonNull(key, "Key value cannot be [null].");
    TimedKey timedKey = new TimedKey((T) key, created);

    // bubble up MRU key by re-adding it to a set
    try (Timer0.Context timer = metrics.addLatency.start()) {
      keys.remove(timedKey);
      keys.add(timedKey);
    }
  }

  void refresh(T key) {
    add(key, System.currentTimeMillis());
  }

  void removeAndConsumeKeysOlderThan(long time, Consumer<T> consumer) {
    try (Timer0.Context timer = metrics.removeAndConsumeOlderThanLatency.start()) {
      Set<TimedKey> toRemoveAndConsume;
      synchronized (keys) {
        toRemoveAndConsume =
            keys.stream().filter(key -> key.created < time).collect(toUnmodifiableSet());
      }
      toRemoveAndConsume.forEach(
          key -> {
            keys.remove(key);
            consumer.accept(key.getKey());
          });
    }
  }

  boolean removeAndConsumeLruKey(Consumer<T> consumer) {
    try (Timer0.Context timer = metrics.removeAndConsumeLruKeyLatency.start()) {
      Optional<TimedKey> lruKey;
      synchronized (keys) {
        lruKey = keys.stream().findFirst();
      }

      return lruKey
          .map(
              key -> {
                keys.remove(key);
                consumer.accept(key.getKey());
                return true;
              })
          .orElse(false);
    }
  }

  void invalidate(@CompatibleWith("T") Object key) {
    keys.remove(key);
  }

  void clear() {
    keys.clear();
  }

  @VisibleForTesting
  Set<TimedKey> keys() {
    return keys;
  }

  void persist() {
    logger.atInfo().log("Persisting cache keys index %s to %s file", name, indexFile);
    Set<TimedKey> toPersist;
    synchronized (keys) {
      toPersist = new LinkedHashSet<>(keys.size(), 1.0F);
      toPersist.addAll(keys);
    }
    CacheSerializer<T> serializer = CacheSerializers.getKeySerializer(name);
    try (DataOutputStream dos =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempIndexFile)))) {
      for (TimedKey key : toPersist) {
        writeKey(serializer, dos, key);
      }
      dos.flush();
      indexFile.delete();
      if (!tempIndexFile.renameTo(indexFile)) {
        logger.atWarning().log(
            "Renaming temporary index file %s to %s was not successful", tempIndexFile, indexFile);
      }
      logger.atInfo().log("Cache keys index %s was persisted to %s file", name, indexFile);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Persisting cache keys index %s failed", name);
    }
  }

  void restore() {
    if (tempIndexFile.isFile()) {
      logger.atWarning().log(
          "Gerrit was not closed properly as index persist operation was not finished: temporary"
              + " index storage file %s exists. Consider extending the shutdown timeout.");
      if (!tempIndexFile.delete()) {
        logger.atSevere().log("Cannot delete the temporary index storage file %s.", tempIndexFile);
      }
    }

    if (!indexFile.isFile() || !indexFile.canRead()) {
      logger.atWarning().log(
          "Restoring cache keys index %s not possible. File %s doesn't exist or cannot be read.",
          name, indexFile.getPath());
      return;
    }

    logger.atInfo().log("Restoring cache keys index %s from %s file", name, indexFile);
    CacheSerializer<T> serializer = CacheSerializers.getKeySerializer(name);
    try (DataInputStream dis =
        new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile)))) {
      while (dis.available() > 0) {
        keys.add(readKey(serializer, dis));
      }
      logger.atInfo().log("Cache keys index %s was restored from %s file", name, indexFile);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Restoring cache keys index %s failed", name);
    }
  }

  private void writeKey(CacheSerializer<T> serializer, DataOutput out, TimedKey key)
      throws IOException {
    byte[] serializeKey = serializer.serialize(key.getKey());
    out.writeLong(key.getCreated());
    out.writeInt(serializeKey.length);
    out.write(serializeKey);
  }

  private TimedKey readKey(CacheSerializer<T> serializer, DataInput in) throws IOException {
    long created = in.readLong();
    int keySize = in.readInt();
    byte[] serializedKey = new byte[keySize];
    in.readFully(serializedKey);
    T key = serializer.deserialize(serializedKey);
    return new TimedKey(key, created);
  }
}
