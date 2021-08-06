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

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.modules.cache.chroniclemap.AutoAdjustCachesCommand.CONFIG_HEADER;
import static com.googlesource.gerrit.modules.cache.chroniclemap.AutoAdjustCachesCommand.TUNED_INFIX;
import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults.maxBloatFactorFor;
import static com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults.maxEntriesFor;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.ModuleImpl;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@Sandboxed
@UseLocalDisk
@UseSsh
@TestPlugin(
    name = "cache-chroniclemap",
    sshModule = "com.googlesource.gerrit.modules.cache.chroniclemap.SSHCommandModule",
    httpModule = "com.googlesource.gerrit.modules.cache.chroniclemap.HttpModule")
public class AutoAdjustCachesIT extends LightweightPluginDaemonTest {
  private static final String SSH_CMD = "cache-chroniclemap auto-adjust-caches";
  private static final String REST_CMD = "/plugins/cache-chroniclemap/auto-adjust-caches";
  private static final String MERGEABILITY = "mergeability";
  private static final String DIFF = "diff";
  private static final String DIFF_SUMMARY = "diff_summary";
  private static final String ACCOUNTS = "accounts";
  private static final String PERSISTED_PROJECTS = "persisted_projects";
  private static final String TEST_CACHE_NAME = "test_cache";
  private static final int TEST_CACHE_VERSION = 1;
  private static final String TEST_CACHE_FILENAME_TUNED =
      TEST_CACHE_NAME + "_" + TEST_CACHE_VERSION + AutoAdjustCaches.TUNED_INFIX;
  private static final String TEST_CACHE_KEY_100_CHARS = new String(new char[100]);

  private static final ImmutableList<String> EXPECTED_CACHES =
      ImmutableList.of(MERGEABILITY, DIFF, DIFF_SUMMARY, ACCOUNTS, PERSISTED_PROJECTS);

  @Inject private SitePaths sitePaths;

  @Inject
  @Named(TEST_CACHE_NAME)
  LoadingCache<String, String> testCache;

  @ModuleImpl(name = CacheModule.PERSISTENT_MODULE)
  public static class TestPersistentCacheModule extends CacheModule {

    @Override
    protected void configure() {
      persist(TEST_CACHE_NAME, String.class, String.class)
          .loader(TestCacheLoader.class)
          .version(TEST_CACHE_VERSION)
          .maximumWeight(1024)
          .expireAfterWrite(Duration.ofDays(1));
      install(new ChronicleMapCacheModule());
    }
  }

  public static class TestCacheLoader extends CacheLoader<String, String> {

    @Override
    public String load(String key) throws Exception {
      return key;
    }
  }

  @Override
  public com.google.inject.Module createModule() {
    return new TestPersistentCacheModule();
  }

  @Test
  public void shouldUseDefaultsWhenCachesAreNotConfigured() throws Exception {
    createChange();

    String result = adminSshSession.exec(SSH_CMD);

    adminSshSession.assertSuccess();
    Config configResult = configResult(result, CONFIG_HEADER);

    for (String cache : EXPECTED_CACHES) {
      assertThat(configResult.getLong("cache", cache, "maxEntries", 0))
          .isEqualTo(maxEntriesFor(cache));
      assertThat(configResult.getLong("cache", cache, "maxBloatFactor", 0))
          .isEqualTo(maxBloatFactorFor(cache));
    }
  }

  @Test
  public void shouldCreateNewCacheFiles() throws Exception {
    createChange();

    adminSshSession.exec(SSH_CMD);

    adminSshSession.assertSuccess();
    File cacheDir = sitePaths.resolve(cfg.getString("cache", null, "directory")).toFile();
    Set<String> tunedCaches =
        Stream.of(Objects.requireNonNull(cacheDir.listFiles()))
            .filter(file -> !file.isDirectory())
            .map(File::getName)
            .filter(
                n ->
                    n.contains(TUNED_INFIX)
                        && n.matches(".*(" + String.join("|", EXPECTED_CACHES) + ").*"))
            .collect(Collectors.toSet());

    assertThat(tunedCaches.size()).isEqualTo(EXPECTED_CACHES.size());
  }

  @Test
  @GerritConfig(name = "cache.test_cache.avgKeySize", value = "207")
  @GerritConfig(name = "cache.test_cache.avgValueSize", value = "207")
  public void shouldNotRecreateCacheFilesForCachesAlreadyTuned() throws Exception {
    testCache.get(TEST_CACHE_KEY_100_CHARS);

    String tuneResult = adminSshSession.exec(SSH_CMD);
    adminSshSession.assertSuccess();

    assertThat(configResult(tuneResult, CONFIG_HEADER).getSubsections("cache"))
        .doesNotContain(TEST_CACHE_NAME);
    assertThat(Joiner.on('\n').join(listTunedFileNames()))
        .doesNotContain(TEST_CACHE_FILENAME_TUNED);
  }

  @Test
  public void shouldCreateDiffCacheTuned() throws Exception {
    testCache.get(TEST_CACHE_KEY_100_CHARS);
    String tuneResult = adminSshSession.exec(SSH_CMD);
    adminSshSession.assertSuccess();

    assertThat(configResult(tuneResult, CONFIG_HEADER).getSubsections("cache"))
        .contains(TEST_CACHE_NAME);
    assertThat(Joiner.on('\n').join(listTunedFileNames())).contains(TEST_CACHE_FILENAME_TUNED);
  }

  @Test
  public void shouldDenyAccessOverSshToCreateNewCacheFiles() throws Exception {
    userSshSession.exec(SSH_CMD);
    userSshSession.assertFailure("not permitted");
  }

  @Test
  public void shouldDenyAccessOverRestToCreateNewCacheFiles() throws Exception {
    userRestSession.put(REST_CMD).assertForbidden();
  }

  @Test
  public void shouldAllowTuningOverRestForAdmin() throws Exception {
    RestResponse resp = adminRestSession.put(REST_CMD);

    resp.assertCreated();

    assertThat(configResult(resp.getEntityContent(), null).getSubsections("cache")).isNotEmpty();
    assertThat(listTunedFileNames()).isNotEmpty();
  }

  private Config configResult(String result, @Nullable String configHeader)
      throws ConfigInvalidException {
    Config configResult = new Config();
    configResult.fromText(configHeader == null ? result : result.split(configHeader)[1]);
    return configResult;
  }

  private List<String> listTunedFileNames() {
    Path cachePath = sitePaths.resolve(cfg.getString("cache", null, "directory"));
    return Stream.of(Objects.requireNonNull(cachePath.toFile().listFiles()))
        .filter(file -> !file.isDirectory())
        .map(File::getName)
        .filter(n -> n.contains(TUNED_INFIX))
        .collect(Collectors.toList());
  }
}
