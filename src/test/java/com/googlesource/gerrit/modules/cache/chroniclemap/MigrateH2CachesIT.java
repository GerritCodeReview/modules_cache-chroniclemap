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

import com.google.common.base.Joiner;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.WaitUtil;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.account.CachedAccountDetails;
import com.google.gerrit.server.cache.h2.H2CacheImpl;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheSshCommand.H2_SUFFIX;
import static com.googlesource.gerrit.modules.cache.chroniclemap.MigrateH2Caches.DEFAULT_MAX_BLOAT_FACTOR;
import static com.googlesource.gerrit.modules.cache.chroniclemap.MigrateH2Caches.DEFAULT_SIZE_MULTIPLIER;

@Sandboxed
@UseSsh
@TestPlugin(
    name = "cache-chroniclemap",
    sshModule = "com.googlesource.gerrit.modules.cache.chroniclemap.SSHCommandModule")
public class MigrateH2CachesIT extends LightweightPluginDaemonTest {
  private final Duration LOAD_CACHE_WAIT_TIMEOUT = Duration.ofSeconds(4);
  private String ACCOUNTS_CACHE_NAME = "accounts";
  private String PERSISTED_PROJECTS_CACHE_NAME = "persisted_projects";

  @Inject protected GitRepositoryManager repoManager;
  @Inject private SitePaths sitePaths;
  @Inject @GerritServerConfig Config cfg;
  @Inject Injector injector;

  private ChronicleMapCacheConfig.Factory chronicleMapCacheConfigFactory;

  private String cmd = Joiner.on(" ").join("cache-chroniclemap", "migrate-h2-caches");

  @Before
  public void setUp() {
    chronicleMapCacheConfigFactory =
        plugin.getSshInjector().getInstance(ChronicleMapCacheConfig.Factory.class);
  }

  @Test
  @UseLocalDisk
  public void shouldRunAndCompleteSuccessfullyWhenCacheDirectoryIsDefined() throws Exception {
    String result = adminSshSession.exec(cmd);
    adminSshSession.assertSuccess();
    assertThat(result).contains("Complete");
  }

  @Test
  @UseLocalDisk
  public void shouldOutputChronicleMapBloatedConfiguration() throws Exception {
    waitForCacheToLoad(ACCOUNTS_CACHE_NAME);
    waitForCacheToLoad(PERSISTED_PROJECTS_CACHE_NAME);

    String result = adminSshSession.exec(cmd);
    adminSshSession.assertSuccess();

    assertThat(result)
        .contains(
            "[cache \""
                + ACCOUNTS_CACHE_NAME
                + "\"]\n"
                + "\tmaxEntries = "
                + H2CacheFor(ACCOUNTS_CACHE_NAME).diskStats().size() * DEFAULT_SIZE_MULTIPLIER);

    assertThat(result)
        .contains(
            "[cache \""
                + PERSISTED_PROJECTS_CACHE_NAME
                + "\"]\n"
                + "\tmaxEntries = "
                + H2CacheFor(PERSISTED_PROJECTS_CACHE_NAME).diskStats().size()
                    * DEFAULT_SIZE_MULTIPLIER);
  }

  @Test
  public void shouldFailWhenCacheDirectoryIsNotDefined() throws Exception {
    adminSshSession.exec(cmd);
    adminSshSession.assertFailure("fatal: Cannot run migration, cache directory is not configured");
  }

  @Test
  public void shouldFailWhenUserHasNoAdminServerCapability() throws Exception {
    userSshSession.exec(cmd);
    userSshSession.assertFailure("administrateServer for plugin cache-chroniclemap not permitted");
  }

  @Test
  @UseLocalDisk
  public void shouldMigrateAccountsCache() throws Exception {
    waitForCacheToLoad(ACCOUNTS_CACHE_NAME);

    adminSshSession.exec(cmd);
    adminSshSession.assertSuccess();

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

    adminSshSession.exec(cmd);
    adminSshSession.assertSuccess();

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
  private <K, V> H2CacheImpl<K, V> H2CacheFor(String named) {
    return (H2CacheImpl<K, V>) findClassBoundWithName(LoadingCache.class, named);
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

  private <K, V> ChronicleMapCacheImpl<K, V> chronicleCacheFor(String cacheName)
      throws BaseCommand.UnloggedFailure, IOException {
    Path cacheDirectory = sitePaths.resolve(cfg.getString("cache", null, "directory"));

    ChronicleMapCacheConfig config =
        MigrateH2Caches.makeChronicleMapConfig(
            chronicleMapCacheConfigFactory,
            cacheDirectory,
            cacheName,
            H2CacheSshCommand.getStats(
                cacheDirectory.resolve(String.format("%s.%s", cacheName, H2_SUFFIX))),
            DEFAULT_SIZE_MULTIPLIER,
            DEFAULT_MAX_BLOAT_FACTOR);

    return new ChronicleMapCacheImpl<>(config, cacheName);
  }

  private void waitForCacheToLoad(String cacheName) throws InterruptedException {
    WaitUtil.waitUntil(() -> H2CacheFor(cacheName).diskStats().size() > 0, LOAD_CACHE_WAIT_TIMEOUT);
  }
}
