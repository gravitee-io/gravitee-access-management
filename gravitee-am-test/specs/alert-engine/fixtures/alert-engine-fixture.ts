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
import fetch from 'cross-fetch';
import { expect } from '@jest/globals';
import { Domain } from '@management-models/Domain';
import { AlertNotifier } from '@management-models/AlertNotifier';
import { AlertTrigger } from '@management-models/AlertTrigger';
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { getAlertsApi } from '@management-commands/service/utils';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { delay, uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../test-fixture';
import { PatchAlertTriggerTypeEnum } from '../../../api/management/models/PatchAlertTrigger';

// Must match the alert-engine overlay of the local stack (docker-compose.alert-engine.yml):
// the alert is evaluated once this many logins have been seen within the time window.
export const ALERT_SAMPLE_SIZE = 5;
// Default of alerts.too_many_login_failures.name on the management API.
export const ALERT_NAME = 'Too many login failures detected';

const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';
const LOGIN_FAILED = 'error=login_failed&error_code=invalid_user';

// WireMock base URLs — Alert Engine reaches it on the Docker network (INTERNAL_SFR_URL), the test runner on the
// host (SFR_URL).
const wiremockAlertEngineFacingBase = (): string => process.env.INTERNAL_SFR_URL || 'http://wiremock:8080';
const wiremockTestFacingBase = (): string => process.env.SFR_URL || 'http://localhost:8181';

/** What the webhook notifier posts, as rendered by Alert Engine from the notifier body template. */
export interface WebhookNotification {
  alert: string;
  severity: string;
  domainId: string;
  domainName: string;
}

export interface AlertEngineFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  oidc: DomainOidcConfig;
  alertNotifier: AlertNotifier;
  alertTrigger: AlertTrigger;
  /** Keeps failing logins, a sample at a time, until Alert Engine notifies the webhook. */
  failLoginsUntilNotified: (timeoutMs?: number) => Promise<WebhookNotification[]>;
}

export const setupAlertEngineFixture = async (): Promise<AlertEngineFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;
  let webhookStubId: string | null = null;

  const cleanUp = async () => {
    await safeDeleteDomain(domain?.id, accessToken);
    if (webhookStubId) {
      await fetch(`${wiremockTestFacingBase()}/__admin/mappings/${webhookStubId}`, { method: 'DELETE' });
    }
  };

  try {
    accessToken = await requestAdminAccessToken();
    domain = await createDomain(accessToken, uniqueName('alert-engine', true), 'Alert Engine notification test domain');
    domain = await patchDomain(domain.id, accessToken, { alertEnabled: true });

    const application = await createTestApp(uniqueName('alert-engine-app', true), domain, accessToken, 'web', {
      settings: {
        oauth: {
          redirectUris: [REDIRECT_URI],
          grantTypes: ['authorization_code'],
        },
      },
      identityProviders: new Set([{ identity: `default-idp-${domain.id}`, priority: 0 }]),
    });
    const user = await buildCreateAndTestUser(domain.id, accessToken, 0);

    const webhookPath = `/alert-engine/webhook/${domain.hrid}`;
    webhookStubId = await stubWebhookInWiremock(webhookPath);

    const alertsApi = getAlertsApi(accessToken);
    const domainRef = { organizationId: process.env.AM_DEF_ORG_ID!, environmentId: process.env.AM_DEF_ENV_ID!, domain: domain.id };

    const alertNotifier = await alertsApi.createAlertNotifier({
      ...domainRef,
      newAlertNotifier: {
        type: 'webhook-notifier',
        name: 'WireMock webhook',
        enabled: true,
        configuration: JSON.stringify({
          method: 'POST',
          url: `${wiremockAlertEngineFacingBase()}${webhookPath}`,
          headers: [{ name: 'Content-Type', value: 'application/json' }],
          body: JSON.stringify({
            alert: '${alert.name}',
            severity: '${alert.severity}',
            domainId: '${domain.id}',
            domainName: '${domain.name}',
          }),
          useSystemProxy: false,
        }),
      },
    });

    const [alertTrigger] = await alertsApi.updateAlertTriggers({
      ...domainRef,
      patchAlertTrigger: [
        {
          type: PatchAlertTriggerTypeEnum.TooManyLoginFailures,
          enabled: true,
          alertNotifiers: [alertNotifier.id],
        },
      ],
    });

    // Starting the domain is also what makes the management API push the trigger as enabled to Alert Engine.
    const started = await waitForDomainStart(await startDomain(domain.id, accessToken));
    domain = started.domain;
    const oidc = started.oidcConfig;

    const failLogins = async (attempts: number) => {
      for (let i = 0; i < attempts; i++) {
        const response = await loginUserNameAndPassword(application.settings.oauth.clientId, user, 'not-the-password', false, oidc, domain);
        expect(response.headers['location']).toContain(LOGIN_FAILED);
      }
    };

    const receivedNotifications = async (): Promise<WebhookNotification[]> => {
      const response = await fetch(`${wiremockTestFacingBase()}/__admin/requests/find`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ method: 'POST', url: webhookPath }),
      });
      expect(response.status).toBe(200);
      const { requests } = await response.json();
      return requests.map((request) => JSON.parse(request.body));
    };

    const failLoginsUntilNotified = async (timeoutMs = 90000): Promise<WebhookNotification[]> => {
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        // Alert Engine may still be registering the trigger, and the sample has to land in a single
        // time window: failing a whole sample per round covers both.
        await failLogins(ALERT_SAMPLE_SIZE);
        const roundEnd = Date.now() + 5000;
        while (Date.now() < roundEnd) {
          const notifications = await receivedNotifications();
          if (notifications.length > 0) {
            return notifications;
          }
          await delay(500);
        }
      }
      throw new Error(`Alert Engine did not notify ${webhookPath} within ${timeoutMs}ms`);
    };

    return {
      accessToken,
      domain,
      oidc,
      alertNotifier,
      alertTrigger,
      failLoginsUntilNotified,
      cleanUp,
    };
  } catch (error) {
    try {
      await cleanUp();
    } catch (e) {
      console.error('Cleanup failed:', e);
    }
    throw error;
  }
};

async function stubWebhookInWiremock(urlPath: string): Promise<string> {
  const response = await fetch(`${wiremockTestFacingBase()}/__admin/mappings`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      request: { method: 'POST', url: urlPath },
      response: { status: 200 },
    }),
  });
  expect(response.status).toBe(201);
  return (await response.json()).id;
}
