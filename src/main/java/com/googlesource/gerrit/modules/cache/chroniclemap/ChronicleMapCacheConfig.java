// Copyright (C) 2020 The Android Open Source Project
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

import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheFactory.PRUNE_DELAY;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.lib.Config;

public class ChronicleMapCacheConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final File cacheFile;
  private final boolean cacheFileExists;
  private final File indexFile;
  private final long maxEntries;
  private final long averageKeySize;
  private final long averageValueSize;
  private final Duration expireAfterWrite;
  private final Duration refreshAfterWrite;
  private final int maxBloatFactor;
  private final int percentageFreeSpaceEvictionThreshold;
  private final String configKey;
  private final Duration persistIndexEvery;
  private final long persistIndexEveryNthPrune;

  public interface Factory {
    ChronicleMapCacheConfig create(
        @Assisted("ConfigKey") String configKey,
        @Assisted File cacheFile,
        @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
        @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite);

    ChronicleMapCacheConfig createWithValues(
        @Assisted("ConfigKey") String configKey,
        @Assisted File cacheFile,
        @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
        @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite,
        @Assisted("maxEntries") long maxEntries,
        @Assisted("avgKeySize") long avgKeySize,
        @Assisted("avgValueSize") long avgValueSize,
        @Assisted("maxBloatFactor") int maxBloatFactor,
        @Assisted("PersistIndexEvery") Duration persistIndexEver);
  }

  @AssistedInject
  ChronicleMapCacheConfig(
      @GerritServerConfig Config cfg,
      @Assisted("ConfigKey") String configKey,
      @Assisted File cacheFile,
      @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
      @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite) {

    this(
        cfg,
        configKey,
        cacheFile,
        expireAfterWrite,
        refreshAfterWrite,
        cfg.getLong("cache", configKey, "maxEntries", Defaults.maxEntriesFor(configKey)),
        cfg.getLong("cache", configKey, "avgKeySize", Defaults.averageKeySizeFor(configKey)),
        cfg.getLong("cache", configKey, "avgValueSize", Defaults.avgValueSizeFor(configKey)),
        cfg.getInt("cache", configKey, "maxBloatFactor", Defaults.maxBloatFactorFor(configKey)),
        Duration.ofSeconds(
            cfg.getTimeUnit(
                "cache",
                null,
                "persistIndexEvery",
                Defaults.persistIndexEvery().toSeconds(),
                SECONDS)));
  }

  @AssistedInject
  ChronicleMapCacheConfig(
      @GerritServerConfig Config cfg,
      @Assisted("ConfigKey") String configKey,
      @Assisted File cacheFile,
      @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
      @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite,
      @Assisted("maxEntries") long maxEntries,
      @Assisted("avgKeySize") long avgKeySize,
      @Assisted("avgValueSize") long avgValueSize,
      @Assisted("maxBloatFactor") int maxBloatFactor,
      @Assisted("PersistIndexEvery") Duration persistIndexEvery) {
    this.cacheFile = cacheFile;
    this.cacheFileExists = cacheFile.exists();
    this.indexFile = resolveIndexFile(cacheFile);
    this.configKey = configKey;

    this.maxEntries = maxEntries;
    this.averageKeySize = avgKeySize;
    this.averageValueSize = avgValueSize;
    this.maxBloatFactor = maxBloatFactor;

    this.expireAfterWrite =
        Duration.ofSeconds(
            ConfigUtil.getTimeUnit(
                cfg, "cache", configKey, "maxAge", toSeconds(expireAfterWrite), SECONDS));
    this.refreshAfterWrite =
        Duration.ofSeconds(
            ConfigUtil.getTimeUnit(
                cfg,
                "cache",
                configKey,
                "refreshAfterWrite",
                toSeconds(refreshAfterWrite),
                SECONDS));

    this.percentageFreeSpaceEvictionThreshold =
        cfg.getInt(
            "cache",
            configKey,
            "percentageFreeSpaceEvictionThreshold",
            Defaults.percentageFreeSpaceEvictionThreshold());

    long persistIndexEverySeconds = persistIndexEvery.getSeconds();
    if (persistIndexEverySeconds < PRUNE_DELAY) {
      logger.atWarning().log(
          "Configured 'persistIndexEvery' duration [%ds] is lower than minimal threshold [%ds]."
              + " Minimal threshold will be used.",
          persistIndexEverySeconds, PRUNE_DELAY);
      persistIndexEverySeconds = PRUNE_DELAY;
    } else if (persistIndexEverySeconds % PRUNE_DELAY != 0L) {
      logger.atWarning().log(
          "Configured 'persistIndexEvery' duration [%ds] will be rounded down to a multiple of"
              + " [%ds]. [%ds] is the minimal 'persistIndexEvery' resolution.",
          persistIndexEverySeconds, PRUNE_DELAY, PRUNE_DELAY);
      persistIndexEverySeconds = (persistIndexEverySeconds / PRUNE_DELAY) * PRUNE_DELAY;
    }
    this.persistIndexEvery = Duration.ofSeconds(persistIndexEverySeconds);
    this.persistIndexEveryNthPrune = persistIndexEverySeconds / PRUNE_DELAY;
  }

  public int getPercentageFreeSpaceEvictionThreshold() {
    return percentageFreeSpaceEvictionThreshold;
  }

  public Duration getExpireAfterWrite() {
    return expireAfterWrite;
  }

  public Duration getRefreshAfterWrite() {
    return refreshAfterWrite;
  }

  public Duration getPersistIndexEvery() {
    return persistIndexEvery;
  }

  public long getPersistIndexEveryNthPrune() {
    return persistIndexEveryNthPrune;
  }

  public long getMaxEntries() {
    return maxEntries;
  }

  public File getCacheFile() {
    return cacheFile;
  }

  public boolean getCacheFileExists() {
    return cacheFileExists;
  }

  public File getIndexFile() {
    return indexFile;
  }

  public long getAverageKeySize() {
    return averageKeySize;
  }

  public long getAverageValueSize() {
    return averageValueSize;
  }

  public int getMaxBloatFactor() {
    return maxBloatFactor;
  }

  public String getConfigKey() {
    return configKey;
  }

  private static File resolveIndexFile(File persistedCacheFile) {
    String cacheFileName = persistedCacheFile.getName();
    String indexFileName = String.format("%s.index", FilenameUtils.getBaseName(cacheFileName));

    return Path.of(persistedCacheFile.getParent()).resolve(indexFileName).toFile();
  }

  private static long toSeconds(@Nullable Duration duration) {
    return duration != null ? duration.getSeconds() : 0;
  }

  protected static class Defaults {

    public static final long DEFAULT_MAX_ENTRIES = 1000;

    public static final long DEFAULT_AVG_KEY_SIZE = 128;
    public static final long DEFAULT_AVG_VALUE_SIZE = 2048;

    public static final int DEFAULT_MAX_BLOAT_FACTOR = 1;

    public static final int DEFAULT_PERCENTAGE_FREE_SPACE_EVICTION_THRESHOLD = 90;

    public static final Duration DEFAULT_PERSIST_INDEX_EVERY = Duration.ofMinutes(15);

    private static final ImmutableMap<String, DefaultConfig> defaultMap =
        new ImmutableMap.Builder<String, DefaultConfig>()
            .put("web_sessions", DefaultConfig.create(45, 221, 1000, 1))
            .put("change_notes", DefaultConfig.create(36, 10240, 1000, 3))
            .put("accounts", DefaultConfig.create(30, 256, 1000, 1))
            .put("diff", DefaultConfig.create(98, 10240, 1000, 2))
            .put("diff_intraline", DefaultConfig.create(512, 2048, 1000, 2))
            .put("diff_summary", DefaultConfig.create(128, 2048, 1000, 1))
            .put("external_ids_map", DefaultConfig.create(128, 204800, 2, 1))
            .put("oauth_tokens", DefaultConfig.create(8, 2048, 1000, 1))
            .put("change_kind", DefaultConfig.create(59, 26, 1000, 1))
            .put("mergeability", DefaultConfig.create(79, 16, 65000, 2))
            .put("pure_revert", DefaultConfig.create(55, 16, 1000, 1))
            .put("persisted_projects", DefaultConfig.create(128, 1024, 250, 2))
            .put("conflicts", DefaultConfig.create(70, 16, 1000, 1))
            .build();

    public static long averageKeySizeFor(String configKey) {
      return Optional.ofNullable(defaultMap.get(configKey))
          .map(DefaultConfig::averageKey)
          .orElseGet(
              () -> {
                logger.atWarning().log(
                    "Fallback average key size value is used for '%s' cache", configKey);
                return DEFAULT_AVG_KEY_SIZE;
              });
    }

    public static long avgValueSizeFor(String configKey) {
      return Optional.ofNullable(defaultMap.get(configKey))
          .map(DefaultConfig::averageValue)
          .orElseGet(
              () -> {
                logger.atWarning().log(
                    "Fallback average value size value is used for '%s' cache", configKey);
                return DEFAULT_AVG_VALUE_SIZE;
              });
    }

    public static long maxEntriesFor(String configKey) {
      return Optional.ofNullable(defaultMap.get(configKey))
          .map(DefaultConfig::entries)
          .orElseGet(
              () -> {
                logger.atWarning().log(
                    "Fallback max entries value is used for '%s' cache", configKey);
                return DEFAULT_MAX_ENTRIES;
              });
    }

    public static int maxBloatFactorFor(String configKey) {
      return Optional.ofNullable(defaultMap.get(configKey))
          .map(DefaultConfig::maxBloatFactor)
          .orElseGet(
              () -> {
                logger.atWarning().log(
                    "Fallback max bloat factor value is used for '%s' cache", configKey);
                return DEFAULT_MAX_BLOAT_FACTOR;
              });
    }

    public static int percentageFreeSpaceEvictionThreshold() {
      return DEFAULT_PERCENTAGE_FREE_SPACE_EVICTION_THRESHOLD;
    }

    public static Duration persistIndexEvery() {
      return DEFAULT_PERSIST_INDEX_EVERY;
    }
  }
}
