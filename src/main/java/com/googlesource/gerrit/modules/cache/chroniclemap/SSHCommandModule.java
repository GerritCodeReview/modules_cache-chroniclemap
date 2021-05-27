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
package com.googlesource.gerrit.modules.cache.chroniclemap;

import com.google.gerrit.sshd.PluginCommandModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;

public class SSHCommandModule extends PluginCommandModule {
  private final Injector injector;

  @Inject
  SSHCommandModule(Injector injector) {
    this.injector = injector;
  }

  @Override
  protected void configureCommands() {
    if (injector.getExistingBinding(Key.get(ChronicleMapCacheConfig.Factory.class)) == null) {
      factory(ChronicleMapCacheConfig.Factory.class);
    }
    command("analyze-h2-caches").to(AnalyzeH2Caches.class);
  }
}
