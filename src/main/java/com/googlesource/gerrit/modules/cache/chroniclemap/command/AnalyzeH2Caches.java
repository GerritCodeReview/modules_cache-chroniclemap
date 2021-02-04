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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.lib.Config;

import java.nio.file.Path;
import java.util.Set;

public class AnalyzeH2Caches extends H2CacheSshCommand {

  @Inject
  AnalyzeH2Caches(@GerritServerConfig Config cfg, SitePaths site) {
    this.cacheDirectory = cfg.getString("cache", null, "directory");
    this.site = site;
  }

  protected String baseName(Path h2File) {
    return FilenameUtils.removeExtension(FilenameUtils.getBaseName(h2File.toString()));
  }

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    Set<Path> h2Files = getH2CacheFiles();
    stdout.println("Extracting information from H2 caches...");

    Config config = new Config();
    for (Path h2 : h2Files) {
      H2DataStats stats = getStats(h2);
      String baseName = baseName(h2);

      if (stats.size() == 0) {
        stdout.println(String.format("WARN: Cache %s is empty, skipping.", baseName));
        continue;
      }

      config.setLong("cache", baseName, "maxEntries", stats.size());
      config.setLong("cache", baseName, "avgKeySize", stats.avgKeySize());
      config.setLong("cache", baseName, "avgValueSize", stats.avgValueSize());
    }
    stdout.println();
    stdout.println("****************************");
    stdout.println("** Chronicle-map template **");
    stdout.println("****************************");
    stdout.println();
    stdout.println(config.toText());
  }
}
