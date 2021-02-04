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

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
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
  public void shouldMigrateH2Cache() throws Exception {
    String result = adminSshSession.exec(cmd);

    adminSshSession.assertSuccess();
  }
}
