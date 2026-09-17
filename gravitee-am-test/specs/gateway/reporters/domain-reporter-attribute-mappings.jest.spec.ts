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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
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

  describe('Sensitive attributes', () => {
    it('should never export a denied attribute, however the expression spells it', async () => {
      const topic = uniqueName('mapping-denied-attribute', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['user'].claims['azure_b2c_refresh_token']}", exportedName: 'denied_top_level' },
          { expression: "{#context.attributes['user'].claims['azure_b2c_'+'refresh_token']}", exportedName: 'denied_concatenated' },
          { expression: "{#context.attributes['user']['claims']['idp']['access_token']}", exportedName: 'denied_nested' },
          { expression: "{#context.attributes['user'].claims['idp']['name']}", exportedName: 'idp_name' },
          { expression: "{#context.attributes['user'].claims['employeeId']}", exportedName: 'employee_id' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.type).toEqual('USER_LOGIN');
      expect(received.customAttributes).toEqual({ idp_name: 'Acme IdP', employee_id: 'E-4471' });
      expect(JSON.stringify(received)).not.toContain('RT-XYZ');
      expect(JSON.stringify(received)).not.toContain('SECRET-AT');
    });
  });

  describe('Event type scope', () => {
    it('should enrich an event of a configured type', async () => {
      const topic = uniqueName('mapping-scope-in', true);
      await fixture.addReporter(
        topic,
        [],
        [{ expression: "{#context.attributes['user'].email}", exportedName: 'user_email' }],
        ['USER_LOGIN'],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ user_email: fixture.user.email });
    });

    it('should still deliver an event of another type, without the extra attributes', async () => {
      const topic = uniqueName('mapping-scope-out', true);
      await fixture.addReporter(
        topic,
        [],
        [{ expression: "{#context.attributes['user'].email}", exportedName: 'user_email' }],
        ['USER_LOGOUT'],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.type).toEqual('USER_LOGIN');
      expect(received.customAttributes).toBeUndefined();
    });
  });

  describe('Structured values', () => {
    it('should export a list as a JSON array', async () => {
      const topic = uniqueName('mapping-list', true);
      await fixture.addReporter(
        topic,
        [],
        [{ expression: "{#context.attributes['user'].additionalInformation['teams']}", exportedName: 'teams' }],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ teams: '["platform","security"]' });
    });

    it('should export a map as a JSON object without its denied keys', async () => {
      const topic = uniqueName('mapping-map', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['user'].additionalInformation['idp']}", exportedName: 'idp' },
          { expression: "{#context.attributes['user'].claims}", exportedName: 'claims' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      const customAttributes = received.customAttributes as Record<string, string>;
      expect(JSON.parse(customAttributes.idp)).toEqual({ name: 'Acme IdP' });
      const claims = JSON.parse(customAttributes.claims);
      expect(claims).toMatchObject({
        employeeId: 'E-4471',
        department: 'Platform',
        teams: ['platform', 'security'],
        idp: { name: 'Acme IdP' },
      });
      expect(claims).not.toHaveProperty('azure_b2c_refresh_token');
      expect(JSON.stringify(received)).not.toContain('RT-XYZ');
      expect(JSON.stringify(received)).not.toContain('SECRET-AT');
    });

    it('should drop an object while keeping the fields picked out of it', async () => {
      const topic = uniqueName('mapping-object', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['client'].cookieSettings}", exportedName: 'whole_object' },
          { expression: "{#context.attributes['client'].clientId}", exportedName: 'application_id' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ application_id: fixture.application.settings.oauth.clientId });
    });
  });

  describe('The audit itself is a source', () => {
    it('should export the audit type and transaction id', async () => {
      const topic = uniqueName('mapping-audit-source', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['audit'].type}", exportedName: 'audit_type' },
          { expression: "{#context.attributes['audit'].transactionId}", exportedName: 'txn' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.customAttributes).toEqual({ audit_type: 'USER_LOGIN', txn: received.transactionId });
    });
  });

  describe('Nothing resolves', () => {
    it('should omit the customAttributes property entirely when no mapping resolves', async () => {
      const topic = uniqueName('mapping-nothing-resolves', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['user'].additionalInformation['doesNotExist']}", exportedName: 'a' },
          { expression: "{#context.attributes['user'].additionalInformation['alsoMissing']}", exportedName: 'b' },
        ],
      );

      const received = await loginAndAwaitLogin(topic);

      expect(received.type).toEqual('USER_LOGIN');
      expect(received.customAttributes).toBeUndefined();
    });
  });

  describe('Client credentials', () => {
    it('should carry the client attribute and omit the user attribute on a client_credentials token', async () => {
      const topic = uniqueName('mapping-client-credentials', true);
      await fixture.addReporter(
        topic,
        [],
        [
          { expression: "{#context.attributes['user'].username}", exportedName: 'user_name' },
          { expression: "{#context.attributes['client'].clientId}", exportedName: 'application_id' },
        ],
      );

      // A dedicated service application with the client_credentials grant (no end user is involved).
      const accessToken = await requestAdminAccessToken();
      await createScope(fixture.domain.id, accessToken, { key: 'read', name: 'read', description: 'read' });
      const created = await createApplication(fixture.domain.id, accessToken, { name: uniqueName('cc-app', true), type: 'SERVICE' });
      const app = await waitForSyncAfter(fixture.domain.id, () =>
        updateApplication(
          fixture.domain.id,
          accessToken,
          { settings: { oauth: { grantTypes: ['client_credentials'], scopeSettings: [{ scope: 'read', defaultScope: false }] } } },
          created.id,
        ).then((updated) => {
          updated.settings.oauth.clientSecret = created.settings.oauth.clientSecret;
          return updated;
        }),
      );

      const requestClientCredentialsToken = () =>
        performPost(
          fixture.openIdConfiguration.token_endpoint,
          '',
          new URLSearchParams({ grant_type: 'client_credentials', scope: 'read' }).toString(),
          {
            'Content-Type': 'application/x-www-form-urlencoded',
            Authorization: `Basic ${applicationBase64Token(app)}`,
          },
        )
          .expect(200)
          .then(() => {});

      const received = await waitForKafkaMessage(
        topic,
        { predicate: (msg) => msg.type === 'TOKEN_CREATED' },
        requestClientCredentialsToken,
      );

      // No end user, so the user mapping is omitted; only the client attribute is exported.
      expect(received.customAttributes).toEqual({ application_id: app.settings.oauth.clientId });
    });
  });
});
