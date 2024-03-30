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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.Cache;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.inject.Inject;
import org.junit.Test;

@UseLocalDisk
public class ChronicleMapCacheIT extends AbstractDaemonTest {

  private static final int ZERO_INMEMORY_CACHE = 0;
  @Inject PersistentCacheFactory persistentCacheFactory;

  @Override
  public com.google.inject.Module createModule() {
    return new ChronicleMapCacheModule();
  }

  @Test
  public void shouldBeAbleToInstallChronicleMapCacheFactory() {
    assertThat(persistentCacheFactory).isInstanceOf(ChronicleMapCacheFactory.class);
  }

  @Test
  public void shouldBuildInMemoryCacheWhenDiskLimitIsNegative() {
    final int negativeDiskLimit = -1;
    final Cache<String, String> cache =
        persistentCacheFactory.build(new TestPersistentCacheDef("foo", null, negativeDiskLimit, 0));

    assertThat(cache.getClass().getSimpleName()).isEqualTo("CaffeinatedGuavaCache");
  }

  @Test
  public void shouldBuildInMemoryCacheWhenDiskLimitIsPositive() {
    final int positiveDiskLimit = 1024;
    assertThat(
            persistentCacheFactory.build(
                new TestPersistentCacheDef("foo", null, positiveDiskLimit, ZERO_INMEMORY_CACHE)))
        .isInstanceOf(ChronicleMapCacheImpl.class);
  }

  @Test
  public void shouldCacheNewProject() throws Exception {
    String newProjectName = name("newProject");
    adminRestSession.put("/projects/" + newProjectName).assertCreated();

    assertThat(projectCache.get(Project.nameKey(newProjectName))).isPresent();
  }

  @Test
  public void shouldCacheNewUser() throws Exception {
    AccountInput input = new AccountInput();
    input.username = "foo";

    assertThat(accountCache.getByUsername(input.username)).isEmpty();
    adminRestSession.put("/accounts/" + input.username, input);

    assertThat(accountCache.getByUsername(input.username)).isPresent();
  }
}
