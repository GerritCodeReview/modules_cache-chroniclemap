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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.io.File;
import java.nio.file.Path;
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
    sshModule = "com.googlesource.gerrit.modules.cache.chroniclemap.SSHCommandModule")
public class AutoAdjustCachesIT extends LightweightPluginDaemonTest {
  private static final String CMD = "cache-chroniclemap auto-adjust-caches";
  private static final String MERGEABILITY = "mergeability";
  private static final String DIFF = "diff";
  private static final String DIFF_TUNED_FILE_PREFIX = DIFF + "_0_tuned_";
  private static final String DIFF_SUMMARY = "diff_summary";
  private static final String ACCOUNTS = "accounts";
  private static final String PERSISTED_PROJECTS = "persisted_projects";

  private static final ImmutableList<String> EXPECTED_CACHES =
      ImmutableList.of(MERGEABILITY, DIFF, DIFF_SUMMARY, ACCOUNTS, PERSISTED_PROJECTS);

  @Inject private SitePaths sitePaths;

  @Override
  public com.google.inject.Module createModule() {
    return new ChronicleMapCacheModule();
  }

  @Test
  public void shouldUseDefaultsWhenCachesAreNotConfigured() throws Exception {
    createChange();

    String result = adminSshSession.exec(CMD);

    adminSshSession.assertSuccess();
    Config configResult = configResult(result);

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

    adminSshSession.exec(CMD);

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
  @GerritConfig(name = "cache.diff.avgKeySize", value = "94")
  @GerritConfig(name = "cache.diff.avgValueSize", value = "260")
  public void shouldNotRecreateCacheFilesForCachesAlreadyTuned() throws Exception {
    createChange();
    String tuneResult = adminSshSession.exec(CMD);
    adminSshSession.assertSuccess();

    assertThat(configResult(tuneResult).getSubsections("cache")).doesNotContain(DIFF);
    assertThat(Joiner.on('\n').join(listTunedFileNames())).doesNotContain(DIFF_TUNED_FILE_PREFIX);
  }

  @Test
  public void shouldCreateDiffCacheTuned() throws Exception {
    createChange();
    String tuneResult = adminSshSession.exec(CMD);
    adminSshSession.assertSuccess();

    assertThat(configResult(tuneResult).getSubsections("cache")).contains(DIFF);
    assertThat(Joiner.on('\n').join(listTunedFileNames())).contains(DIFF_TUNED_FILE_PREFIX);
  }

  @Test
  public void shouldDenyAccessToCreateNewCacheFiles() throws Exception {
    userSshSession.exec(CMD);
    userSshSession.assertFailure("not permitted");
  }

  private Config configResult(String result) throws ConfigInvalidException {
    Config configResult = new Config();
    configResult.fromText((result.split(CONFIG_HEADER))[1]);
    return configResult;
  }

  private List<String> listTunedFileNames() {
    List<String> tunedFileNames =
        Stream.of(Objects.requireNonNull(cachePath().toFile().listFiles()))
            .filter(file -> !file.isDirectory())
            .map(File::getName)
            .filter(n -> n.contains(TUNED_INFIX))
            .collect(Collectors.toList());
    return tunedFileNames;
  }

  private Path cachePath() {
    return sitePaths.resolve(cfg.getString("cache", null, "directory"));
  }
}
