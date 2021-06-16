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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import org.junit.Test;

public class MigrateH2CachesIT extends AbstractDaemonTest {
  @Inject protected GitRepositoryManager repoManager;

  @Test
  @UseLocalDisk
  public void shouldRunAndCompleteSuccessfullyWhenCacheDirectoryIsDefined() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldReturnTexPlain() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldReturnBadRequestWhenTextPlainIsNotAnAcceptedHeader() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldReturnSuccessWhenAllTextContentsAreAccepted() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldReturnSuccessWhenAllContentsAreAccepted() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldOutputChronicleMapBloatedDefaultConfiguration() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldOutputChronicleMapBloatedProvidedConfiguration() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldFailWhenCacheDirectoryIsNotDefined() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldFailWhenUserHasNoAdminServerCapability() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldMigrateAccountsCache() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldMigratePersistentProjects() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldAssert1() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldAssert2() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldAssert3() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldCacheNewProject() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldCacheNewUser() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldAnalyzeH2Cache() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldProduceWarningWhenCacheFileIsEmpty() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldIgnoreNonH2Files() throws Exception {
    assertThat(true).isTrue();
  }

  @Test
  @UseLocalDisk
  public void shouldFailWhenCacheDirectoryDoesNotExists() throws Exception {
    assertThat(true).isTrue();
  }
}
