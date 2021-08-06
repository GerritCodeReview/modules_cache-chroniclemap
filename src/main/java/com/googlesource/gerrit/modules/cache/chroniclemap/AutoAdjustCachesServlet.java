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

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.TEXT_PLAIN;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.PrintWriter;
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

  private final Provider<AutoAdjustCaches> autoAdjustCachesProvider;

  @Inject
  AutoAdjustCachesServlet(Provider<AutoAdjustCaches> autoAdjustCachesProvider) {
    this.autoAdjustCachesProvider = autoAdjustCachesProvider;
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    AutoAdjustCaches autoAdjustCachesEngine = autoAdjustCachesProvider.get();
    if (hasInvalidAcceptHeader(req)) {
      setResponse(
          rsp,
          HttpServletResponse.SC_BAD_REQUEST,
          "No advertised 'Accept' headers can be honoured. 'text/plain' should be provided in the request 'Accept' header.");
      return;
    }

    autoAdjustCachesEngine.setDryRun(
        Optional.ofNullable(req.getParameter("dry-run"))
            .or(() -> Optional.ofNullable(req.getParameter("d")))
            .map(Boolean::parseBoolean)
            .orElse(false));

    try {
      Config outputChronicleMapConfig = autoAdjustCachesEngine.run(null);

      if (outputChronicleMapConfig.getSections().isEmpty()) {
        setResponse(
            rsp,
            HttpServletResponse.SC_NO_CONTENT,
            "All exsting caches are already tuned: no changes needed.");
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

  private void setResponse(HttpServletResponse httpResponse, int statusCode, String value)
      throws IOException {
    httpResponse.setContentType(TEXT_PLAIN);
    httpResponse.setStatus(statusCode);
    PrintWriter writer = httpResponse.getWriter();
    writer.print(value);
  }

  private boolean hasInvalidAcceptHeader(HttpServletRequest req) {
    return req.getHeader(ACCEPT) != null
        && !Arrays.asList("text/plain", "text/*", "*/*").contains(req.getHeader(ACCEPT));
  }
}
