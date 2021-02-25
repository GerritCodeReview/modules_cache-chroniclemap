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

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheCommand.H2_SUFFIX;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2MigrationServlet.DEFAULT_MAX_BLOAT_FACTOR;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2MigrationServlet.DEFAULT_SIZE_MULTIPLIER;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2MigrationServlet.MAX_BLOAT_FACTOR_PARAM;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2MigrationServlet.SIZE_MULTIPLIER_PARAM;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.WaitUtil;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.account.CachedAccountDetails;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.h2.H2CacheImpl;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Key;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(
    name = "cache-chroniclemap",
    httpModule = "com.googlesource.gerrit.modules.cache.chroniclemap.HttpModule")
public class MigrateH2CachesIT extends LightweightPluginDaemonTest {
  private final Duration LOAD_CACHE_WAIT_TIMEOUT = Duration.ofSeconds(4);
  private String ACCOUNTS_CACHE_NAME = "accounts";
  private String PERSISTED_PROJECTS_CACHE_NAME = "persisted_projects";
  private String MIGRATION_ENDPOINT = "/plugins/cache-chroniclemap/migrate";

  @Inject protected GitRepositoryManager repoManager;
  @Inject private SitePaths sitePaths;
  @Inject @GerritServerConfig Config cfg;

  private ChronicleMapCacheConfig.Factory chronicleMapCacheConfigFactory;

  @Before
  public void setUp() {
    chronicleMapCacheConfigFactory =
        plugin.getHttpInjector().getInstance(ChronicleMapCacheConfig.Factory.class);
  }

  @Test
  @UseLocalDisk
  public void shouldRunAndCompleteSuccessfullyWhenCacheDirectoryIsDefined() throws Exception {
    runMigration(adminRestSession).assertOK();
  }

  @Test
  @UseLocalDisk
  public void shouldReturnTexPlain() throws Exception {
    RestResponse result = runMigration(adminRestSession);
    assertThat(result.getHeader(CONTENT_TYPE)).contains("text/plain");
  }

  @Test
  @UseLocalDisk
  public void shouldOutputChronicleMapBloatedDefaultConfiguration() throws Exception {
    waitForCacheToLoad(ACCOUNTS_CACHE_NAME);
    waitForCacheToLoad(PERSISTED_PROJECTS_CACHE_NAME);

    RestResponse result = runMigration(adminRestSession);
    result.assertOK();

    Config configResult = new Config();
    configResult.fromText(result.getEntityContent());

    assertThat(configResult.getInt("cache", ACCOUNTS_CACHE_NAME, "maxEntries", 0))
        .isEqualTo(H2CacheFor(ACCOUNTS_CACHE_NAME).diskStats().size() * DEFAULT_SIZE_MULTIPLIER);

    assertThat(configResult.getInt("cache", ACCOUNTS_CACHE_NAME, "maxBloatFactor", 0))
        .isEqualTo(DEFAULT_MAX_BLOAT_FACTOR);

    assertThat(configResult.getInt("cache", PERSISTED_PROJECTS_CACHE_NAME, "maxEntries", 0))
        .isEqualTo(
            H2CacheFor(PERSISTED_PROJECTS_CACHE_NAME).diskStats().size() * DEFAULT_SIZE_MULTIPLIER);

    assertThat(configResult.getInt("cache", PERSISTED_PROJECTS_CACHE_NAME, "maxBloatFactor", 0))
        .isEqualTo(DEFAULT_MAX_BLOAT_FACTOR);
  }

  @Test
  @UseLocalDisk
  public void shouldOutputChronicleMapBloatedProvidedConfiguration() throws Exception {
    waitForCacheToLoad(ACCOUNTS_CACHE_NAME);
    waitForCacheToLoad(PERSISTED_PROJECTS_CACHE_NAME);

    int sizeMultiplier = 2;
    int maxBloatFactor = 3;
    RestResponse result = runMigration(sizeMultiplier, maxBloatFactor);
    result.assertOK();

    Config configResult = new Config();
    configResult.fromText(result.getEntityContent());

    assertThat(configResult.getInt("cache", ACCOUNTS_CACHE_NAME, "maxEntries", 0))
        .isEqualTo(H2CacheFor(ACCOUNTS_CACHE_NAME).diskStats().size() * sizeMultiplier);

    assertThat(configResult.getInt("cache", ACCOUNTS_CACHE_NAME, "maxBloatFactor", 0))
        .isEqualTo(maxBloatFactor);

    assertThat(configResult.getInt("cache", PERSISTED_PROJECTS_CACHE_NAME, "maxEntries", 0))
        .isEqualTo(H2CacheFor(PERSISTED_PROJECTS_CACHE_NAME).diskStats().size() * sizeMultiplier);

    assertThat(configResult.getInt("cache", PERSISTED_PROJECTS_CACHE_NAME, "maxBloatFactor", 0))
        .isEqualTo(maxBloatFactor);
  }

  @Test
  public void shouldFailWhenCacheDirectoryIsNotDefined() throws Exception {
    RestResponse result = runMigration(adminRestSession);
    result.assertBadRequest();
    assertThat(result.getEntityContent())
        .contains("Cannot run migration, cache directory is not configured");
  }

  @Test
  public void shouldFailWhenUserHasNoAdminServerCapability() throws Exception {
    RestResponse result = runMigration(userRestSession);
    result.assertForbidden();
    assertThat(result.getEntityContent())
        .contains("administrateServer for plugin cache-chroniclemap not permitted");
  }

  @Test
  @UseLocalDisk
  public void shouldMigrateAccountsCache() throws Exception {
    waitForCacheToLoad(ACCOUNTS_CACHE_NAME);

    runMigration(adminRestSession).assertOK();

    ChronicleMapCacheImpl<CachedAccountDetails.Key, CachedAccountDetails> chronicleMapCache =
        chronicleCacheFor(ACCOUNTS_CACHE_NAME);
    H2CacheImpl<CachedAccountDetails.Key, CachedAccountDetails> h2Cache =
        H2CacheFor(ACCOUNTS_CACHE_NAME);

    assertThat(chronicleMapCache.diskStats().size()).isEqualTo(h2Cache.diskStats().size());
  }

  @Test
  @UseLocalDisk
  public void shouldMigratePersistentProjects() throws Exception {
    waitForCacheToLoad(PERSISTED_PROJECTS_CACHE_NAME);

    runMigration(adminRestSession).assertOK();

    H2CacheImpl<Cache.ProjectCacheKeyProto, CachedProjectConfig> h2Cache =
        H2CacheFor(PERSISTED_PROJECTS_CACHE_NAME);
    ChronicleMapCacheImpl<Cache.ProjectCacheKeyProto, CachedProjectConfig> chronicleMapCache =
        chronicleCacheFor(PERSISTED_PROJECTS_CACHE_NAME);

    Cache.ProjectCacheKeyProto allUsersProto = projectCacheKey(allUsers);
    Cache.ProjectCacheKeyProto allProjectsProto = projectCacheKey(allProjects);

    assertThat(chronicleMapCache.get(allUsersProto)).isEqualTo(h2Cache.get(allUsersProto));
    assertThat(chronicleMapCache.get(allProjectsProto)).isEqualTo(h2Cache.get(allProjectsProto));
  }

  private Cache.ProjectCacheKeyProto projectCacheKey(Project.NameKey key) throws IOException {
    try (Repository git = repoManager.openRepository(key)) {
      return Cache.ProjectCacheKeyProto.newBuilder()
          .setProject(key.get())
          .setRevision(
              ObjectIdConverter.create()
                  .toByteString(git.exactRef(RefNames.REFS_CONFIG).getObjectId()))
          .build();
    }
  }

  @SuppressWarnings("unchecked")
  private <K, V> PersistentCacheDef<K, V> getPersistentCacheDef(String named) {
    return findClassBoundWithName(PersistentCacheDef.class, named);
  }

  @SuppressWarnings("unchecked")
  private <K, V> H2CacheImpl<K, V> H2CacheFor(String named) {
    return (H2CacheImpl<K, V>) findClassBoundWithName(LoadingCache.class, named);
  }

  @SuppressWarnings("unchecked")
  private <K, V> CacheLoader<K, V> cacheLoaderFor(String named) {
    return findClassBoundWithName(CacheLoader.class, named);
  }

  private RestResponse runMigration(int sizeMultiplier, int maxBloatFactor) throws IOException {
    return adminRestSession.get(
        String.format(
            "%s?%s=%d&%s=%d",
            MIGRATION_ENDPOINT,
            MAX_BLOAT_FACTOR_PARAM,
            maxBloatFactor,
            SIZE_MULTIPLIER_PARAM,
            sizeMultiplier));
  }

  private RestResponse runMigration(RestSession restSession) throws IOException {
    return restSession.get(MIGRATION_ENDPOINT);
  }

  private <T> T findClassBoundWithName(Class<T> clazz, String named) {
    return plugin.getSysInjector().getAllBindings().entrySet().stream()
        .filter(entry -> isClassBoundWithName(entry, clazz.getSimpleName(), named))
        .findFirst()
        .map(entry -> clazz.cast(entry.getValue().getProvider().get()))
        .get();
  }

  private boolean isClassBoundWithName(
      Map.Entry<Key<?>, Binding<?>> entry, String classNameMatch, String named) {
    String className = entry.getKey().getTypeLiteral().getRawType().getSimpleName();
    Annotation annotation = entry.getKey().getAnnotation();
    return className.equals(classNameMatch)
        && annotation != null
        && annotation.toString().endsWith(String.format("Named(value=\"%s\")", named));
  }

  private <K, V> ChronicleMapCacheImpl<K, V> chronicleCacheFor(String cacheName) throws Exception {
    Path cacheDirectory = sitePaths.resolve(cfg.getString("cache", null, "directory"));

    PersistentCacheDef<K, V> persistentDef = getPersistentCacheDef(cacheName);
    ChronicleMapCacheConfig config =
        H2MigrationServlet.makeChronicleMapConfig(
            chronicleMapCacheConfigFactory,
            cacheDirectory,
            persistentDef,
            H2CacheCommand.getStats(
                cacheDirectory.resolve(String.format("%s.%s", cacheName, H2_SUFFIX))),
            DEFAULT_SIZE_MULTIPLIER,
            DEFAULT_MAX_BLOAT_FACTOR);

    return new ChronicleMapCacheImpl<>(
        persistentDef, config, cacheLoaderFor(cacheName), new DisabledMetricMaker());
  }

  private void waitForCacheToLoad(String cacheName) throws InterruptedException {
    WaitUtil.waitUntil(() -> H2CacheFor(cacheName).diskStats().size() > 0, LOAD_CACHE_WAIT_TIMEOUT);
  }
}
