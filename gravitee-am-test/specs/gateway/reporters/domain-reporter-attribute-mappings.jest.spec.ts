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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { KafkaAuditPayload, waitForKafkaMessage } from '@utils-commands/kafka-consumer';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
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

/** Signs the fixture user in and returns the USER_LOGIN record the given topic received. */
const loginAndAwaitLogin = (topic: string): Promise<KafkaAuditPayload> =>
  waitForKafkaMessage(topic, { predicate: (msg) => msg.type === 'USER_LOGIN' }, () =>
    loginUserNameAndPassword(
      fixture.application.settings.oauth.clientId,
      fixture.user,
      fixture.user.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    ).then(() => {}),
  );

describe('Reporter attribute mappings - Domain Level Gateway', () => {
  describe('Resolving the supported sources', () => {
    it('should export a top-level user attribute', async () => {
      const topic = uniqueName('mapping-user-attribute', true);
      await fixture.addReporter(topic, [], [{ expression: "{#context.attributes['user'].email}", exportedName: 'user_email' }]);

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ user_email: fixture.user.email });
    });

    it('should export a nested custom user attribute under its configured name', async () => {
      const topic = uniqueName('mapping-nested-attribute', true);
      await fixture.addReporter(
        topic,
        [],
        [{ expression: "{#context.attributes['user'].additionalInformation['employeeId']}", exportedName: 'employee_id' }],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ employee_id: 'E-4471' });
    });

    it('should export a client attribute', async () => {
      const topic = uniqueName('mapping-client-attribute', true);
      await fixture.addReporter(topic, [], [{ expression: "{#context.attributes['client'].clientId}", exportedName: 'application_id' }]);

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ application_id: fixture.application.settings.oauth.clientId });
    });

    it('should export several mappings on one event', async () => {
      const topic = uniqueName('mapping-several', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['user'].username}", exportedName: 'user_name' },
          { expression: "{#context.attributes['user'].additionalInformation['department']}", exportedName: 'department_name' },
          { expression: "{#context.attributes['client'].clientId}", exportedName: 'application_id' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({
        user_name: fixture.user.username,
        department_name: 'Platform',
        application_id: fixture.application.settings.oauth.clientId,
      });
    });
  });

  describe('Template text', () => {
    it('should interpolate a literal prefix and export a plain constant', async () => {
      const topic = uniqueName('mapping-prefix-const', true);
      await fixture.addReporter(
        topic,
        [],
        [
          {
            expression: "tenant-{#context.attributes['user'].additionalInformation['department']}",
            exportedName: 'tenant_tag',
          },
          { expression: 'production', exportedName: 'environment' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ tenant_tag: 'tenant-Platform', environment: 'production' });
    });

    it('should interpolate literal text between two expressions', async () => {
      const topic = uniqueName('mapping-interpolated', true);
      await fixture.addReporter(
        topic,
        [],
        [
          {
            expression: "{#context.attributes['user'].username} of {#context.attributes['user'].additionalInformation['department']}",
            exportedName: 'who',
          },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ who: `${fixture.user.username} of Platform` });
    });
  });

  describe('Event coverage', () => {
    it('should resolve user mappings on token events as well as login events', async () => {
      const topic = uniqueName('mapping-token-event', true);
      await fixture.addReporter(topic, [], [{ expression: "{#context.attributes['user'].username}", exportedName: 'user_name' }]);

      const exchangeCodeForToken = async (): Promise<void> => {
        const redirect = await loginUserNameAndPassword(
          fixture.application.settings.oauth.clientId,
          fixture.user,
          fixture.user.password,
          false,
          fixture.openIdConfiguration,
          fixture.domain,
        );
        const code = new URL(redirect.headers['location']).searchParams.get('code');
        expect(code).toBeTruthy();

        await performPost(
          fixture.openIdConfiguration.token_endpoint,
          '',
          new URLSearchParams({
            grant_type: 'authorization_code',
            code,
            redirect_uri: fixture.application.settings.oauth.redirectUris[0],
          }).toString(),
          {
            'Content-Type': 'application/x-www-form-urlencoded',
            Authorization: `Basic ${applicationBase64Token(fixture.application)}`,
          },
        ).expect(200);
      };

      const received = await waitForKafkaMessage(topic, { predicate: (msg) => msg.type === 'TOKEN_CREATED' }, exchangeCodeForToken);

      expect(received.customAttributes).toEqual({ user_name: fixture.user.username });
    });
  });

  describe('Never failing the event', () => {
    it('should still deliver the event and omit the field when the attribute is missing', async () => {
      const topic = uniqueName('mapping-missing-attribute', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['user'].additionalInformation['nowhereToBeFound']}", exportedName: 'absent' },
          { expression: "{#context.attributes['user'].username}", exportedName: 'user_name' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.type).toEqual('USER_LOGIN');
      expect(received.customAttributes).toEqual({ user_name: fixture.user.username });
    });

    it('should still deliver the event and resolve the siblings when one expression is malformed', async () => {
      const topic = uniqueName('mapping-malformed', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['user'.username}", exportedName: 'broken' },
          { expression: "{#context.attributes['user'].email}", exportedName: 'user_email' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.type).toEqual('USER_LOGIN');
      expect(received.customAttributes).toEqual({ user_email: fixture.user.email });
    });
  });

  describe('Backward compatibility', () => {
    it('should leave the payload untouched when the reporter declares no mappings', async () => {
      const topic = uniqueName('mapping-none-declared', true);
      await fixture.addReporter(topic, []);

      const received = await loginAndAwaitLogin(topic);

      expect(received.type).toEqual('USER_LOGIN');
      expect(received.referenceId).toEqual(fixture.domain.id);
      expect(received.customAttributes).toBeUndefined();
    });
  });

  describe('Per-reporter isolation', () => {
    it('should give each reporter only its own attributes', async () => {
      const userTopic = uniqueName('mapping-isolation-user', true);
      const clientTopic = uniqueName('mapping-isolation-client', true);
      const plainTopic = uniqueName('mapping-isolation-plain', true);

      await fixture.addReporter(userTopic, [], [{ expression: "{#context.attributes['user'].email}", exportedName: 'user_email' }]);
      await fixture.addReporter(
        clientTopic,
        [],
        [{ expression: "{#context.attributes['client'].clientId}", exportedName: 'application_id' }],
      );
      await fixture.addReporter(plainTopic, []);

      // One sign-in fans out to all three reporters on the domain.
      const [fromUserTopic, fromClientTopic, fromPlainTopic] = await Promise.all([
        waitForKafkaMessage(userTopic, { predicate: (msg) => msg.type === 'USER_LOGIN' }, () =>
          loginUserNameAndPassword(
            fixture.application.settings.oauth.clientId,
            fixture.user,
            fixture.user.password,
            false,
            fixture.openIdConfiguration,
            fixture.domain,
          ).then(() => {}),
        ),
        waitForKafkaMessage(clientTopic, { predicate: (msg) => msg.type === 'USER_LOGIN' }, () => Promise.resolve()),
        waitForKafkaMessage(plainTopic, { predicate: (msg) => msg.type === 'USER_LOGIN' }, () => Promise.resolve()),
      ]);

      expect(fromUserTopic.customAttributes).toEqual({ user_email: fixture.user.email });
      expect(fromClientTopic.customAttributes).toEqual({
        application_id: fixture.application.settings.oauth.clientId,
      });
      expect(fromPlainTopic.customAttributes).toBeUndefined();
    });
  });
});
