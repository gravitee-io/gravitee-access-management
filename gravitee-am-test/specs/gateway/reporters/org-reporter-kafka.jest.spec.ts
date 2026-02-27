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

import { afterAll, beforeAll, expect } from '@jest/globals';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { KafkaAuditPayload, assertNoKafkaMessage, waitForKafkaMessage } from '@utils-commands/kafka-consumer';
import { withRetry } from '@utils-commands/retry';
import { OrgReporterGatewayFixture, setupOrgReporterGatewayFixture } from './fixture/org-reporter-gateway-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: OrgReporterGatewayFixture;

beforeAll(async () => {
  fixture = await setupOrgReporterGatewayFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Kafka Reporter - Org Level Gateway', () => {
  describe('inherited=true', () => {
    it('should receive USER_LOGIN event from child domain login', async () => {
      const topic = uniqueName('org-inherited-true', true);
      await fixture.addOrgReporter(topic, true);

      const received: KafkaAuditPayload = await waitForKafkaMessage(
        topic,
        { predicate: (msg) => msg.type === 'USER_LOGIN' },
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
      expect(received.type).toEqual('USER_LOGIN');
      expect(received.referenceType).toEqual('DOMAIN');
      expect(received.referenceId).toEqual(fixture.domain.id);
      expect(received.domainId).toEqual(fixture.domain.id);
    });
  });

  describe('inherited=false', () => {
    it('should NOT receive USER_LOGIN event from child domain login', async () => {
      const topic = uniqueName('org-inherited-false', true);
      await fixture.addOrgReporter(topic, false);

      await assertNoKafkaMessage(
        topic,
        { predicate: (msg) => msg.type === 'USER_LOGIN' },
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

    it('should receive event originating from org level', async () => {
      const topic = uniqueName('org-direct-events', true);
      await fixture.addOrgReporter(topic, false);

      // The management server needs a sync cycle to pick up the newly-created org
      // reporter before it routes audit events to Kafka (we can't use waitForSyncAfter)
      const received: KafkaAuditPayload = await withRetry(
        () =>
          waitForKafkaMessage(
            topic,
            { predicate: (msg) => msg.type === 'USER_LOGIN', timeoutMs: 5000 },
            () => requestAdminAccessToken().then(() => {}),
          ),
        6,
        500,
      );

      expect(received).toBeDefined();
      expect(received.type).toEqual('USER_LOGIN');
      expect(received.referenceType).toEqual('ORGANIZATION');
      expect(received.referenceId).toEqual(process.env.AM_DEF_ORG_ID);
      expect(received.domainId).toBeUndefined();
    });
  });
});
