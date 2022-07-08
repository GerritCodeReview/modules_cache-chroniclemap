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
import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults.DEFAULT_AVG_KEY_SIZE;
import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults.DEFAULT_AVG_VALUE_SIZE;
import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults.DEFAULT_MAX_BLOAT_FACTOR;
import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults.DEFAULT_MAX_ENTRIES;
import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults.DEFAULT_PERCENTAGE_FREE_SPACE_EVICTION_THRESHOLD;

import com.google.gerrit.server.config.SitePaths;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ChronicleMapCacheConfigTest {

  private final String cacheDirectory = ".";
  private final String cacheName = "foobar-cache";
  private final String cacheKey = "foobar-cache-key";
  private final int version = 1;
  private final Duration expireAfterWrite = Duration.ofSeconds(10_000);
  private final Duration refreshAfterWrite = Duration.ofSeconds(20_000);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private SitePaths sitePaths;
  private StoredConfig gerritConfig;

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
  }

  @Test
  public void shouldProvideCacheFile() throws Exception {
    assertThat(
            configUnderTest(gerritConfig)
                .getCacheFile()
                .toPath()
                .getParent()
                .toRealPath()
                .toString())
        .isEqualTo(sitePaths.resolve(cacheDirectory).toRealPath().toString());
  }

  @Test
  public void shouldProvideIndexFileThatIsRelatedToCacheFile() {
    ChronicleMapCacheConfig config = configUnderTest(gerritConfig);
    File cacheFile = config.getCacheFile();
    File indexFile = config.getIndexFile();

    assertThat(indexFile.getParentFile()).isEqualTo(cacheFile.getParentFile());
    String cacheFileName = cacheFile.getName();
    String cacheFileExt = ".dat";
    assertThat(indexFile.getName())
        .isEqualTo(
            String.format(
                "%s_idx%s",
                cacheFileName.substring(0, cacheFileName.indexOf(cacheFileExt)), cacheFileExt));
  }

  @Test
  public void shouldProvideConfiguredMaxEntriesWhenDefined() throws Exception {
    long maxEntries = 10;
    gerritConfig.setLong("cache", cacheKey, "maxEntries", maxEntries);
    gerritConfig.save();

    assertThat(configUnderTest(gerritConfig).getMaxEntries()).isEqualTo(maxEntries);
  }

  @Test
  public void shouldProvideDefaultMaxEntriesWhenNotConfigured() throws Exception {
    assertThat(configUnderTest(gerritConfig).getMaxEntries()).isEqualTo(DEFAULT_MAX_ENTRIES);
  }

  @Test
  public void shouldProvideAverageKeySizeWhenConfigured() throws Exception {
    long averageKeySize = 5;
    gerritConfig.setLong("cache", cacheKey, "avgKeySize", averageKeySize);
    gerritConfig.save();

    assertThat(configUnderTest(gerritConfig).getAverageKeySize()).isEqualTo(averageKeySize);
  }

  @Test
  public void shouldProvideDefaultAverageKeySizeWhenNotConfigured() throws Exception {
    assertThat(configUnderTest(gerritConfig).getAverageKeySize()).isEqualTo(DEFAULT_AVG_KEY_SIZE);
  }

  @Test
  public void shouldProvideAverageValueSizeWhenConfigured() throws Exception {
    long averageValueSize = 6;
    gerritConfig.setLong("cache", cacheKey, "avgValueSize", averageValueSize);
    gerritConfig.save();

    assertThat(configUnderTest(gerritConfig).getAverageValueSize()).isEqualTo(averageValueSize);
  }

  @Test
  public void shouldProvideDefaultAverageValueSizeWhenNotConfigured() throws Exception {
    assertThat(configUnderTest(gerritConfig).getAverageValueSize())
        .isEqualTo(DEFAULT_AVG_VALUE_SIZE);
  }

  @Test
  public void shouldProvideMaxDefaultBloatFactorWhenNotConfigured() throws Exception {
    assertThat(configUnderTest(gerritConfig).getMaxBloatFactor())
        .isEqualTo(DEFAULT_MAX_BLOAT_FACTOR);
  }

  @Test
  public void shouldProvideMaxBloatFactorWhenConfigured() throws Exception {
    int bloatFactor = 3;
    gerritConfig.setInt("cache", cacheKey, "maxBloatFactor", bloatFactor);
    gerritConfig.save();

    assertThat(configUnderTest(gerritConfig).getMaxBloatFactor()).isEqualTo(bloatFactor);
  }

  @Test
  public void shouldProvideExpireAfterWriteWhenMaxAgeIsConfgured() throws Exception {
    String maxAge = "3 minutes";
    gerritConfig.setString("cache", cacheKey, "maxAge", maxAge);
    gerritConfig.save();

    assertThat(configUnderTest(gerritConfig).getExpireAfterWrite())
        .isEqualTo(Duration.ofSeconds(180));
  }

  @Test
  public void shouldProvideDefinitionExpireAfterWriteWhenNotConfigured() throws Exception {
    assertThat(configUnderTest(gerritConfig).getExpireAfterWrite()).isEqualTo(expireAfterWrite);
  }

  @Test
  public void shouldProvideRefreshAfterWriteWhenConfigured() throws Exception {
    String refreshAfterWrite = "6 minutes";
    gerritConfig.setString("cache", cacheKey, "refreshAfterWrite", refreshAfterWrite);
    gerritConfig.save();

    assertThat(configUnderTest(gerritConfig).getRefreshAfterWrite())
        .isEqualTo(Duration.ofSeconds(360));
  }

  @Test
  public void shouldProvideDefinitionRefreshAfterWriteWhenNotConfigured() throws Exception {
    assertThat(configUnderTest(gerritConfig).getRefreshAfterWrite()).isEqualTo(refreshAfterWrite);
  }

  @Test
  public void shouldProvidePercentageFreeSpaceEvictionThresholdWhenConfigured() throws Exception {
    int percentageFreeThreshold = 70;
    gerritConfig.setInt(
        "cache", cacheKey, "percentageFreeSpaceEvictionThreshold", percentageFreeThreshold);
    gerritConfig.save();

    assertThat(configUnderTest(gerritConfig).getPercentageFreeSpaceEvictionThreshold())
        .isEqualTo(percentageFreeThreshold);
  }

  @Test
  public void shouldProvidePercentageFreeSpaceEvictionThresholdDefault() throws Exception {
    assertThat(configUnderTest(gerritConfig).getPercentageFreeSpaceEvictionThreshold())
        .isEqualTo(DEFAULT_PERCENTAGE_FREE_SPACE_EVICTION_THRESHOLD);
  }

  private ChronicleMapCacheConfig configUnderTest(StoredConfig gerritConfig) {
    File cacheFile =
        ChronicleMapCacheFactory.fileName(
            sitePaths.site_path.resolve(cacheDirectory), cacheName, version);
    sitePaths
        .resolve(cacheDirectory)
        .resolve(String.format("%s_%s.dat", cacheName, version))
        .toFile();

    return new ChronicleMapCacheConfig(
        gerritConfig, cacheKey, cacheFile, expireAfterWrite, refreshAfterWrite);
  }
}
