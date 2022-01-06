// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.WaitUtil;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import net.openhft.chronicle.bytes.Bytes;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

@UseLocalDisk // Needed to have Gerrit with DropWizardMetricMaker enabled
public class ChronicleMapCacheTest extends AbstractDaemonTest {
  private static final DisabledMetricMaker WITHOUT_METRICS = new DisabledMetricMaker();
  @Inject MetricMaker metricMaker;
  @Inject MetricRegistry metricRegistry;
  @Inject MemoryCacheFactory memCacheFactory;

  @Inject SitePaths sitePaths;
  private StoredConfig gerritConfig;

  private final String cacheDirectory = ".";

  String testCacheName = "test-cache";

  @Before
  public void setUp() throws Exception {
    CacheSerializers.registerCacheKeySerializer(testCacheName, StringCacheSerializer.INSTANCE);
    CacheSerializers.registerCacheValueSerializer(testCacheName, StringCacheSerializer.INSTANCE);

    gerritConfig =
        new FileBasedConfig(
            sitePaths.resolve("etc").resolve("gerrit.config").toFile(), FS.DETECTED);
    gerritConfig.load();
    gerritConfig.setString("cache", null, "directory", cacheDirectory);
    gerritConfig.save();
  }

  @Override
  protected void beforeTest(Description description) throws Exception {
    super.beforeTest(description);
    testCacheName += description.getMethodName();
  }

  @Test
  public void getIfPresentShouldReturnNullWhenThereIsNoCachedValue() throws Exception {
    assertThat(newCacheWithLoader(null).getIfPresent("foo")).isNull();
  }

  @Test
  public void getIfPresentShouldReturnNullWhenThereCacheHasADifferentVersion() throws Exception {
    gerritConfig.setString("cache", null, "directory", "cache");
    gerritConfig.save();
    final ChronicleMapCacheImpl<String, String> cacheV1 = newCacheVersion(1);

    cacheV1.put("foo", "value version 1");
    cacheV1.close();

    final ChronicleMapCacheImpl<String, String> cacheV2 = newCacheVersion(2);
    assertThat(cacheV2.getIfPresent("foo")).isNull();
  }

  @Test
  public void getWithLoaderShouldPopulateTheCache() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader();

    assertThat(cache.get("foo", () -> cachedValue)).isEqualTo(cachedValue);
    assertThat(cache.get("foo")).isEqualTo(cachedValue);
  }

  @Test
  public void getShouldRetrieveTheValueViaTheLoader() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(cachedValue);

    assertThat(cache.get("foo")).isEqualTo(cachedValue);
  }

  @Test
  public void getShouldRetrieveANewValueWhenCacheHasADifferentVersion() throws Exception {
    gerritConfig.setString("cache", null, "directory", "cache");
    gerritConfig.save();
    final ChronicleMapCacheImpl<String, String> cacheV1 = newCacheVersion(1);

    cacheV1.put("foo", "value version 1");
    cacheV1.close();

    final ChronicleMapCacheImpl<String, String> cacheV2 = newCacheVersion(2);

    final String v2Value = "value version 2";
    assertThat(cacheV2.get("foo", () -> v2Value)).isEqualTo(v2Value);
  }

  @Test
  public void getShouldRetrieveCachedValueWhenCacheHasSameVersion() throws Exception {
    int cacheVersion = 2;
    final ChronicleMapCacheImpl<String, String> cache = newCacheVersion(cacheVersion);

    final String originalValue = "value 1";
    cache.put("foo", originalValue);
    cache.close();

    final ChronicleMapCacheImpl<String, String> newCache = newCacheVersion(cacheVersion);

    final String newValue = "value 2";
    assertThat(newCache.get("foo", () -> newValue)).isEqualTo(originalValue);
  }

  @Test
  public void getShoudThrowWhenNoLoaderHasBeenProvided() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithoutLoader();

    UnsupportedOperationException thrown =
        assertThrows(UnsupportedOperationException.class, () -> cache.get("foo"));
    assertThat(thrown).hasMessageThat().contains("Could not load value");
  }

  @Test
  public void shouldIncreaseMissCountWhenValueIsNotInCache() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader();

    cache.getIfPresent("foo");
    assertThat(cache.stats().hitCount()).isEqualTo(0);
    assertThat(cache.stats().missCount()).isEqualTo(1);
  }

  @Test
  public void shouldIncreaseHitCountWhenValueIsInCache() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader();

    cache.put("foo", "bar");
    assertThat(cache.getIfPresent("foo")).isEqualTo("bar");

    assertThat(cache.stats().hitCount()).isEqualTo(1);
    assertThat(cache.stats().missCount()).isEqualTo(0);
  }

  @Test
  public void shouldIncreaseLoadSuccessCountWhenValueIsLoadedFromCacheDefinitionLoader()
      throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader();

    cache.get("foo");

    assertThat(cache.stats().loadSuccessCount()).isEqualTo(1);
    assertThat(cache.stats().loadExceptionCount()).isEqualTo(0);
  }

  @Test
  public void valueShouldBeCachedAfterPut() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader();

    cache.put("foo", cachedValue);
    assertThat(cache.get("foo")).isEqualTo(cachedValue);
  }

  @Test
  public void shouldNotIncreaseLoadExceptionCountWhenNoLoaderIsAvailable() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithoutLoader();

    assertThrows(UnsupportedOperationException.class, () -> cache.get("foo"));

    assertThat(cache.stats().loadExceptionCount()).isEqualTo(0);
    assertThat(cache.stats().loadSuccessCount()).isEqualTo(0);
  }

  @Test
  public void shouldIncreaseLoadExceptionCountWhenLoaderThrows() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader();

    assertThrows(
        ExecutionException.class,
        () ->
            cache.get(
                "foo",
                () -> {
                  throw new Exception("Boom!");
                }));

    assertThat(cache.stats().loadExceptionCount()).isEqualTo(1);
    assertThat(cache.stats().loadSuccessCount()).isEqualTo(0);
  }

  @Test
  public void shouldIncreaseLoadSuccessCountWhenValueIsLoadedFromCallableLoader() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(null);

    cache.get("foo", () -> "some-value");

    assertThat(cache.stats().loadSuccessCount()).isEqualTo(1);
    assertThat(cache.stats().loadExceptionCount()).isEqualTo(0);
  }

  @Test
  public void getIfPresentShouldReturnNullWhenValueIsExpired() throws Exception {
    ChronicleMapCacheImpl<String, String> cache =
        newCache(true, testCacheName, null, Duration.ofSeconds(1), null, 1, WITHOUT_METRICS);
    cache.put("foo", "some-stale-value");
    Thread.sleep(1010); // Allow cache entry to expire
    assertThat(cache.getIfPresent("foo")).isNull();
  }

  @Test
  public void getShouldRefreshValueWhenExpired() throws Exception {
    String newCachedValue = UUID.randomUUID().toString();
    String staleValue = "some-stale-value";

    ChronicleMapCacheImpl<String, String> cache =
        newCache(
            true, testCacheName, newCachedValue, null, Duration.ofSeconds(1), 1, WITHOUT_METRICS);
    cache.put("foo", staleValue);
    assertThat(cache.get("foo")).isEqualTo(staleValue);

    // Wait until the cache is asynchronously refreshed
    WaitUtil.waitUntil(
        () -> {
          try {
            return cache.get("foo").equals(newCachedValue);
          } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
          }
        },
        Duration.ofSeconds(2));
  }

  @Test
  public void shouldPruneExpiredValues() throws Exception {
    ChronicleMapCacheImpl<String, String> cache =
        newCache(true, testCacheName, null, Duration.ofSeconds(1), null, 1, WITHOUT_METRICS);
    cache.put("foo1", "some-stale-value1");
    cache.put("foo2", "some-stale-value1");
    Thread.sleep(1010); // Allow cache entries to expire
    cache.put("foo3", "some-fresh-value3");
    cache.prune();

    assertThat(cache.diskStats().size()).isEqualTo(1);
    assertThat(cache.get("foo3")).isEqualTo("some-fresh-value3");
  }

  @Test
  public void shouldLoadNewValueAfterBeingInvalidated() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(cachedValue);
    cache.put("foo", "old-value");
    cache.invalidate("foo");

    assertThat(cache.size()).isEqualTo(0);
    assertThat(cache.diskStats().size()).isEqualTo(0);
    assertThat(cache.get("foo")).isEqualTo(cachedValue);
  }

  @Test
  public void shouldClearAllEntriesWhenInvalidateAll() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithoutLoader();
    cache.put("foo1", "some-value");
    cache.put("foo2", "some-value");

    cache.invalidateAll();

    assertThat(cache.size()).isEqualTo(0);
    assertThat(cache.diskStats().size()).isEqualTo(0);
  }

  @Test
  public void shouldEvictOldestElementInCacheWhenIsNeverAccessed() throws Exception {
    final String fooValue = "foo";

    gerritConfig.setInt("cache", testCacheName, "maxEntries", 2);
    gerritConfig.setInt("cache", testCacheName, "percentageHotKeys", 10);
    gerritConfig.setInt("cache", testCacheName, "avgKeySize", "foo1".getBytes().length);
    gerritConfig.setInt("cache", testCacheName, "avgValueSize", valueSize(fooValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(fooValue);
    cache.put("foo1", fooValue);
    cache.put("foo2", fooValue);

    cache.prune();

    assertThat(cache.diskStats().size()).isEqualTo(1);
    assertThat(cache.get("foo2")).isNotNull();
  }

  @Test
  public void shouldEvictRecentlyInsertedElementInCacheWhenOldestElementIsAccessed()
      throws Exception {
    final String fooValue = "foo";
    gerritConfig.setInt("cache", testCacheName, "maxEntries", 2);
    gerritConfig.setInt("cache", testCacheName, "percentageHotKeys", 10);
    gerritConfig.setInt("cache", testCacheName, "avgKeySize", "foo1".getBytes().length);
    gerritConfig.setInt("cache", testCacheName, "avgValueSize", valueSize(fooValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(fooValue);
    cache.put("foo1", fooValue);
    cache.put("foo2", fooValue);

    cache.get("foo1");

    cache.prune();

    assertThat(cache.diskStats().size()).isEqualTo(1);
    assertThat(cache.get("foo1")).isEqualTo(fooValue);
  }

  @Test
  public void shouldEvictEntriesUntilFreeSpaceIsRecovered() throws Exception {
    final int uuidSize = valueSize(UUID.randomUUID().toString());
    gerritConfig.setInt("cache", "foo", "maxEntries", 50);
    gerritConfig.setInt("cache", "foo", "percentageHotKeys", 10);
    gerritConfig.setInt("cache", "foo", "avgKeySize", uuidSize);
    gerritConfig.setInt("cache", "foo", "avgValueSize", uuidSize);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader();
    while (!cache.runningOutOfFreeSpace()) {
      cache.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
  }

  @Test
  public void shouldRecoverWhenPutFailsBecauseEntryIsTooBig() throws Exception {
    String key = UUID.randomUUID().toString();
    String value = UUID.randomUUID().toString();
    int uuidSize = valueSize(value);
    gerritConfig.setInt("cache", testCacheName, "maxEntries", 1);
    gerritConfig.setInt("cache", testCacheName, "maxBloatFactor", 1);
    gerritConfig.setInt("cache", testCacheName, "avgKeySize", uuidSize / 2);
    gerritConfig.setInt("cache", testCacheName, "avgValueSize", uuidSize / 2);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithoutLoader();

    cache.put(key, value);

    assertThat(cache.getStore().size()).isEqualTo(0);
    assertThat(cache.getIfPresent(key)).isEqualTo(value);
  }

  @Test
  public void shouldRecoverWhenPutFailsBecauseCacheCannotExpand() throws Exception {
    String key = UUID.randomUUID().toString();
    String value = UUID.randomUUID().toString();
    int uuidSize = valueSize(value);
    gerritConfig.setInt("cache", testCacheName, "maxEntries", 1);
    gerritConfig.setInt("cache", testCacheName, "maxBloatFactor", 1);
    gerritConfig.setInt("cache", testCacheName, "avgKeySize", uuidSize);
    gerritConfig.setInt("cache", testCacheName, "avgValueSize", uuidSize);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithoutLoader();

    cache.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    cache.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    cache.put(key, value);

    assertThat(cache.getStore().size()).isEqualTo(2);
    assertThat(cache.getIfPresent(key)).isEqualTo(value);
  }

  @Test
  public void shouldTriggerPercentageFreeMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    String freeSpaceMetricName = "cache/chroniclemap/percentage_free_space_" + testCacheName;
    gerritConfig.setInt("cache", testCacheName, "maxEntries", 2);
    gerritConfig.setInt("cache", testCacheName, "avgKeySize", cachedValue.getBytes().length);
    gerritConfig.setInt("cache", testCacheName, "avgValueSize", valueSize(cachedValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(testCacheName, cachedValue);

    assertThat(getMetric(freeSpaceMetricName).getValue()).isEqualTo(100);

    cache.put(cachedValue, cachedValue);

    WaitUtil.waitUntil(
        () -> (long) getMetric(freeSpaceMetricName).getValue() < 100, Duration.ofSeconds(2));
  }

  @Test
  public void shouldTriggerRemainingAutoResizeMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    String autoResizeMetricName = "cache/chroniclemap/remaining_autoresizes_" + testCacheName;
    gerritConfig.setInt("cache", testCacheName, "maxEntries", 2);
    gerritConfig.setInt("cache", testCacheName, "avgKeySize", cachedValue.getBytes().length);
    gerritConfig.setInt("cache", testCacheName, "avgValueSize", valueSize(cachedValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(testCacheName, cachedValue);

    assertThat(getMetric(autoResizeMetricName).getValue()).isEqualTo(1);

    cache.put(cachedValue + "1", cachedValue);
    cache.put(cachedValue + "2", cachedValue);
    cache.put(cachedValue + "3", cachedValue);

    WaitUtil.waitUntil(
        () -> (int) getMetric(autoResizeMetricName).getValue() == 0, Duration.ofSeconds(2));
  }

  @Test
  public void shouldTriggerMaxAutoResizeMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    String maxAutoResizeMetricName = "cache/chroniclemap/max_autoresizes_" + testCacheName;
    gerritConfig.setInt("cache", testCacheName, "maxEntries", 2);
    gerritConfig.setInt("cache", testCacheName, "avgKeySize", cachedValue.getBytes().length);
    gerritConfig.setInt("cache", testCacheName, "avgValueSize", valueSize(cachedValue));
    gerritConfig.setInt("cache", testCacheName, "maxBloatFactor", 3);
    gerritConfig.save();

    newCacheWithMetrics(testCacheName, cachedValue);

    assertThat(getMetric(maxAutoResizeMetricName).getValue()).isEqualTo(3);
  }

  @Test
  public void shouldTriggerHotKeysCapacityCacheMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    int percentageHotKeys = 60;
    int maxEntries = 10;
    int expectedCapacity = 6;
    String hotKeysCapacityMetricName = "cache/chroniclemap/hot_keys_capacity_" + testCacheName;
    gerritConfig.setInt("cache", testCacheName, "maxEntries", maxEntries);
    gerritConfig.setInt("cache", testCacheName, "percentageHotKeys", percentageHotKeys);
    gerritConfig.save();

    newCacheWithMetrics(testCacheName, cachedValue);

    assertThat(getMetric(hotKeysCapacityMetricName).getValue()).isEqualTo(expectedCapacity);
  }

  @Test
  public void shouldTriggerHotKeysSizeCacheMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    int percentageHotKeys = 30;
    int maxEntries = 10;
    int maxHotKeyCapacity = 3;
    final Duration METRIC_TRIGGER_TIMEOUT = Duration.ofSeconds(2);
    String hotKeysSizeMetricName = "cache/chroniclemap/hot_keys_size_" + testCacheName;
    gerritConfig.setInt("cache", testCacheName, "maxEntries", maxEntries);
    gerritConfig.setInt("cache", testCacheName, "percentageHotKeys", percentageHotKeys);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(testCacheName, cachedValue);

    assertThat(getMetric(hotKeysSizeMetricName).getValue()).isEqualTo(0);

    for (int i = 0; i < maxHotKeyCapacity; i++) {
      cache.put(cachedValue + i, cachedValue);
    }

    WaitUtil.waitUntil(
        () -> (int) getMetric(hotKeysSizeMetricName).getValue() == maxHotKeyCapacity,
        METRIC_TRIGGER_TIMEOUT);

    cache.put(cachedValue + maxHotKeyCapacity + 1, cachedValue);

    assertThrows(
        InterruptedException.class,
        () ->
            WaitUtil.waitUntil(
                () -> (int) getMetric(hotKeysSizeMetricName).getValue() > maxHotKeyCapacity,
                METRIC_TRIGGER_TIMEOUT));
  }

  @Test
  public void shouldResetHotKeysWhenInvalidateAll() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    int percentageHotKeys = 30;
    int maxEntries = 10;
    int maxHotKeyCapacity = 3;
    final Duration METRIC_TRIGGER_TIMEOUT = Duration.ofSeconds(2);
    String hotKeysSizeMetricName = "cache/chroniclemap/hot_keys_size_" + testCacheName;
    gerritConfig.setInt("cache", testCacheName, "maxEntries", maxEntries);
    gerritConfig.setInt("cache", testCacheName, "percentageHotKeys", percentageHotKeys);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(testCacheName, cachedValue);

    for (int i = 0; i < maxHotKeyCapacity; i++) {
      cache.put(cachedValue + i, cachedValue);
    }

    WaitUtil.waitUntil(
        () -> (int) getMetric(hotKeysSizeMetricName).getValue() == maxHotKeyCapacity,
        METRIC_TRIGGER_TIMEOUT);

    cache.invalidateAll();

    WaitUtil.waitUntil(
        () -> (int) getMetric(hotKeysSizeMetricName).getValue() == 0, METRIC_TRIGGER_TIMEOUT);
  }

  @Test
  public void shouldSanitizeUnwantedCharsInMetricNames() throws Exception {
    String cacheName = "very+confusing.cache#name";
    String sanitized = "very_confusing_cache_name";
    String hotKeySizeMetricName = "cache/chroniclemap/hot_keys_size_" + sanitized;
    String percentageFreeMetricName = "cache/chroniclemap/percentage_free_space_" + sanitized;
    String autoResizeMetricName = "cache/chroniclemap/remaining_autoresizes_" + sanitized;
    String maxAutoResizeMetricName = "cache/chroniclemap/max_autoresizes_" + sanitized;
    String hotKeyCapacityMetricName = "cache/chroniclemap/hot_keys_capacity_" + sanitized;

    newCacheWithMetrics(cacheName, null);

    getMetric(hotKeySizeMetricName);
    getMetric(percentageFreeMetricName);
    getMetric(autoResizeMetricName);
    getMetric(maxAutoResizeMetricName);
    getMetric(hotKeyCapacityMetricName);
  }

  private int valueSize(String value) {
    final TimedValueMarshaller<String> marshaller = new TimedValueMarshaller<>(testCacheName);

    Bytes<ByteBuffer> out = Bytes.elasticByteBuffer();
    marshaller.write(out, new TimedValue<>(value));
    return out.toByteArray().length;
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithMetrics(
      String cacheName, @Nullable String cachedValue) {
    return newCache(true, cacheName, cachedValue, null, null, null, null, 1, metricMaker);
  }

  private ChronicleMapCacheImpl<String, String> newCache(
      Boolean withLoader,
      String cacheName,
      @Nullable String loadedValue,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite,
      Integer version,
      MetricMaker metricMaker) {
    return newCache(
        withLoader,
        cacheName,
        loadedValue,
        expireAfterWrite,
        refreshAfterWrite,
        null,
        null,
        version,
        metricMaker);
  }

  private ChronicleMapCacheImpl<String, String> newCache(
      Boolean withLoader,
      String cacheName,
      @Nullable String cachedValue,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite,
      @Nullable CacheSerializer<String> keySerializer,
      @Nullable CacheSerializer<String> valueSerializer,
      Integer version,
      MetricMaker metricMaker) {
    TestPersistentCacheDef cacheDef =
        new TestPersistentCacheDef(
            cacheName, cachedValue, keySerializer, valueSerializer, withLoader, expireAfterWrite);

    File persistentFile =
        ChronicleMapCacheFactory.fileName(
            sitePaths.site_path.resolve(cacheDirectory), cacheDef.name(), version);

    ChronicleMapCacheConfig config =
        new ChronicleMapCacheConfig(
            gerritConfig,
            cacheDef.configKey(),
            persistentFile,
            expireAfterWrite != null ? expireAfterWrite : Duration.ZERO,
            refreshAfterWrite != null ? refreshAfterWrite : Duration.ZERO);

    ChronicleMapCacheFactory cacheFactory =
        new ChronicleMapCacheFactory(
            memCacheFactory, new Config(), sitePaths, null, null, metricMaker);

    if (withLoader) {
      return (ChronicleMapCacheImpl<String, String>)
          cacheFactory.build(
              cacheDef, cacheDef.loader(), CacheBackend.CAFFEINE, config, metricMaker);
    }
    return (ChronicleMapCacheImpl<String, String>)
        cacheFactory.build(cacheDef, CacheBackend.CAFFEINE, config, metricMaker);
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithLoader(@Nullable String loadedValue) {
    return newCache(true, testCacheName, loadedValue, null, null, 1, metricMaker);
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithLoader() {
    return newCache(true, testCacheName, null, null, null, 1, metricMaker);
  }

  private ChronicleMapCacheImpl<String, String> newCacheVersion(int version) {
    return newCache(true, testCacheName, null, null, null, version, WITHOUT_METRICS);
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithoutLoader() {
    return newCache(false, testCacheName, null, null, null, 1, metricMaker);
  }

  private <V> Gauge<V> getMetric(String name) {
    @SuppressWarnings("unchecked")
    Gauge<V> gauge = (Gauge<V>) metricRegistry.getMetrics().get(name);
    assertWithMessage(name).that(gauge).isNotNull();
    return gauge;
  }
}
