// Copyright (C) 2024 The Android Open Source Project
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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.GerritServer;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.inject.Inject;
import java.net.InetSocketAddress;
import java.util.function.Supplier;

public class LightweightPluginDaemonWithSshSessionsTest extends LightweightPluginDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject @Nullable @GerritServer.TestSshServerAddress private InetSocketAddress sshAddress;

  static class SshSessionProvider implements Supplier<SshSession>, AutoCloseable {
    private final SshSession sshSession;

    SshSessionProvider(SshSession sshSession) {
      this.sshSession = sshSession;
    }

    @Override
    public void close() throws Exception {
      sshSession.close();
    }

    @Override
    public SshSession get() {
      return sshSession;
    }

    @CanIgnoreReturnValue
    public String exec(String command) throws Exception {
      return sshSession.exec(command);
    }

    public void assertSuccess() {
      sshSession.assertSuccess();
    }

    public void assertFailure(String message) {
      sshSession.assertFailure(message);
    }
  }

  protected SshSessionProvider newSshSession(Account.Id accountId) throws Exception {
    SshSession sshSession =
        SshSessionFactory.createSession(
            sshKeys, sshAddress, accountOperations.account(accountId).get());
    sshSession.open();
    return new SshSessionProvider(sshSession);
  }
}
