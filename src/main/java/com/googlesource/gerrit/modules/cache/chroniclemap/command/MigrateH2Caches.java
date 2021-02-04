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
package com.googlesource.gerrit.modules.cache.chroniclemap.command;

import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig;
import com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheFactory;
import com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheImpl;
import java.io.IOException;
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
import org.h2.Driver;

public class MigrateH2Caches extends H2CacheSshCommand {

  private final Injector injector;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final MetricMaker metricMaker;
  private final Path cacheDir;

  @Inject
  MigrateH2Caches(
      @GerritServerConfig Config cfg,
      SitePaths site,
      Injector injector,
      ChronicleMapCacheConfig.Factory configFactory,
      MetricMaker metricMaker)
      throws IOException {
    this.injector = injector;
    this.configFactory = configFactory;
    this.metricMaker = metricMaker;
    this.site = site;
    this.gerritConfig = cfg;
    this.cacheDir =
        getCacheDir()
            .orElseThrow(
                () -> new IOException("Cannot run migration, cache directory is not configured."));
  }

  @Override
  protected void run() throws Exception {
    stdout.println("Migrating H2 caches to cache-chroniclemap...");

    Set<PersistentCacheDef<?, ?>> cacheDefs = getAllBoundPersistentCacheDefs();

    for (PersistentCacheDef<?, ?> in : cacheDefs) {
      Optional<Path> h2CacheFile = getH2CacheFile(in.name());

      if (!h2CacheFile.isPresent()) {
        stdout.println(
            String.format("[%s] H2 persistent cache file not found. Skipping.", in.name()));
        continue;
      }

      H2AggregateData stats = getStats(h2CacheFile.get());

      if (stats.isEmpty()) {
        stdout.println(
            String.format("[%s] H2 persistent cache file is empty. Skipping.", in.name()));
        continue;
      }

      ChronicleMapCacheImpl<?, ?> chronicleMapCache =
          new ChronicleMapCacheImpl<>(in, makeChronicleMapConfig(in, stats), null, metricMaker);

      doMigrate(h2CacheFile.get(), in, chronicleMapCache);
    }
  }

  private ChronicleMapCacheConfig makeChronicleMapConfig(
      PersistentCacheDef<?, ?> in, H2AggregateData stats) {
    return configFactory.createWithValues(
        in.configKey(),
        ChronicleMapCacheFactory.fileName(cacheDir, in.name(), in.version()),
        in.expireAfterWrite(),
        in.refreshAfterWrite(),
        stats.size() * 3,
        stats.avgKeySize(),
        stats.avgValueSize(),
        3);
  }

  private void doMigrate(
      Path h2File, PersistentCacheDef<?, ?> in, ChronicleMapCacheImpl<?, ?> chronicleMapCache)
      throws UnloggedFailure {
    String url = jdbcUrl(h2File);
    try (Connection conn = Driver.load().connect(url, null)) {
      PreparedStatement preparedStatement =
          conn.prepareStatement("SELECT k, v, created FROM data WHERE version=?");
      preparedStatement.setInt(1, in.version());

      try (ResultSet r = preparedStatement.executeQuery()) {
        if (!r.next()) {
          stdout.println(
              String.format(
                  "[%s - version: %d] H2 persistent cache is empty. Skipping.",
                  in.name(), in.version()));
        }
        while (r.next()) {
          Object key = in.keySerializer().deserialize(r.getBytes(1));
          Object value = in.valueSerializer().deserialize(r.getBytes(2));
          Timestamp created = r.getTimestamp(3);
          chronicleMapCache.putUnchecked(key, value, created);
        }
      }

    } catch (Exception e) {
      stderr.println(String.format("Fatal error when migrating %s H2 cache", in.name()));
      throw die(e);
    }
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

  private Optional<Path> getH2CacheFile(String name) {
    Path h2CacheFile = cacheDir.resolve(String.format("%s.%s", name, H2_SUFFIX));
    if (Files.exists(h2CacheFile)) {
      return Optional.of(h2CacheFile);
    }
    return Optional.empty();
  }
}
