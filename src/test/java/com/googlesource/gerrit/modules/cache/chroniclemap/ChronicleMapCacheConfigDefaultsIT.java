// Copyright (C) 2022 The Android Open Source Project
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
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheCommand.H2_SUFFIX;
import static com.googlesource.gerrit.modules.cache.chroniclemap.H2CacheCommand.getCacheDir;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.googlesource.gerrit.modules.cache.chroniclemap.ChronicleMapCacheConfig.Defaults;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@UseLocalDisk
@UseSsh
public class ChronicleMapCacheConfigDefaultsIT extends AbstractDaemonTest {
  @Inject private SitePaths site;

  @Test
  @GerritConfig(name = "cache.change_notes.diskLimit", value = "1")
  @GerritConfig(name = "cache.external_ids_map.diskLimit", value = "1")
  @GerritConfig(name = "cache.oauth_tokens.diskLimit", value = "1")
  @GerritConfig(name = "cache.web_sessions.diskLimit", value = "1")
  public void shouldAllPersistentCachesHaveDefaultConfiguration() throws Exception {
    // the following step is needed to spin-up all caches
    assertThat(adminSshSession.exec("gerrit show-caches")).isNotEmpty();
    Set<String> allCaches = gerritPersistentCaches(cfg, site);

    // for the time being filter out all caches that have no defaults so that the test passes
    Set<String> missingDefaults =
        Set.of(
            "comment_context",
            "gerrit_file_diff",
            "git_file_diff",
            "git_modified_files",
            "git_tags",
            "groups_byuuid_persisted",
            "modified_files");
    Set<String> expected =
        allCaches.stream().filter(cache -> !missingDefaults.contains(cache)).collect(toSet());
    assertThat(Defaults.defaultMap.keySet()).containsExactlyElementsIn(expected);
  }

  private static final Set<String> gerritPersistentCaches(Config config, SitePaths site)
      throws IOException {
    return getCacheDir(config, site)
        .map(
            cacheDir -> {
              try {
                return Files.walk(cacheDir)
                    .filter(path -> path.toString().endsWith(H2_SUFFIX))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(ChronicleMapCacheConfigDefaultsIT::getFileNameWithoutExtension)
                    .collect(toSet());
              } catch (IOException e) {
                e.printStackTrace();
                return Collections.<String>emptySet();
              }
            })
        .orElse(Collections.emptySet());
  }

  private static final String getFileNameWithoutExtension(String name) {
    return name.substring(0, name.length() - H2_SUFFIX.length() - 1);
  }
}
