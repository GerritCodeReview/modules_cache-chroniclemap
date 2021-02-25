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

import static org.eclipse.jgit.util.HttpSupport.TEXT_PLAIN;

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class H2MigrationFilter extends AllRequestFilter {
  private final H2MigrationEndpoint migrationEndpoint;

  @Inject
  public H2MigrationFilter(H2MigrationEndpoint migrationEndpoint) {
    this.migrationEndpoint = migrationEndpoint;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletResponse httpResponse = (HttpServletResponse) response;
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    if (isMigrationEndpoint(httpRequest)) {
      doMigration(httpResponse);
    } else {
      chain.doFilter(request, response);
    }
  }

  private boolean isMigrationEndpoint(HttpServletRequest httpServletRequest) {
    return httpServletRequest
        .getRequestURI()
        .matches("(?:/a)?/config/server/cache-chroniclemap~migrate");
  }

  private void doMigration(HttpServletResponse httpResponse) throws ServletException {
    try {
      Response<String> response = migrationEndpoint.apply(new ConfigResource());
      httpResponse.setContentType(TEXT_PLAIN);
      httpResponse.setStatus(response.statusCode());
      String responseContent = response.value();
      PrintWriter writer = httpResponse.getWriter();
      writer.print(responseContent);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }
}
