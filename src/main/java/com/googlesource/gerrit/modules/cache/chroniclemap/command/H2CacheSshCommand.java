package com.googlesource.gerrit.modules.cache.chroniclemap.command;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.SshCommand;
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
import org.h2.Driver;

public abstract class H2CacheSshCommand extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  protected String cacheDirectory;
  protected SitePaths site;

  protected String baseName(Path h2File) {
    return FilenameUtils.removeExtension(FilenameUtils.getBaseName(h2File.toString()));
  }

  protected H2DataStats getStats(Path h2File) throws UnloggedFailure {
    String url = jdbcUrl(h2File);
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

          // Account for extra serialization bytes of TimedValue entries.
          short TIMED_VALUE_WRAPPER_OVERHEAD = Long.BYTES + Integer.BYTES;
          return H2DataStats.create(size, avgKeySize, avgValueSize + TIMED_VALUE_WRAPPER_OVERHEAD);
        }
        return H2DataStats.empty();
      }
    } catch (SQLException e) {
      stderr.println(String.format("Could not get information from %s", baseName(h2File)));
      throw die(e);
    }
  }

  protected H2DataStats asMap(Path h2File) throws UnloggedFailure {
    String url = jdbcUrl(h2File);
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

          // Account for extra serialization bytes of TimedValue entries.
          short TIMED_VALUE_WRAPPER_OVERHEAD = Long.BYTES + Integer.BYTES;
          return H2DataStats.create(size, avgKeySize, avgValueSize + TIMED_VALUE_WRAPPER_OVERHEAD);
        }
        return H2DataStats.empty();
      }
    } catch (SQLException e) {
      stderr.println(String.format("Could not get information from %s", baseName(h2File)));
      throw die(e);
    }
  }

  protected Set<Path> getH2CacheFiles() throws UnloggedFailure {

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

  String jdbcUrl(Path h2FilePath) {
    final String normalized =
        FilenameUtils.removeExtension(FilenameUtils.removeExtension(h2FilePath.toString()));
    return "jdbc:h2:" + normalized + ";AUTO_SERVER=TRUE";
  }

  public static Optional<Path> getCacheDir(SitePaths site, String name) throws IOException {
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
