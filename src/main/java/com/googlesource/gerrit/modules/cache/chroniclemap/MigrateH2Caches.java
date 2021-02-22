// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.h2.Driver;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "migrate-h2-caches", description = "Migrate H2 caches to Chronicle-Map")
public class MigrateH2Caches extends H2CacheSshCommand {

  private final Injector injector;
  private final ChronicleMapCacheConfig.Factory configFactory;

  protected static int DEFAULT_SIZE_MULTIPLIER = 3;
  protected static int DEFAULT_MAX_BLOAT_FACTOR = 3;

  @Option(
      name = "--size-multiplier",
      aliases = {"-s"},
      metaVar = "MULTIPLIER",
      usage = "Multiplicative factor for the number of entries allowed in chronicle-map")
  private int sizeMultiplier = DEFAULT_SIZE_MULTIPLIER;

  @Option(
      name = "--max-bloat-factor",
      aliases = {"-m"},
      metaVar = "FACTOR",
      usage = "maximum number of times chronicle-map cache is allowed to grow in size")
  private int maxBloatFactor = DEFAULT_MAX_BLOAT_FACTOR;

  @Inject
  MigrateH2Caches(
      @GerritServerConfig Config cfg,
      SitePaths site,
      Injector injector,
      ChronicleMapCacheConfig.Factory configFactory) {
    this.injector = injector;
    this.configFactory = configFactory;
    this.site = site;
    this.gerritConfig = cfg;
  }

  @Override
  protected void run() throws Exception {
    Optional<Path> cacheDir = getCacheDir();

    if (!cacheDir.isPresent()) {
      throw die("Cannot run migration, cache directory is not configured");
    }

    stdout.println("Migrating H2 caches to Chronicle-Map...");
    stdout.println("* Size multiplier: " + sizeMultiplier);
    stdout.println("* Max Bloat Factor: " + maxBloatFactor);
    Set<Path> h2Files = getH2CacheFiles();

    Config outputChronicleMapConfig = new Config();

    for (Path h2CacheFile : h2Files) {
      H2AggregateData stats = getStats(h2CacheFile);
      String baseName = baseName(h2CacheFile);

      if (stats.isEmpty()) {
        stdout.println(String.format("WARN: Cache %s is empty, skipping.", baseName));
        continue;
      }

      ChronicleMapCacheImpl<Byte[], Byte[]> chronicleMapCache =
          new ChronicleMapCacheImpl<>(
              makeChronicleMapConfig(
                  configFactory, cacheDir.get(), baseName, stats, sizeMultiplier, maxBloatFactor),
              baseName);
      doMigrate(h2CacheFile, baseName, chronicleMapCache, stats.size());
      chronicleMapCache.close();
      appendBloatedConfig(outputChronicleMapConfig, stats);
    }
    stdout.println("Complete!");
    stdout.println();
    stdout.println("****************************");
    stdout.println("** Chronicle-map template **");
    stdout.println("****************************");
    stdout.println();
    stdout.println(outputChronicleMapConfig.toText());
  }

  protected static ChronicleMapCacheConfig makeChronicleMapConfig(
      ChronicleMapCacheConfig.Factory configFactory,
      Path cacheDir,
      String baseName,
      H2AggregateData stats,
      int sizeMultiplier,
      int maxBloatFactor) {
    return configFactory.createWithValues(
        baseName,
        ChronicleMapCacheFactory.fileName(cacheDir, baseName, 0),
        null,
        null,
        stats.size() * sizeMultiplier,
        stats.avgKeySize(),
        stats.avgValueSize(),
        maxBloatFactor);
  }

  private Byte[] box(byte[] bytes) {
    Byte[] bytesWrapper = new Byte[bytes.length];
    for (int j = 0; j < bytes.length; j++) bytesWrapper[j] = bytes[j];
    return bytesWrapper;
  }

  private void doMigrate(
      Path h2File,
      String name,
      ChronicleMapCacheImpl<Byte[], Byte[]> chronicleMapCache,
      long totalEntries)
      throws UnloggedFailure {

    TextProgressMonitor cacheProgress = new TextProgressMonitor(stdout);
    cacheProgress.beginTask(String.format("[%s]", name), (int) totalEntries);

    String url = jdbcUrl(h2File);
    try (Connection conn = Driver.load().connect(url, null)) {
      PreparedStatement preparedStatement =
          conn.prepareStatement("SELECT k, v, created FROM data WHERE version=?");
      preparedStatement.setInt(1, 0);

      try (ResultSet r = preparedStatement.executeQuery()) {
        while (r.next()) {
          final Byte[] key = box(r.getBytes(1));
          final Byte[] value = box(r.getBytes(2));
          Timestamp created = r.getTimestamp(3);
          chronicleMapCache.putUnchecked(key, value, created);
          cacheProgress.update(1);
        }
      }

    } catch (Exception e) {
      String message = String.format("FATAL: error migrating %s H2 cache", name);
      logger.atSevere().withCause(e).log(message);
      stderr.println(message);
      throw die(e);
    }
    cacheProgress.endTask();
  }

  private byte[] getBytes(ResultSet r, int columnIndex, CacheSerializer<?> serializer)
      throws SQLException {
    return (serializer instanceof StringCacheSerializer)
        ? r.getString(columnIndex).getBytes()
        : r.getBytes(columnIndex);
  }

  private Optional<Path> getH2CacheFile(Path cacheDir, String name) {
    Path h2CacheFile = cacheDir.resolve(String.format("%s.%s", name, H2_SUFFIX));
    if (Files.exists(h2CacheFile)) {
      return Optional.of(h2CacheFile);
    }
    return Optional.empty();
  }

  private void appendBloatedConfig(Config config, H2AggregateData stats) {
    appendToConfig(
        config,
        H2AggregateData.create(
            stats.cacheName(),
            stats.size() * sizeMultiplier,
            stats.avgKeySize(),
            stats.avgValueSize()));
    config.setLong("cache", stats.cacheName(), "maxBloatFactor", maxBloatFactor);
  }

  private Set<Path> getH2CacheFiles() throws UnloggedFailure {

    try {
      return getCacheDir()
          .map(
              cacheDir -> {
                try {
                  return Files.walk(cacheDir)
                      .filter(path -> path.toString().endsWith(H2_SUFFIX))
                      .collect(Collectors.toSet());
                } catch (IOException e) {
                  logger.atSevere().withCause(e).log("Could not read H2 files");
                  return Collections.<Path>emptySet();
                }
              })
          .orElse(Collections.emptySet());
    } catch (IOException e) {
      throw die(e);
    }
  }
}
