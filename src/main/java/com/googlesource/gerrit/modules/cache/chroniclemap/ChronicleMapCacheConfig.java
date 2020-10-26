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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

public class ChronicleMapCacheConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final File persistedFile;
  private final long diskLimit;
  private final long maxEntries;
  private final long averageKeySize;
  private final long averageValueSize;
  private final Duration expireAfterWrite;
  private final Duration refreshAfterWrite;
  private final int maxBloatFactor;
  private final int version;
  private final int percentageFreeSpaceEvictionThreshold;
  private final int percentageHotKeys;

  public interface Factory {
    ChronicleMapCacheConfig create(
        @Assisted("Name") String name,
        @Assisted("ConfigKey") String configKey,
        @Assisted("DiskLimit") long diskLimit,
        @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
        @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite,
        int version);
  }

  @AssistedInject
  ChronicleMapCacheConfig(
      @GerritServerConfig Config cfg,
      SitePaths site,
      @Assisted("Name") String name,
      @Assisted("ConfigKey") String configKey,
      @Assisted("DiskLimit") long diskLimit,
      @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
      @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite,
      @Assisted int version)
      throws IOException {
    this.version = version;
    final Path cacheDir = getCacheDir(site, cfg.getString("cache", null, "directory"));
    this.persistedFile =
        cacheDir != null
            ? cacheDir.resolve(String.format("%s_%s.dat", name, version)).toFile()
            : null;
    this.diskLimit = cfg.getLong("cache", configKey, "diskLimit", diskLimit);

    this.maxEntries =
        cfg.getLong("cache", configKey, "maxEntries", Defaults.maxEntriesFor(configKey));
    this.averageKeySize =
        cfg.getLong("cache", configKey, "avgKeySize", Defaults.averageKeySizeFor(configKey));
    this.averageValueSize =
        cfg.getLong("cache", configKey, "avgValueSize", Defaults.avgValueSizeFor(configKey));
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

    this.maxBloatFactor =
        cfg.getInt("cache", configKey, "maxBloatFactor", Defaults.maxBloatFactorFor(configKey));

    this.percentageFreeSpaceEvictionThreshold =
        cfg.getInt(
            "cache",
            configKey,
            "percentageFreeSpaceEvictionThreshold",
            Defaults.percentageFreeSpaceEvictionThreshold());

    this.percentageHotKeys =
        cfg.getInt("cache", configKey, "percentageHotKeys", Defaults.percentageHotKeys());

    if (percentageHotKeys == 0 || percentageHotKeys == 100) {
      throw new IllegalArgumentException("Invalid 'percentageHotKeys': should be in range [1-99]");
    }
  }

  public int getPercentageFreeSpaceEvictionThreshold() {
    return percentageFreeSpaceEvictionThreshold;
  }

  public int getpercentageHotKeys() {
    return percentageHotKeys;
  }

  public int getVersion() {
    return version;
  }

  public Duration getExpireAfterWrite() {
    return expireAfterWrite;
  }

  public Duration getRefreshAfterWrite() {
    return refreshAfterWrite;
  }

  public long getMaxEntries() {
    return maxEntries;
  }

  public File getPersistedFile() {
    return persistedFile;
  }

  public long getAverageKeySize() {
    return averageKeySize;
  }

  public long getAverageValueSize() {
    return averageValueSize;
  }

  public long getDiskLimit() {
    return diskLimit;
  }

  public int getMaxBloatFactor() {
    return maxBloatFactor;
  }

  private static Path getCacheDir(SitePaths site, String name) throws IOException {
    if (name == null) {
      return null;
    }
    Path loc = site.resolve(name);
    if (!Files.exists(loc)) {
      Files.createDirectories(loc);
    }
    if (!Files.isWritable(loc)) {
      throw new IOException(String.format("Can't write to disk cache: %s", loc.toAbsolutePath()));
    }
    logger.atFine().log("Enabling disk cache %s", loc.toAbsolutePath());
    return loc;
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
    public static final int DEFAULT_PERCENTAGE_HOT_KEYS = 50;

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
          .orElse(DEFAULT_AVG_KEY_SIZE);
    }

    public static long avgValueSizeFor(String configKey) {
      return Optional.ofNullable(defaultMap.get(configKey))
          .map(DefaultConfig::averageValue)
          .orElse(DEFAULT_AVG_VALUE_SIZE);
    }

    public static long maxEntriesFor(String configKey) {
      return Optional.ofNullable(defaultMap.get(configKey))
          .map(DefaultConfig::entries)
          .orElse(DEFAULT_MAX_ENTRIES);
    }

    public static int maxBloatFactorFor(String configKey) {
      return Optional.ofNullable(defaultMap.get(configKey))
          .map(DefaultConfig::maxBloatFactor)
          .orElse(DEFAULT_MAX_BLOAT_FACTOR);
    }

    public static int percentageFreeSpaceEvictionThreshold() {
      return DEFAULT_PERCENTAGE_FREE_SPACE_EVICTION_THRESHOLD;
    }

    public static int percentageHotKeys() {
      return DEFAULT_PERCENTAGE_HOT_KEYS;
    }
  }
}
