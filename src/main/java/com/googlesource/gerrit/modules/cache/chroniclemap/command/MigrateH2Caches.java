package com.googlesource.gerrit.modules.cache.chroniclemap.command;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.h2.H2CacheImpl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig;
import com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheImpl;
import org.eclipse.jgit.lib.Config;
import org.h2.Driver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MigrateH2Caches extends H2CacheSshCommand {

  private final Map<String, H2CacheImpl<?, ?>> h2PersistentCaches;
  private final Injector injector;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final MetricMaker metricMaker;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final Optional<Path> maybeCacheDir;

  @Inject
  MigrateH2Caches(
      @GerritServerConfig Config cfg,
      SitePaths site,
      DynamicMap<Cache<?, ?>> cacheMap,
      Injector injector,
      ChronicleMapCacheConfig.Factory configFactory,
      MetricMaker metricMaker)
      throws IOException {
    this.h2PersistentCaches = h2PersistentCaches(cacheMap);
    this.cacheMap = cacheMap;
    this.injector = injector;
    this.configFactory = configFactory;
    this.metricMaker = metricMaker;
    this.cacheDirectory = cfg.getString("cache", null, "directory");
    this.site = site;
    this.maybeCacheDir = getCacheDir(site, cacheDirectory);
  }

  private Optional<Path> getH2CacheFile(String name) {

    return maybeCacheDir.flatMap(
        cacheDir -> {
          Path h2CacheFile = cacheDir.resolve(name + ".h2.db");
          if (Files.exists(h2CacheFile)) {
            return Optional.of(h2CacheFile);
          }
          return Optional.empty();
        });
  }

  @Override
  protected void run() throws Exception {
    stdout.println("Migrating H2 caches to cache-chroniclemap...");

    Set<PersistentCacheDef<?, ?>> cacheDefs = persistentCacheDefs();

    for (PersistentCacheDef<?, ?> in : cacheDefs) {
      Optional<Path> h2CacheFile = getH2CacheFile(in.name());

      if (!h2CacheFile.isPresent()) {
        stdout.println(
            String.format("[%s] H2 persistent cache file not found. Skipping.", in.name()));
        continue;
      }

      H2DataStats stats = getStats(h2CacheFile.get());

      if (stats.isEmpty()) {
        stdout.println(
            String.format("[%s] H2 persistent cache file is empty. Skipping.", in.name()));
        continue;
      }

      ChronicleMapCacheConfig chronicleMapConfig =
          configFactory.createWithValues(
              in.name(),
              in.configKey(),
              in.diskLimit(),
              in.expireAfterWrite(),
              in.refreshAfterWrite(),
              stats.size() * 3,
              stats.avgKeySize(),
              stats.avgValueSize(),
              3,
              in.version());
      ChronicleMapCacheImpl<?, ?> chronicleMapCache =
          new ChronicleMapCacheImpl<>(in, chronicleMapConfig, null, metricMaker);

      doMigrate(h2CacheFile.get(), in, chronicleMapCache);

      //      H2CacheImpl<?, ?> h2Cache = h2PersistentCaches.get(in.name());
      //
      //      for (Map.Entry<?, ?> entry : h2Cache.asMap().entrySet()) {
      //        chronicleMapCache.putUnchecked(entry.getKey(), entry.getValue());
      //      }
    }
  }

  protected void doMigrate(
      Path h2File, PersistentCacheDef<?, ?> in, ChronicleMapCacheImpl<?, ?> chronicleMapCache)
      throws UnloggedFailure {
    String url = jdbcUrl(h2File);
    try (Connection conn = Driver.load().connect(url, null);) {
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

  private Set<PersistentCacheDef<?, ?>> persistentCacheDefs() {
    Set<PersistentCacheDef<?, ?>> cacheDefs = new HashSet<>();
    for (Map.Entry<Key<?>, Binding<?>> entry : injector.getParent().getAllBindings().entrySet()) {
      final Class<?> rawType = entry.getKey().getTypeLiteral().getRawType();
      stdout.println("****** " + rawType.getCanonicalName());
      if ("PersistentCacheDef".equals(rawType.getSimpleName())) {
        cacheDefs.add((PersistentCacheDef<?, ?>) entry.getValue().getProvider().get());
      }
    }
    return cacheDefs;
  }

  private static Map<String, H2CacheImpl<?, ?>> h2PersistentCaches(
      DynamicMap<Cache<?, ?>> cacheMap) {
    Map<String, H2CacheImpl<?, ?>> persistent = new HashMap<>();

    cacheMap.forEach(
        c -> {
          final Cache<?, ?> cache = c.get();
          if (cache instanceof H2CacheImpl) {
            persistent.put(c.getExportName(), (H2CacheImpl<?, ?>) cache);
          }
        });

    return persistent;
  }
}
