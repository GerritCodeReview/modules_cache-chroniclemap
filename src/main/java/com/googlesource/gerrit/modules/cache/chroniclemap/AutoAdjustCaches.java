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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;

public class AutoAdjustCaches {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  protected static final String CONFIG_HEADER = "__CONFIG__";
  protected static final String TUNED_INFIX = "_tuned_";

  protected static final Integer MAX_ENTRIES_MULTIPLIER = 2;
  protected static final Integer PERCENTAGE_SIZE_INCREASE_THRESHOLD = 50;

  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final Path cacheDir;
  private final AdministerCachePermission adminCachePermission;

  private boolean dryRun;
  private Optional<Long> optionalMaxEntries = Optional.empty();
  private Set<String> cacheNames = new HashSet<>();

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

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

  public Optional<Long> getOptionalMaxEntries() {
    return optionalMaxEntries;
  }

  public void setOptionalMaxEntries(Optional<Long> maxEntries) {
    this.optionalMaxEntries = maxEntries;
  }

  public void addCacheNames(List<String> cacheNames) {
    this.cacheNames.addAll(cacheNames);
  }

  protected Config run(@Nullable ProgressMonitor optionalProgressMonitor)
      throws AuthException, PermissionBackendException, IOException {
    ProgressMonitor progressMonitor =
        optionalProgressMonitor == null ? NullProgressMonitor.INSTANCE : optionalProgressMonitor;
    adminCachePermission.checkCurrentUserAllowed(null);

    Config outputChronicleMapConfig = new Config();

    Map<String, ChronicleMapCacheImpl<Object, Object>> chronicleMapCaches = getChronicleMapCaches();

    for (Map.Entry<String, ChronicleMapCacheImpl<Object, Object>> cache :
        chronicleMapCaches.entrySet()) {
      String cacheName = cache.getKey();
      ChronicleMapCacheImpl<Object, Object> currCache = cache.getValue();

      {
        ImmutablePair<Long, Long> avgSizes =
            averageSizes(cacheName, currCache.getStore(), progressMonitor);
        if (!(avgSizes.getKey() > 0) || !(avgSizes.getValue() > 0)) {
          logger.atWarning().log(
              "Cache [%s] has %s entries, but average of (key: %d, value: %d). Skipping.",
              cacheName, currCache.diskStats().size(), avgSizes.getKey(), avgSizes.getValue());
          continue;
        }

        long averageKeySize = avgSizes.getKey();
        long averageValueSize = avgSizes.getValue();

        ChronicleMapCacheConfig currCacheConfig = currCache.getConfig();
        long newMaxEntries = newMaxEntries(currCache);

        if (currCacheConfig.getAverageKeySize() == averageKeySize
            && currCacheConfig.getAverageValueSize() == averageValueSize
            && currCacheConfig.getMaxEntries() == newMaxEntries) {
          continue;
        }

        ChronicleMapCacheConfig newChronicleMapCacheConfig =
            makeChronicleMapConfig(
                currCache.getConfig(), newMaxEntries, averageKeySize, averageValueSize);

        updateOutputConfig(
            outputChronicleMapConfig,
            cacheName,
            averageKeySize,
            averageValueSize,
            newMaxEntries,
            currCache.getConfig().getMaxBloatFactor());

        if (!dryRun) {
          ChronicleMapCacheImpl<Object, Object> newCache =
              new ChronicleMapCacheImpl<>(
                  currCache.getCacheDefinition(), newChronicleMapCacheConfig);

          progressMonitor.beginTask(
              String.format("[%s] migrate content", cacheName), (int) currCache.size());

          currCache
              .getStore()
              .forEach(
                  (k, v) -> {
                    try {
                      newCache.putUnchecked(k, v);

                      progressMonitor.update(1);
                    } catch (Exception e) {
                      logger.atWarning().withCause(e).log(
                          "[%s] Could not migrate entry %s -> %s",
                          cacheName, k.getValue(), v.getValue());
                    }
                  });
        }
      }
    }

    return outputChronicleMapConfig;
  }

  private ImmutablePair<Long, Long> averageSizes(
      String cacheName,
      ConcurrentMap<KeyWrapper<Object>, TimedValue<Object>> store,
      ProgressMonitor progressMonitor) {
    long kAvg = 0;
    long vAvg = 0;

    if (store.isEmpty()) return ImmutablePair.of(kAvg, vAvg);

    progressMonitor.beginTask(
        String.format("[%s] calculate average key/value size", cacheName), store.size());

    int i = 1;
    for (Map.Entry<KeyWrapper<Object>, TimedValue<Object>> entry : store.entrySet()) {
      kAvg = kAvg + (serializedKeyLength(cacheName, entry.getKey()) - kAvg) / i;
      vAvg = vAvg + (serializedValueLength(cacheName, entry.getValue()) - vAvg) / i;
      progressMonitor.update(1);
    }
    progressMonitor.endTask();
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
      long newMaxEntries,
      long averageKeySize,
      long averageValueSize) {

    return configFactory.createWithValues(
        currentChronicleMapConfig.getConfigKey(),
        resolveNewFile(currentChronicleMapConfig.getCacheFile().getName()),
        currentChronicleMapConfig.getExpireAfterWrite(),
        currentChronicleMapConfig.getRefreshAfterWrite(),
        newMaxEntries,
        averageKeySize,
        averageValueSize,
        currentChronicleMapConfig.getMaxBloatFactor(),
        currentChronicleMapConfig.getPersistIndexEvery());
  }

  private long newMaxEntries(ChronicleMapCacheImpl<Object, Object> currentCache) {
    return getOptionalMaxEntries()
        .orElseGet(
            () -> {
              double percentageUsedAutoResizes = currentCache.percentageUsedAutoResizes();
              long currMaxEntries = currentCache.getConfig().getMaxEntries();

              long newMaxEntries = currMaxEntries;
              if (percentageUsedAutoResizes > PERCENTAGE_SIZE_INCREASE_THRESHOLD) {
                newMaxEntries = currMaxEntries * MAX_ENTRIES_MULTIPLIER;
              }
              logger.atInfo().log(
                  "Cache '%s' (maxEntries: %s) used %s%% of available space. new maxEntries will be: %s",
                  currentCache.name(), currMaxEntries, percentageUsedAutoResizes, newMaxEntries);
              return newMaxEntries;
            });
  }

  private File resolveNewFile(String currentFileName) {
    String newFileName =
        String.format(
            "%s%s%s.%s",
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Map<String, ChronicleMapCacheImpl<Object, Object>> getChronicleMapCaches() {
    return cacheMap.plugins().stream()
        .map(cacheMap::byPlugin)
        .flatMap(
            pluginCaches ->
                pluginCaches.entrySet().stream()
                    .map(entry -> ImmutablePair.of(entry.getKey(), entry.getValue().get())))
        .filter(
            pair ->
                pair.getValue() instanceof ChronicleMapCacheImpl
                    && ((ChronicleMapCacheImpl) pair.getValue()).diskStats().size() > 0)
        .filter(pair -> cacheNames.isEmpty() ? true : cacheNames.contains(pair.getKey()))
        .collect(
            Collectors.toMap(
                ImmutablePair::getKey, p -> (ChronicleMapCacheImpl<Object, Object>) p.getValue()));
  }
}
