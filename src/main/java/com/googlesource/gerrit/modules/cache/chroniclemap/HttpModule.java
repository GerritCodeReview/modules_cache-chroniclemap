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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.servlet.ServletModule;

public class HttpModule extends ServletModule {
  private final Injector injector;

  @Inject
  HttpModule(Injector injector) {
    this.injector = injector;
  }

  @Override
  protected void configureServlets() {
    /*
     This module can be installed as a plugin, as a lib or both, depending on the wanted usage
     (refer to the docs for more details on why this is needed). For this reason, some binding
     might or might have not already been configured.
    */
    if (injector.getExistingBinding(Key.get(ChronicleMapCacheConfig.Factory.class)) == null) {
      install(
          new FactoryModule() {
            @Override
            protected void configure() {
              factory(ChronicleMapCacheConfig.Factory.class);
            }
          });
    }

    serve("/migrate").with(H2MigrationServlet.class);
  }
}
