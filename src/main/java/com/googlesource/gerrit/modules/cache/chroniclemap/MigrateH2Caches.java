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
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.googlesource.gerrit.modules.cache.chroniclemap.command.H2AggregateData;
import com.googlesource.gerrit.modules.cache.chroniclemap.command.H2CacheSshCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.h2.Driver;
import org.kohsuke.args4j.Option;

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
    Set<PersistentCacheDef<?, ?>> cacheDefs = getAllBoundPersistentCacheDefs();

    Config outputChronicleMapConfig = new Config();

    for (PersistentCacheDef<?, ?> in : cacheDefs) {
      Optional<Path> h2CacheFile = getH2CacheFile(cacheDir.get(), in.name());

      if (h2CacheFile.isPresent()) {
        H2AggregateData stats = getStats(h2CacheFile.get());

        if (!stats.isEmpty()) {
          ChronicleMapCacheImpl<?, ?> chronicleMapCache =
              new ChronicleMapCacheImpl<>(
                  in,
                  makeChronicleMapConfig(
                      configFactory, cacheDir.get(), in, stats, sizeMultiplier, maxBloatFactor),
                  null,
                  new DisabledMetricMaker());
          doMigrate(h2CacheFile.get(), in, chronicleMapCache, stats.size());
          chronicleMapCache.close();
          appendBloatedConfig(outputChronicleMapConfig, stats);
        }
      }
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
      PersistentCacheDef<?, ?> in,
      H2AggregateData stats,
      int sizeMultiplier,
      int maxBloatFactor) {
    return configFactory.createWithValues(
        in.configKey(),
        ChronicleMapCacheFactory.fileName(cacheDir, in.name(), in.version()),
        in.expireAfterWrite(),
        in.refreshAfterWrite(),
        stats.size() * sizeMultiplier,
        stats.avgKeySize(),
        stats.avgValueSize(),
        maxBloatFactor);
  }

  private void doMigrate(
      Path h2File,
      PersistentCacheDef<?, ?> in,
      ChronicleMapCacheImpl<?, ?> chronicleMapCache,
      long totalEntries)
      throws UnloggedFailure {

    TextProgressMonitor cacheProgress = new TextProgressMonitor(stdout);
    cacheProgress.beginTask(String.format("[%s]", in.name()), (int) totalEntries);

    String url = jdbcUrl(h2File);
    try (Connection conn = Driver.load().connect(url, null)) {
      PreparedStatement preparedStatement =
          conn.prepareStatement("SELECT k, v, created FROM data WHERE version=?");
      preparedStatement.setInt(1, in.version());

      try (ResultSet r = preparedStatement.executeQuery()) {
        while (r.next()) {
          Object key = in.keySerializer().deserialize(r.getBytes(1));
          Object value = in.valueSerializer().deserialize(r.getBytes(2));
          Timestamp created = r.getTimestamp(3);
          chronicleMapCache.putUnchecked(key, value, created);
          cacheProgress.update(1);
        }
      }

    } catch (Exception e) {
      stderr.println(String.format("FATAL: error migrating %s H2 cache", in.name()));
      throw die(e);
    }
    cacheProgress.endTask();
  }

  private Set<PersistentCacheDef<?, ?>> getAllBoundPersistentCacheDefs() {
    Set<PersistentCacheDef<?, ?>> cacheDefs = new HashSet<>();
    for (Map.Entry<Key<?>, Binding<?>> entry : injector.getParent().getAllBindings().entrySet()) {
      final Class<?> rawType = entry.getKey().getTypeLiteral().getRawType();
      if ("PersistentCacheDef".equals(rawType.getSimpleName())) {
        cacheDefs.add((PersistentCacheDef<?, ?>) entry.getValue().getProvider().get());
      }
    }
    return cacheDefs;
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
}
