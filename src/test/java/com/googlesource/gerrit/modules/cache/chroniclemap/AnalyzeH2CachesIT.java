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
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

@UseSsh
@TestPlugin(
    name = "cache-chroniclemap",
    sshModule = "com.googlesource.gerrit.modules.cache.chroniclemap.SSHCommandModule")
@UseLocalDisk
@Sandboxed
public class AnalyzeH2CachesIT extends LightweightPluginDaemonWithSshSessionsTest {

  @Inject private SitePaths sitePaths;

  private String cmd = Joiner.on(" ").join("cache-chroniclemap", "analyze-h2-caches");

  @Test
  public void shouldAnalyzeH2Cache() throws Exception {
    try (SshSessionProvider testSshSessionAsAdmin = newSshSession(admin.id())) {
      createChange();

      String result = testSshSessionAsAdmin.exec(cmd);

      testSshSessionAsAdmin.assertSuccess();
      assertThat(result).contains("[cache \"git_file_diff\"]");
      assertThat(result).contains("[cache \"gerrit_file_diff\"]");
      assertThat(result).contains("[cache \"accounts\"]");
      assertThat(result).contains("[cache \"diff_summary\"]");
      assertThat(result).contains("[cache \"persisted_projects\"]");
    }
  }

  @Test
  public void shouldDenyAccessToAnalyzeH2Cache() throws Exception {
    try (SshSessionProvider sshSession = newSshSession(user.id())) {
      sshSession.exec(cmd);
      sshSession.assertFailure("not permitted");
    }
  }

  @Test
  public void shouldProduceWarningWhenCacheFileIsEmpty() throws Exception {
    try (SshSessionProvider testSshSessionAsAdmin = newSshSession(admin.id())) {
      String expectedPattern = "WARN: Cache .[a-z]+ is empty, skipping";
      String result = testSshSessionAsAdmin.exec(cmd);

      testSshSessionAsAdmin.assertSuccess();
      assertThat(result).containsMatch(expectedPattern);
    }
  }

  @Test
  public void shouldIgnoreNonH2Files() throws Exception {
    try (SshSessionProvider testSshSessionAsAdmin = newSshSession(admin.id())) {
      Path cacheDirectory = sitePaths.resolve(cfg.getString("cache", null, "directory"));
      Files.write(cacheDirectory.resolve("some.dat"), "some_content".getBytes());

      @SuppressWarnings("unused")
      String result = testSshSessionAsAdmin.exec(cmd);

      testSshSessionAsAdmin.assertSuccess();
    }
  }

  @Test
  public void shouldFailWhenCacheDirectoryDoesNotExists() throws Exception {
    try (SshSessionProvider testSshSessionAsAdmin = newSshSession(admin.id())) {
      cfg.setString("cache", null, "directory", "/tmp/non_existing_directory");

      testSshSessionAsAdmin.exec(cmd);
      testSshSessionAsAdmin.assertFailure(
          "fatal: disk cache is configured but doesn't exist: /tmp/non_existing_directory");
    }
  }
}
