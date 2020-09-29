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

  public static final long DEFAULT_MAX_ENTRIES = 1000;

  public static final long DEFAULT_AVG_KEY_SIZE = 128;
  public static final long DEFAULT_AVG_VALUE_SIZE = 2048;

  public static final int DEFAULT_MAX_BLOAT_FACTOR = 1;

  public interface Factory {
    ChronicleMapCacheConfig create(
        @Assisted("Name") String name,
        @Assisted("ConfigKey") String configKey,
        @Assisted("DiskLimit") long diskLimit,
        @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
        @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite);
  }

  @AssistedInject
  ChronicleMapCacheConfig(
      @GerritServerConfig Config cfg,
      SitePaths site,
      @Assisted("Name") String name,
      @Assisted("ConfigKey") String configKey,
      @Assisted("DiskLimit") long diskLimit,
      @Nullable @Assisted("ExpireAfterWrite") Duration expireAfterWrite,
      @Nullable @Assisted("RefreshAfterWrite") Duration refreshAfterWrite)
      throws IOException {
    final Path cacheDir = getCacheDir(site, cfg.getString("cache", null, "directory"));
    this.persistedFile =
        cacheDir != null ? cacheDir.resolve(String.format("%s.dat", name)).toFile() : null;
    this.diskLimit = cfg.getLong("cache", configKey, "diskLimit", diskLimit);

    this.maxEntries = cfg.getLong("cache", configKey, "maxEntries", DEFAULT_MAX_ENTRIES);
    this.averageKeySize = cfg.getLong("cache", configKey, "avgKeySize", DEFAULT_AVG_KEY_SIZE);
    this.averageValueSize = cfg.getLong("cache", configKey, "avgValueSize", DEFAULT_AVG_VALUE_SIZE);
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
        cfg.getInt("cache", configKey, "maxBloatFactor", DEFAULT_MAX_BLOAT_FACTOR);
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
}
