// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.modules.cache.chroniclemap.command;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.modules.cache.chroniclemap.command.H2CacheSshCommand.H2_SUFFIX;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

@UseSsh
@TestPlugin(
    name = "cache-chroniclemap",
    sshModule = "com.googlesource.gerrit.modules.cache.chroniclemap.command.SSHCommandModule")
public class MigrateH2CachesIT extends LightweightPluginDaemonTest {

  @Inject private SitePaths sitePaths;

  private String cmd = Joiner.on(" ").join("cache-chroniclemap", "migrate-h2-caches");

  @Test
  @UseLocalDisk
  public void shouldRunAndCompleteSuccessfullyWhenCacheDirectoryIsDefined() throws Exception {
    String result = adminSshSession.exec(cmd);
    adminSshSession.assertSuccess();
    assertThat(result).contains("Complete");
  }

  @Test
  public void shouldRunAndCompleteSuccessfullyWhenCacheDirectoryIsNotDefined() throws Exception {
    adminSshSession.exec(cmd);
    adminSshSession.assertFailure("fatal: Cannot run migration, cache directory is not configured");
  }

  @Test
  @UseLocalDisk
  public void shouldMigrateAccountsCache() throws Exception {
    String cacheName = "accounts";
    int cacheVersion = 1;
    Path cacheDirectory = sitePaths.resolve(cfg.getString("cache", null, "directory"));

    Path h2Cache = cacheDirectory.resolve(String.format("%s.%s", cacheName, H2_SUFFIX));
    Path chronicleCache =
        ChronicleMapCacheFactory.fileName(cacheDirectory, cacheName, cacheVersion).toPath();

    assertThat(Files.exists(h2Cache)).isTrue();
    assertThat(Files.exists(chronicleCache)).isFalse();

    adminSshSession.exec(cmd);
    adminSshSession.assertSuccess();

    assertThat(Files.exists(chronicleCache)).isTrue();
  }
}
