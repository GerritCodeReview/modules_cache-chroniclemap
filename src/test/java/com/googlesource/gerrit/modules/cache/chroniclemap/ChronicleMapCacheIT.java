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
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.truth.Truth8;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritServer;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.io.File;
import org.junit.Test;
import org.junit.runner.Description;

public class ChronicleMapCacheIT extends AbstractDaemonTest {

  @Inject PersistentCacheFactory persistentCacheFactory;
  @Inject SitePaths gerritSitePath;
  Description testDescription;

  @Override
  protected void beforeTest(Description description) throws Exception {
    this.testDescription = description;
  }

  @Override
  protected void afterTest() throws Exception {}

  @Override
  public com.google.inject.Module createModule() {
    return new ChronicleMapCacheModule();
  }

  @Test
  public void shouldBeAbleToInstallChronicleMapCacheFactory() throws Exception {
    super.beforeTest(testDescription);
    assertThat(persistentCacheFactory).isInstanceOf(ChronicleMapCacheFactory.class);
  }

  @Test
  public void shouldCacheNewProject() throws Exception {
    super.beforeTest(testDescription);
    String newProjectName = name("newProject");
    RestResponse r = adminRestSession.put("/projects/" + newProjectName);

    Truth8.assertThat(projectCache.get(Project.nameKey(newProjectName))).isPresent();
  }

  @Test
  public void shouldCacheNewUser() throws Exception {
    super.beforeTest(testDescription);
    AccountInput input = new AccountInput();
    input.username = "foo";

    assertThat(accountCache.getByUsername(input.username)).isEmpty();
    adminRestSession.put("/accounts/" + input.username, input);

    assertThat(accountCache.getByUsername(input.username)).isPresent();
  }

  @Test(expected = GerritServer.StartupException.class)
  @UseLocalDisk
  @GerritConfig(name = "cache.directory", value = "cache")
  @GerritConfig(name = "cache.diff.diskLimit", value = "1")
  public void shouldFailToStartGerritWhenDiskLimitCannotBeHonoured() throws Exception {
    super.beforeTest(testDescription);
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "cache.directory", value = "cache")
  @GerritConfig(name = "cache.persisted_projects.diskLimit", value = "1024000")
  public void shouldStartGerritWhenDiskLimitIsHigherThanCacheFile() throws Exception {
    super.beforeTest(testDescription);
    File cache = gerritSitePath.resolve("cache/persisted_projects_2.dat").toFile();
    assertThat(cache.exists()).isTrue();
    assertThat(cache.length()).isLessThan(1_024_000);
  }
}
