/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { afterAll, afterEach, beforeAll, beforeEach, expect } from '@jest/globals';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { waitFor } from '@management-commands/domain-management-commands';
import { updateDomainReporter } from '@management-commands/reporter-management-commands';
import { TcpServer, startTcpServer } from '@utils-commands/tcp-consumer';
import { DomainReporterTcpFixture, setupDomainReporterTcpFixture } from './fixture/domain-reporter-tcp-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: DomainReporterTcpFixture;
let tcpServer: TcpServer;

beforeAll(async () => {
  fixture = await setupDomainReporterTcpFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

beforeEach(async () => {
  tcpServer = await startTcpServer();
});

afterEach(async () => {
  try {
    await tcpServer.close();
  } catch {
    // ignore
  }
});

describe('TCP Reporter - Domain Level Gateway', () => {
  describe('Enabled', () => {
    it('should receive USER_LOGIN event over TCP in ELASTICSEARCH format', async () => {
      await fixture.addReporter('localhost', tcpServer.port);

      const received = await tcpServer.waitForMessage(
        { predicate: (msg) => msg.event_type === 'USER_LOGIN' },
        () =>
          loginUserNameAndPassword(
            fixture.application.settings.oauth.clientId,
            fixture.user,
            fixture.user.password,
            false,
            fixture.openIdConfiguration,
            fixture.domain,
          ).then(() => {}),
      );

      expect(received).toBeDefined();
      expect(received.event_type).toEqual('USER_LOGIN');
      expect(received.referenceType).toEqual('DOMAIN');
      expect(received.referenceId).toEqual(fixture.domain.id);
    });
  });

  describe('Disabled', () => {
    it('should not receive USER_LOGIN event over TCP when reporter is disabled', async () => {
      const reporter = await fixture.addReporter('localhost', tcpServer.port);

      await updateDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: false,
        configuration: reporter.configuration,
      });

      // waitForSyncAfter does not work for disabled reporters, so wait manually
      await waitFor(5000);

      await tcpServer.assertNoMessage(
        { predicate: (msg) => msg.event_type === 'USER_LOGIN' },
        () =>
          loginUserNameAndPassword(
            fixture.application.settings.oauth.clientId,
            fixture.user,
            fixture.user.password,
            false,
            fixture.openIdConfiguration,
            fixture.domain,
          ).then(() => {}),
      );
    });
  });
});
