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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class AutoAdjustCachesCommand extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  protected static final String CONFIG_HEADER = "__CONFIG__";
  protected static final String TUNED_INFIX = "_tuned_";

  private final AutoAdjustCaches autoAdjustCachesEngine;

  @Option(
      name = "--dry-run",
      aliases = {"-d"},
      usage = "Calculate the average key and value size, but do not migrate the data.")
  public void setDryRun(boolean dryRun) {
    autoAdjustCachesEngine.setDryRun(dryRun);
  }

  @Argument(
      index = 0,
      required = false,
      multiValued = true,
      metaVar = "CACHE_NAME",
      usage = "name of cache to be adjusted")
  public void setCacheName(String cacheName) {
    autoAdjustCachesEngine.addCacheNames(Arrays.asList(cacheName));
  }

  @Inject
  AutoAdjustCachesCommand(AutoAdjustCaches autoAdjustCachesEngine) {
    this.autoAdjustCachesEngine = autoAdjustCachesEngine;
  }

  @Override
  protected void run() throws Exception {
    try {
      Config outputChronicleMapConfig = autoAdjustCachesEngine.run(new TextProgressMonitor(stdout));

      stdout.println();
      stdout.println("**********************************");

      if (outputChronicleMapConfig.getSections().isEmpty()) {
        stdout.println("All exsting caches are already tuned: no changes needed.");
        return;
      }

      stdout.println("** Chronicle-map config changes **");
      stdout.println("**********************************");
      stdout.println();
      stdout.println(CONFIG_HEADER);
      stdout.println(outputChronicleMapConfig.toText());
    } catch (AuthException | PermissionBackendException e) {
      stderr.println(e.getLocalizedMessage());
      throw e;
    } catch (IOException e) {
      logger.atSevere().log("Could not create new cache", e);
      stderr.println(String.format("Could not create new cache : %s", e.getLocalizedMessage()));
    }
  }
}
