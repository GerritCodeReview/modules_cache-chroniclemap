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

public class TuneChronicleMapCaches extends SshCommand {
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final Path cacheDir;

  @Inject
  TuneChronicleMapCaches(
      @GerritServerConfig Config cfg,
      SitePaths site,
      DynamicMap<Cache<?, ?>> cacheMap,
      ChronicleMapCacheConfig.Factory configFactory) {
    this.cacheMap = cacheMap;
    this.configFactory = configFactory;
    this.cacheDir = getCacheDir(site, cfg.getString("cache", null, "directory"));
  }

  @Override
  protected void run() throws Exception {
    TextProgressMonitor overallProgress = new TextProgressMonitor(stdout);
    Config outputChronicleMapConfig = new Config();

    Map<String, ChronicleMapCacheImpl<Object, Object>> chronicleMapCaches = getChronicleMapCaches();

    overallProgress.beginTask("Tuning chronicle-map caches", chronicleMapCaches.size());

    chronicleMapCaches.forEach(
        (cacheName, currCache) -> {
          TextProgressMonitor cacheProgress = new TextProgressMonitor(stdout);
          cacheProgress.beginTask(cacheName, (int) currCache.size());
          long averageKeySize = averageKeySize(cacheName, currCache.getStore());
          long averageValueSize = averageValueSize(cacheName, currCache.getStore());
          ChronicleMapCacheConfig newChronicleMapCacheConfig =
              makeChronicleMapConfig(currCache.getConfig(), averageKeySize, averageValueSize);

          updateOutputConfig(
              outputChronicleMapConfig,
              cacheName,
              averageKeySize,
              averageValueSize,
              currCache.getConfig().getMaxEntries(),
              currCache.getConfig().getMaxBloatFactor());
          try {
            ChronicleMapCacheImpl<Object, Object> newCache =
                new ChronicleMapCacheImpl<>(
                    currCache.getCacheDefinition(),
                    newChronicleMapCacheConfig,
                    null,
                    new DisabledMetricMaker());

            currCache
                .getStore()
                .forEach(
                    (k, v) -> {
                      cacheProgress.update(1);
                      newCache.putUnchecked(k, v);
                    });
          } catch (IOException e) {
            stderr.println(String.format("Could not create new cache %s", cacheName));
            overallProgress.update(1);
          } finally {
            overallProgress.endTask();
          }
        });

    stdout.println();
    stdout.println("****************************");
    stdout.println("** Chronicle-map template **");
    stdout.println("****************************");
    stdout.println();
    stdout.println(outputChronicleMapConfig.toText());
  }

  private static long averageKeySize(
      String cacheName, ConcurrentMap<KeyWrapper<Object>, TimedValue<Object>> concurrentMap) {
    if (concurrentMap.size() == 0) return 0;

    long keysTotalSize =
        concurrentMap.keySet().stream()
            .mapToInt(keyWrapper -> serializedKeyLength(cacheName, keyWrapper))
            .asLongStream()
            .sum();
    return keysTotalSize / concurrentMap.size();
  }

  private static long averageValueSize(
      String cacheName, ConcurrentMap<KeyWrapper<Object>, TimedValue<Object>> concurrentMap) {
    if (concurrentMap.size() == 0) return 0;

    long valuesTotalSize =
        concurrentMap.values().stream()
            .mapToInt(timedValue -> serializedValueLength(cacheName, timedValue))
            .asLongStream()
            .sum();
    return valuesTotalSize / concurrentMap.size();
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

    stdout.println(
        String.format(
            "[%s] key %d bytes. Value: %d bytes for file: %s",
            currentChronicleMapConfig.getConfigKey(),
            averageKeySize,
            averageValueSize,
            computeNewFile(currentChronicleMapConfig.getPersistedFile())));

    return configFactory.createWithValues(
        currentChronicleMapConfig.getConfigKey(),
        computeNewFile(currentChronicleMapConfig.getPersistedFile()),
        currentChronicleMapConfig.getExpireAfterWrite(),
        currentChronicleMapConfig.getRefreshAfterWrite(),
        currentChronicleMapConfig.getMaxEntries(),
        averageKeySize,
        averageValueSize,
        currentChronicleMapConfig.getMaxBloatFactor());
  }

  private File computeNewFile(File currentFile) {
    String currentFileName = currentFile.getName();

    String newFileName =
        String.format(
            "%s_%s.%s",
            FilenameUtils.getBaseName(currentFileName),
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
            pair -> pair.getRight() instanceof ChronicleMapCacheImpl && pair.getRight().size() > 0)
        .collect(
            Collectors.toMap(
                ImmutablePair::getLeft, p -> (ChronicleMapCacheImpl<Object, Object>) p.getRight()));
  }
}
