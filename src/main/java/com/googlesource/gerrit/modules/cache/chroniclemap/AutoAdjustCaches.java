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

import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheFactory.getCacheDir;

import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Option;

public class AutoAdjustCaches extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  protected static final String CONFIG_HEADER = "__CONFIG__";
  protected static final String TUNED_INFIX = "tuned";

  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final Path cacheDir;
  private final AdministerCachePermission adminCachePermission;

  @Option(
      name = "--dry-run",
      aliases = {"-d"},
      usage = "Calculate the average key and value size, but do not migrate the data.")
  private boolean dryRun;

  @Inject
  AutoAdjustCaches(
      @GerritServerConfig Config cfg,
      SitePaths site,
      DynamicMap<Cache<?, ?>> cacheMap,
      ChronicleMapCacheConfig.Factory configFactory,
      AdministerCachePermission adminCachePermission) {
    this.cacheMap = cacheMap;
    this.configFactory = configFactory;
    this.cacheDir = getCacheDir(site, cfg.getString("cache", null, "directory"));
    this.adminCachePermission = adminCachePermission;
  }

  @Override
  protected void run() throws Exception {
    adminCachePermission.checkCurrentUserAllowed(e -> stderr.println(e.getLocalizedMessage()));

    Config outputChronicleMapConfig = new Config();

    Map<String, ChronicleMapCacheImpl<Object, Object>> chronicleMapCaches = getChronicleMapCaches();

    chronicleMapCaches.forEach(
        (cacheName, currCache) -> {
          ImmutablePair<Long, Long> avgSizes = averageSizes(cacheName, currCache.getStore());
          if (!(avgSizes.getKey() > 0) || !(avgSizes.getValue() > 0)) {
            logger.atWarning().log(
                "Cache [%s] has %s entries, but average of (key: %d, value: %d). Skipping.",
                cacheName, currCache.size(), avgSizes.getKey(), avgSizes.getValue());
            return;
          }

          long averageKeySize = avgSizes.getKey();
          long averageValueSize = avgSizes.getValue();

          ChronicleMapCacheConfig currCacheConfig = currCache.getConfig();

          if (currCacheConfig.getAverageKeySize() == averageKeySize
              && currCacheConfig.getAverageValueSize() == averageValueSize) {
            return;
          }

          ChronicleMapCacheConfig newChronicleMapCacheConfig =
              makeChronicleMapConfig(currCache.getConfig(), averageKeySize, averageValueSize);

          updateOutputConfig(
              outputChronicleMapConfig,
              cacheName,
              averageKeySize,
              averageValueSize,
              currCache.getConfig().getMaxEntries(),
              currCache.getConfig().getMaxBloatFactor());

          if (!dryRun) {
            try {
              ChronicleMapCacheImpl<Object, Object> newCache =
                  new ChronicleMapCacheImpl<>(
                      currCache.getCacheDefinition(),
                      newChronicleMapCacheConfig,
                      null,
                      new DisabledMetricMaker());

              TextProgressMonitor cacheMigrationProgress = new TextProgressMonitor(stdout);
              cacheMigrationProgress.beginTask(
                  String.format("[%s] migrate content", cacheName), (int) currCache.size());

              currCache
                  .getStore()
                  .forEach(
                      (k, v) -> {
                        try {
                          newCache.putUnchecked(k, v);
                          cacheMigrationProgress.update(1);
                        } catch (Exception e) {
                          logger.atWarning().withCause(e).log(
                              "[%s] Could not migrate entry %s -> %s",
                              cacheName, k.getValue(), v.getValue());
                        }
                      });

            } catch (IOException e) {
              stderr.println(String.format("Could not create new cache %s", cacheName));
            }
          }
        });

    stdout.println();
    stdout.println("**********************************");

    if (outputChronicleMapConfig.getSections().isEmpty()) {
      stdout.println("All exsting caches are already tuned: no changes needed.");
      return;
    }

    stdout.println("** Chronicle-map config changes **");
    stdout.println("**********************************");
    stdout.println();
    stdout.println(CONFIG_HEADER);
    stdout.println(outputChronicleMapConfig.toText());
  }

  private ImmutablePair<Long, Long> averageSizes(
      String cacheName, ConcurrentMap<KeyWrapper<Object>, TimedValue<Object>> store) {
    long kAvg = 0;
    long vAvg = 0;

    if (store.isEmpty()) return ImmutablePair.of(kAvg, vAvg);

    TextProgressMonitor progress = new TextProgressMonitor(stdout);

    progress.beginTask(
        String.format("[%s] calculate average key/value size", cacheName), store.size());

    int i = 1;
    for (Map.Entry<KeyWrapper<Object>, TimedValue<Object>> entry : store.entrySet()) {
      kAvg = kAvg + (serializedKeyLength(cacheName, entry.getKey()) - kAvg) / i;
      vAvg = vAvg + (serializedValueLength(cacheName, entry.getValue()) - vAvg) / i;
      progress.update(1);
    }
    progress.endTask();
    return ImmutablePair.of(kAvg, vAvg);
  }

  private static int serializedKeyLength(String cacheName, KeyWrapper<Object> keyWrapper) {
    return CacheSerializers.getKeySerializer(cacheName).serialize(keyWrapper.getValue()).length;
  }

  private static int serializedValueLength(String cacheName, TimedValue<Object> timedValue) {
    return CacheSerializers.getValueSerializer(cacheName).serialize(timedValue.getValue()).length;
  }

  private ChronicleMapCacheConfig makeChronicleMapConfig(
      ChronicleMapCacheConfig currentChronicleMapConfig,
      long averageKeySize,
      long averageValueSize) {

    return configFactory.createWithValues(
        currentChronicleMapConfig.getConfigKey(),
        resolveNewFile(currentChronicleMapConfig.getPersistedFile().getName()),
        currentChronicleMapConfig.getExpireAfterWrite(),
        currentChronicleMapConfig.getRefreshAfterWrite(),
        currentChronicleMapConfig.getMaxEntries(),
        averageKeySize,
        averageValueSize,
        currentChronicleMapConfig.getMaxBloatFactor());
  }

  private File resolveNewFile(String currentFileName) {
    String newFileName =
        String.format(
            "%s_%s_%s.%s",
            FilenameUtils.getBaseName(currentFileName),
            TUNED_INFIX,
            System.currentTimeMillis(),
            FilenameUtils.getExtension(currentFileName));

    return cacheDir.resolve(newFileName).toFile();
  }

  private static void updateOutputConfig(
      Config config,
      String cacheName,
      long averageKeySize,
      long averageValueSize,
      long maxEntries,
      int maxBloatFactor) {

    config.setLong("cache", cacheName, "avgKeySize", averageKeySize);
    config.setLong("cache", cacheName, "avgValueSize", averageValueSize);
    config.setLong("cache", cacheName, "maxEntries", maxEntries);
    config.setLong("cache", cacheName, "maxBloatFactor", maxBloatFactor);
  }

  @SuppressWarnings("unchecked")
  private Map<String, ChronicleMapCacheImpl<Object, Object>> getChronicleMapCaches() {
    return cacheMap.plugins().stream()
        .map(cacheMap::byPlugin)
        .flatMap(
            pluginCaches ->
                pluginCaches.entrySet().stream()
                    .map(entry -> ImmutablePair.of(entry.getKey(), entry.getValue().get())))
        .filter(
            pair -> pair.getValue() instanceof ChronicleMapCacheImpl && pair.getValue().size() > 0)
        .collect(
            Collectors.toMap(
                ImmutablePair::getKey, p -> (ChronicleMapCacheImpl<Object, Object>) p.getValue()));
  }
}
