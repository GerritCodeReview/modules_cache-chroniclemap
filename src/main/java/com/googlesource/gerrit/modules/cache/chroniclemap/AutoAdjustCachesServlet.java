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

import static com.googlesource.gerrit.modules.cache.chroniclemap.HttpServletOps.checkAcceptHeader;
import static com.googlesource.gerrit.modules.cache.chroniclemap.HttpServletOps.setResponse;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;

@Singleton
public class AutoAdjustCachesServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  protected static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // This needs to be a provider so that every doPut() call is reentrant because
  // uses a different auto-adjuster for the caches.
  private final Provider<AutoAdjustCaches> autoAdjustCachesProvider;

  @Inject
  AutoAdjustCachesServlet(Provider<AutoAdjustCaches> autoAdjustCachesProvider) {
    this.autoAdjustCachesProvider = autoAdjustCachesProvider;
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    AutoAdjustCaches autoAdjustCachesEngine = autoAdjustCachesProvider.get();
    if (!checkAcceptHeader(req, rsp)) {
      return;
    }

    autoAdjustCachesEngine.setDryRun(
        Optional.ofNullable(req.getParameter("dry-run"))
            .or(() -> Optional.ofNullable(req.getParameter("d")))
            .isPresent());

    autoAdjustCachesEngine.addCacheNames(Arrays.asList(req.getParameterValues("CACHE_NAME")));

    try {
      Config outputChronicleMapConfig = autoAdjustCachesEngine.run(null);

      if (outputChronicleMapConfig.getSections().isEmpty()) {
        setResponse(
            rsp,
            HttpServletResponse.SC_NO_CONTENT,
            "All existing caches are already tuned: no changes needed.");
        return;
      }

      setResponse(rsp, HttpServletResponse.SC_CREATED, outputChronicleMapConfig.toText());
    } catch (AuthException | PermissionBackendException e) {
      setResponse(
          rsp,
          HttpServletResponse.SC_FORBIDDEN,
          "not permitted to administer caches : " + e.getLocalizedMessage());
      return;
    }
  }
}
