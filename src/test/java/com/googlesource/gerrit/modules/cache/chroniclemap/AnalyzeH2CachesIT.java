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

package com.googlesource.gerrit.modules.cache.chroniclemap;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

@UseSsh
@TestPlugin(
    name = "cache-chroniclemap",
    sshModule = "com.googlesource.gerrit.modules.cache.chroniclemap.SSHCommandModule")
public class AnalyzeH2CachesIT extends LightweightPluginDaemonTest {

  @Inject private SitePaths sitePaths;

  private String cmd = Joiner.on(" ").join("cache-chroniclemap", "analyze-h2-caches");

  @Test
  @UseLocalDisk
  public void shouldAnalyzeH2Cache() throws Exception {
    createChange();

    String result = adminSshSession.exec(cmd);

    adminSshSession.assertSuccess();
    assertThat(result).contains("[cache \"diff\"]\n" + "\tmaxEntries = 1\n");
    assertThat(result).contains("[cache \"accounts\"]\n" + "\tmaxEntries = 4\n");
    assertThat(result).contains("[cache \"diff_summary\"]\n" + "\tmaxEntries = 1\n");
    assertThat(result).contains("[cache \"persisted_projects\"]\n" + "\tmaxEntries = 3\n");
  }

  @Test
  @UseLocalDisk
  public void shouldProduceWarningWhenCacheFileIsEmpty() throws Exception {
    List<String> expected =
        ImmutableList.of(
            "WARN: Cache diff_intraline is empty, skipping.",
            "WARN: Cache change_kind is empty, skipping.",
            "WARN: Cache diff_summary is empty, skipping.",
            "WARN: Cache diff is empty, skipping.",
            "WARN: Cache pure_revert is empty, skipping.",
            "WARN: Cache git_tags is empty, skipping.");
    String result = adminSshSession.exec(cmd);

    adminSshSession.assertSuccess();
    assertThat(ImmutableList.copyOf(result.split("\n"))).containsAtLeastElementsIn(expected);
  }

  @Test
  @UseLocalDisk
  public void shouldIgnoreNonH2Files() throws Exception {

    Path cacheDirectory = sitePaths.resolve(cfg.getString("cache", null, "directory"));
    Files.write(cacheDirectory.resolve("some.dat"), "some_content".getBytes());

    List<String> expected =
        ImmutableList.of(
            "WARN: Cache diff_intraline is empty, skipping.",
            "WARN: Cache change_kind is empty, skipping.",
            "WARN: Cache diff_summary is empty, skipping.",
            "WARN: Cache diff is empty, skipping.",
            "WARN: Cache pure_revert is empty, skipping.",
            "WARN: Cache git_tags is empty, skipping.");
    String result = adminSshSession.exec(cmd);

    adminSshSession.assertSuccess();
    assertThat(ImmutableList.copyOf(result.split("\n"))).containsAtLeastElementsIn(expected);
  }

  @Test
  @UseLocalDisk
  public void shouldFailWhenCacheDirectoryDoesNotExists() throws Exception {
    cfg.setString("cache", null, "directory", "/tmp/non_existing_directory");

    adminSshSession.exec(cmd);
    adminSshSession.assertFailure(
        "fatal: disk cache is configured but doesn't exist: /tmp/non_existing_directory");
  }
}
