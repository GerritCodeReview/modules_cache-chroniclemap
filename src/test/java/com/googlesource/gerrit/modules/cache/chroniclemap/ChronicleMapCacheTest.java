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
import com.google.gerrit.acceptance.WaitUtil;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import net.openhft.chronicle.bytes.Bytes;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ChronicleMapCacheTest {
  @Inject MetricMaker metricMaker;
  @Inject MetricRegistry metricRegistry;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private SitePaths sitePaths;
  private StoredConfig gerritConfig;

  private final String cacheDirectory = ".";

  @Before
  public void setUp() throws Exception {
    sitePaths = new SitePaths(temporaryFolder.newFolder().toPath());
    Files.createDirectories(sitePaths.etc_dir);

    gerritConfig =
        new FileBasedConfig(
            sitePaths.resolve("etc").resolve("gerrit.config").toFile(), FS.DETECTED);
    gerritConfig.load();
    gerritConfig.setString("cache", null, "directory", cacheDirectory);
    gerritConfig.save();

    setupMetrics();
  }

  public void setupMetrics() {
    Injector injector = Guice.createInjector(new DropWizardMetricMaker.ApiModule());

    LifecycleManager mgr = new LifecycleManager();
    mgr.add(injector);
    mgr.start();

    injector.injectMembers(this);
  }

  @Test
  public void getIfPresentShouldReturnNullWhenThereisNoCachedValue() throws Exception {
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
    gerritConfig.setString("cache", null, "directory", "cache");
    gerritConfig.save();
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
    cache.getIfPresent("foo");

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
  public void shouldIncreaseLoadExceptionCountWhenNoLoaderIsAvailable() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithoutLoader();

    assertThrows(UnsupportedOperationException.class, () -> cache.get("foo"));

    assertThat(cache.stats().loadExceptionCount()).isEqualTo(1);
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
        newCache(true, null, Duration.ofSeconds(1), null, 1);
    cache.put("foo", "some-stale-value");
    Thread.sleep(1010); // Allow cache entry to expire
    assertThat(cache.getIfPresent("foo")).isNull();
  }

  @Test
  public void getShouldRefreshValueWhenExpired() throws Exception {
    String newCachedValue = UUID.randomUUID().toString();
    ChronicleMapCacheImpl<String, String> cache =
        newCache(true, newCachedValue, null, Duration.ofSeconds(1), 1);
    cache.put("foo", "some-stale-value");
    Thread.sleep(1010); // Allow cache to be flagged as needing refresh
    assertThat(cache.get("foo")).isEqualTo(newCachedValue);
  }

  @Test
  public void shouldPruneExpiredValues() throws Exception {
    ChronicleMapCacheImpl<String, String> cache =
        newCache(true, null, Duration.ofSeconds(1), null, 1);
    cache.put("foo1", "some-stale-value1");
    cache.put("foo2", "some-stale-value1");
    Thread.sleep(1010); // Allow cache entries to expire
    cache.put("foo3", "some-fresh-value3");
    cache.prune();

    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.get("foo3")).isEqualTo("some-fresh-value3");
  }

  @Test
  public void shouldLoadNewValueAfterBeingInvalidated() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(cachedValue);
    cache.put("foo", "old-value");
    cache.invalidate("foo");

    assertThat(cache.size()).isEqualTo(0);
    assertThat(cache.get("foo")).isEqualTo(cachedValue);
  }

  @Test
  public void shouldClearAllEntriesWhenInvalidateAll() throws Exception {
    final ChronicleMapCacheImpl<String, String> cache = newCacheWithoutLoader();
    cache.put("foo1", "some-value");
    cache.put("foo2", "some-value");

    cache.invalidateAll();

    assertThat(cache.size()).isEqualTo(0);
  }

  @Test
  public void shouldEvictOldestElementInCacheWhenIsNeverAccessed() throws Exception {
    final String fooValue = "foo";

    gerritConfig.setInt("cache", "foo", "maxEntries", 2);
    gerritConfig.setInt("cache", "foo", "percentageHotKeys", 10);
    gerritConfig.setInt("cache", "foo", "avgKeySize", "foo1".getBytes().length);
    gerritConfig.setInt("cache", "foo", "avgValueSize", valueSize(fooValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(fooValue);
    cache.put("foo1", fooValue);
    cache.put("foo2", fooValue);

    cache.prune();

    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.get("foo2")).isNotNull();
  }

  @Test
  public void shouldEvictRecentlyInsertedElementInCacheWhenOldestElementIsAccessed()
      throws Exception {
    final String fooValue = "foo";
    gerritConfig.setInt("cache", "foo", "maxEntries", 2);
    gerritConfig.setInt("cache", "foo", "percentageHotKeys", 10);
    gerritConfig.setInt("cache", "foo", "avgKeySize", "foo1".getBytes().length);
    gerritConfig.setInt("cache", "foo", "avgValueSize", valueSize(fooValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithLoader(fooValue);
    cache.put("foo1", fooValue);
    cache.put("foo2", fooValue);

    cache.get("foo1");

    cache.prune();

    assertThat(cache.size()).isEqualTo(1);
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
    assertThat(cache.runningOutOfFreeSpace()).isTrue();

    cache.prune();

    assertThat(cache.runningOutOfFreeSpace()).isFalse();
  }

  @Test
  public void shouldTriggerPercentageFreeMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    String freeSpaceMetricName = "cache/chroniclemap/percentage_free_space_" + cachedValue;
    gerritConfig.setInt("cache", cachedValue, "maxEntries", 2);
    gerritConfig.setInt("cache", cachedValue, "avgKeySize", cachedValue.getBytes().length);
    gerritConfig.setInt("cache", cachedValue, "avgValueSize", valueSize(cachedValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(cachedValue);

    assertThat(getMetric(freeSpaceMetricName).getValue()).isEqualTo(100);

    cache.put(cachedValue, cachedValue);

    WaitUtil.waitUntil(
        () -> (long) getMetric(freeSpaceMetricName).getValue() < 100, Duration.ofSeconds(2));
  }

  @Test
  public void shouldTriggerRemainingAutoResizeMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    String autoResizeMetricName = "cache/chroniclemap/remaining_autoresizes_" + cachedValue;
    gerritConfig.setInt("cache", cachedValue, "maxEntries", 2);
    gerritConfig.setInt("cache", cachedValue, "avgKeySize", cachedValue.getBytes().length);
    gerritConfig.setInt("cache", cachedValue, "avgValueSize", valueSize(cachedValue));
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(cachedValue);

    assertThat(getMetric(autoResizeMetricName).getValue()).isEqualTo(1);

    cache.put(cachedValue + "1", cachedValue);
    cache.put(cachedValue + "2", cachedValue);
    cache.put(cachedValue + "3", cachedValue);

    WaitUtil.waitUntil(
        () -> (int) getMetric(autoResizeMetricName).getValue() == 0, Duration.ofSeconds(2));
  }

  @Test
  public void shouldTriggerHotKeysCapacityCacheMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    int percentageHotKeys = 60;
    int maxEntries = 10;
    int expectedCapacity = 6;
    String hotKeysCapacityMetricName = "cache/chroniclemap/hot_keys_capacity_" + cachedValue;
    gerritConfig.setInt("cache", cachedValue, "maxEntries", maxEntries);
    gerritConfig.setInt("cache", cachedValue, "percentageHotKeys", percentageHotKeys);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(cachedValue);

    assertThat(getMetric(hotKeysCapacityMetricName).getValue()).isEqualTo(expectedCapacity);
  }

  @Test
  public void shouldTriggerHotKeysSizeCacheMetric() throws Exception {
    String cachedValue = UUID.randomUUID().toString();
    int percentageHotKeys = 30;
    int maxEntries = 10;
    int maxHotKeyCapacity = 3;
    final Duration METRIC_TRIGGER_TIMEOUT = Duration.ofSeconds(2);
    String hotKeysSizeMetricName = "cache/chroniclemap/hot_keys_size_" + cachedValue;
    gerritConfig.setInt("cache", cachedValue, "maxEntries", maxEntries);
    gerritConfig.setInt("cache", cachedValue, "percentageHotKeys", percentageHotKeys);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(cachedValue);

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
    String hotKeysSizeMetricName = "cache/chroniclemap/hot_keys_size_" + cachedValue;
    gerritConfig.setInt("cache", cachedValue, "maxEntries", maxEntries);
    gerritConfig.setInt("cache", cachedValue, "percentageHotKeys", percentageHotKeys);
    gerritConfig.save();

    ChronicleMapCacheImpl<String, String> cache = newCacheWithMetrics(cachedValue);

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
    String hotKeyCapacityMetricName = "cache/chroniclemap/hot_keys_capacity_" + sanitized;

    newCacheWithMetrics(cacheName);

    getMetric(hotKeySizeMetricName);
    getMetric(percentageFreeMetricName);
    getMetric(autoResizeMetricName);
    getMetric(hotKeyCapacityMetricName);
  }

  private int valueSize(String value) {
    final TimedValueMarshaller<String> marshaller =
        new TimedValueMarshaller<>(StringCacheSerializer.INSTANCE);

    Bytes<ByteBuffer> out = Bytes.elasticByteBuffer();
    marshaller.write(out, new TimedValue<>(value));
    return out.toByteArray().length;
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithMetrics(String cachedValue)
      throws IOException {
    return newCache(true, cachedValue, null, null, null, null, 1, metricMaker);
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithMetrics(
      String cachedValue,
      CacheSerializer<String> keySerializer,
      CacheSerializer<String> valueSerializer)
      throws IOException {
    return newCache(true, cachedValue, null, null, null, null, 1, new DisabledMetricMaker());
  }

  private ChronicleMapCacheImpl<String, String> newCache(
      Boolean withLoader,
      @Nullable String cachedValue,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite,
      Integer version)
      throws IOException {
    return newCache(
        withLoader,
        cachedValue,
        expireAfterWrite,
        refreshAfterWrite,
        null,
        null,
        version,
        new DisabledMetricMaker());
  }

  private ChronicleMapCacheImpl<String, String> newCache(
      Boolean withLoader,
      @Nullable String cachedValue,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite,
      @Nullable CacheSerializer<String> keySerializer,
      @Nullable CacheSerializer<String> valueSerializer,
      Integer version,
      MetricMaker metricMaker)
      throws IOException {
    TestPersistentCacheDef cacheDef =
        new TestPersistentCacheDef(cachedValue, keySerializer, valueSerializer);

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

    return new ChronicleMapCacheImpl<>(
        cacheDef, config, withLoader ? cacheDef.loader() : null, metricMaker);
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithLoader(@Nullable String cachedValue)
      throws IOException {
    return newCache(true, cachedValue, null, null, 1);
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithLoader() throws IOException {
    return newCache(true, null, null, null, 1);
  }

  private ChronicleMapCacheImpl<String, String> newCacheVersion(int version) throws IOException {
    return newCache(true, null, null, null, version);
  }

  private ChronicleMapCacheImpl<String, String> newCacheWithoutLoader() throws IOException {
    return newCache(false, null, null, null, 1);
  }

  private <V> Gauge<V> getMetric(String name) {
    @SuppressWarnings("unchecked")
    Gauge<V> gauge = (Gauge<V>) metricRegistry.getMetrics().get(name);
    assertWithMessage(name).that(gauge).isNotNull();
    return gauge;
  }
}
