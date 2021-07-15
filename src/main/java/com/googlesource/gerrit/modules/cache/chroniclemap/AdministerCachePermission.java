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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;

class AdministerCachePermission {
  private final PermissionBackend permissionBackend;
  private final String pluginName;

  @Inject
  AdministerCachePermission(PermissionBackend permissionBackend, @PluginName String pluginName) {
    this.permissionBackend = permissionBackend;
    this.pluginName = pluginName;
  }

  boolean isCurrentUserAllowed() {
    try {
      checkCurrentUserAllowed();
      return true;
    } catch (AuthException | PermissionBackendException e) {
      return false;
    }
  }

  void checkCurrentUserAllowed() throws AuthException, PermissionBackendException {
    permissionBackend
        .currentUser()
        .checkAny(
            ImmutableSet.of(
                GlobalPermission.ADMINISTRATE_SERVER,
                new PluginPermission(pluginName, AdministerCachesCapability.ID)));
  }
}
