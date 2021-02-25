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

import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheCommand.H2_SUFFIX;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheCommand.appendToConfig;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheCommand.getStats;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheCommand.jdbcUrl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.h2.Driver;
import org.kohsuke.args4j.Option;

@Singleton
public class H2MigrationEndpoint implements RestReadView<ConfigResource> {
  protected static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Injector injector;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final SitePaths site;
  private final Config gerritConfig;

  public static int DEFAULT_SIZE_MULTIPLIER = 4;
  public static int DEFAULT_MAX_BLOAT_FACTOR = 4;

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
  H2MigrationEndpoint(
      Injector injector,
      @GerritServerConfig Config cfg,
      SitePaths site,
      ChronicleMapCacheConfig.Factory configFactory) {
    this.injector = injector;
    this.configFactory = configFactory;
    this.site = site;
    this.gerritConfig = cfg;
  }

  @Override
  public Response<String> apply(ConfigResource resource) throws Exception {
    Optional<Path> cacheDir = getCacheDir();

    if (!cacheDir.isPresent()) {
      return Response.withStatusCode(
          HttpServletResponse.SC_BAD_REQUEST,
          "Cannot run migration, cache directory is not configured");
    }

    logger.atInfo().log("Migrating H2 caches to Chronicle-Map...");
    logger.atInfo().log("* Size multiplier: " + sizeMultiplier);
    logger.atInfo().log("* Max Bloat Factor: " + maxBloatFactor);
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
          doMigrate(h2CacheFile.get(), in, chronicleMapCache);
          chronicleMapCache.close();
          appendBloatedConfig(outputChronicleMapConfig, stats);
        }
      }
    }
    logger.atInfo().log("Complete!");
    return Response.ok(outputChronicleMapConfig.toText());
  }

  protected Optional<Path> getCacheDir() throws IOException {
    String name = gerritConfig.getString("cache", null, "directory");
    if (name == null) {
      return Optional.empty();
    }
    Path loc = site.resolve(name);
    if (!Files.exists(loc)) {
      throw new IOException(
          String.format("disk cache is configured but doesn't exist: %s", loc.toAbsolutePath()));
    }
    if (!Files.isReadable(loc)) {
      throw new IOException(String.format("Can't read from disk cache: %s", loc.toAbsolutePath()));
    }
    logger.atFine().log("Enabling disk cache %s", loc.toAbsolutePath());
    return Optional.of(loc);
  }

  @VisibleForTesting
  protected Set<PersistentCacheDef<?, ?>> getAllBoundPersistentCacheDefs() {
    Set<PersistentCacheDef<?, ?>> cacheDefs = new HashSet<>();
    Stream.concat(
            injector.getAllBindings().entrySet().stream(),
            injector.getParent().getAllBindings().entrySet().stream())
        .forEach(
            entry -> {
              final Class<?> rawType = entry.getKey().getTypeLiteral().getRawType();
              if ("PersistentCacheDef".equals(rawType.getSimpleName())) {
                cacheDefs.add((PersistentCacheDef<?, ?>) entry.getValue().getProvider().get());
              }
            });
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
      Path h2File, PersistentCacheDef<?, ?> in, ChronicleMapCacheImpl<?, ?> chronicleMapCache)
      throws RestApiException {

    String url = jdbcUrl(h2File);
    try (Connection conn = Driver.load().connect(url, null)) {
      PreparedStatement preparedStatement =
          conn.prepareStatement("SELECT k, v, created FROM data WHERE version=?");
      preparedStatement.setInt(1, in.version());

      try (ResultSet r = preparedStatement.executeQuery()) {
        while (r.next()) {
          Object key =
              isStringType(in.keyType())
                  ? r.getString(1)
                  : in.keySerializer().deserialize(r.getBytes(1));
          Object value =
              isStringType(in.valueType())
                  ? r.getString(2)
                  : in.valueSerializer().deserialize(r.getBytes(2));
          Timestamp created = r.getTimestamp(3);
          chronicleMapCache.putUnchecked(key, value, created);
        }
      }

    } catch (Exception e) {
      String message = String.format("FATAL: error migrating %s H2 cache", in.name());
      logger.atSevere().withCause(e).log(message);
      throw RestApiException.wrap(message, e);
    }
  }

  private boolean isStringType(TypeLiteral<?> typeLiteral) {
    return typeLiteral.getRawType().getSimpleName().equals("String");
  }
}
