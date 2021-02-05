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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.lib.Config;
import org.h2.Driver;

public class AnalyzeH2Caches extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private String cacheDirectory;
  private SitePaths site;

  @Inject
  AnalyzeH2Caches(@GerritServerConfig Config cfg, SitePaths site) {
    this.cacheDirectory = cfg.getString("cache", null, "directory");
    this.site = site;
  }

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    Set<Path> h2Files = getH2CacheFiles();
    stdout.println("Extracting information from H2 caches...");

    Config config = new Config();
    for (Path h2 : h2Files) {
      final String url = jdbcUrl(h2);
      final String baseName =
          FilenameUtils.removeExtension(FilenameUtils.getBaseName(h2.toString()));
      try {

        try (Connection conn = Driver.load().connect(url, null);
            Statement s = conn.createStatement();
            ResultSet r =
                s.executeQuery(
                    "SELECT COUNT(*), AVG(OCTET_LENGTH(k)), AVG(OCTET_LENGTH(v)) FROM data")) {
          if (r.next()) {
            long size = r.getLong(1);
            long avgKeySize = r.getLong(2);
            long avgValueSize = r.getLong(3);

            if (size == 0) {
              stdout.println(String.format("WARN: Cache %s is empty, skipping.", baseName));
              continue;
            }

            config.setLong("cache", baseName, "maxEntries", size);
            config.setLong("cache", baseName, "avgKeySize", avgKeySize);

            // Account for extra serialization bytes of TimedValue entries.
            short TIMED_VALUE_WRAPPER_OVERHEAD = Long.BYTES + Integer.BYTES;
            config.setLong(
                "cache", baseName, "avgValueSize", avgValueSize + TIMED_VALUE_WRAPPER_OVERHEAD);
          }
        }
      } catch (SQLException e) {
        stderr.println(String.format("Could not get information from %s", baseName));
        throw die(e);
      }
    }
    stdout.println();
    stdout.println("****************************");
    stdout.println("** Chronicle-map template **");
    stdout.println("****************************");
    stdout.println();
    stdout.println(config.toText());
  }

  private Set<Path> getH2CacheFiles() throws UnloggedFailure {

    try {
      final Optional<Path> maybeCacheDir = getCacheDir(site, cacheDirectory);

      return maybeCacheDir
          .map(
              cacheDir -> {
                try {
                  return Files.walk(cacheDir)
                      .filter(path -> path.toString().endsWith("h2.db"))
                      .collect(Collectors.toSet());
                } catch (IOException e) {
                  logger.atSevere().withCause(e).log("Could not read H2 files");
                  return Collections.<Path>emptySet();
                }
              })
          .orElse(Collections.emptySet());
    } catch (IOException e) {
      throw die(e);
    }
  }

  private String jdbcUrl(Path h2FilePath) {
    final String normalized =
        FilenameUtils.removeExtension(FilenameUtils.removeExtension(h2FilePath.toString()));
    return "jdbc:h2:" + normalized + ";AUTO_SERVER=TRUE";
  }

  private static Optional<Path> getCacheDir(SitePaths site, String name) throws IOException {
    if (name == null) {
      return Optional.empty();
    }
    Path loc = site.resolve(name);
    if (!Files.exists(loc)) {
      throw new IOException(
          String.format("disk cache is configured but doesn't exist: %s", loc.toAbsolutePath()));
    }
    if (!Files.isReadable(loc)) {
      throw new IOException(String.format("Can't read from disk cache: %s", loc.toAbsolutePath()));
    }
    logger.atFine().log("Enabling disk cache %s", loc.toAbsolutePath());
    return Optional.of(loc);
  }
}
