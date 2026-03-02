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
import { waitFor } from '@management-commands/domain-management-commands';
import { updateDomainReporter } from '@management-commands/reporter-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { KafkaAuditPayload, assertNoKafkaMessage, waitForKafkaMessage } from '@utils-commands/kafka-consumer';
import { DomainReporterGatewayFixture, setupDomainReporterGatewayFixture } from './fixture/domain-reporter-gateway-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: DomainReporterGatewayFixture;

beforeAll(async () => {
  fixture = await setupDomainReporterGatewayFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Kafka Reporter - Domain Level Gateway', () => {
  describe('Enabled - All events (no auditTypes filter)', () => {
    it('should receive USER_LOGIN event in Kafka', async () => {
      const topic = uniqueName('domain-all-events', true);
      await fixture.addReporter(topic, []);

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

  describe('Enabled - Filtered (auditTypes set)', () => {
    it('should not receive USER_LOGIN event in Kafka when filter excludes it', async () => {
      const topic = uniqueName('domain-filtered-events', true);
      await fixture.addReporter(topic, ['USER_CREATED']);

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
  });

  describe('Disabled', () => {
    it('should not receive USER_LOGIN event in Kafka when reporter is disabled', async () => {
      const topic = uniqueName('domain-disabled', true);
      const reporter = await fixture.addReporter(topic, []);

      await updateDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: false,
        configuration: reporter.configuration,
      });

      // waitForSyncAfter does not work for disabled reporters, so we need to wait for the sync to complete manually
      await waitFor(5000);

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
  });
});
