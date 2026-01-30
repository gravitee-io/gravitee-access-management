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
import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { getAlertsApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface ApiManagementAlertFixture extends Fixture {
  domain: Domain;
  /** Ensures a webhook notifier exists (creates one if needed). Returns its id so tests can run in isolation. */
  ensureWebhookNotifierExists: () => Promise<string>;
}

const WEBHOOK_NOTIFIER_CONFIG = {
  type: 'webhook-notifier' as const,
  configuration: JSON.stringify({
    method: 'POST',
    url: 'https://example.com/webhook',
    headers: [{ name: 'Content-Type', value: 'text/plain' }],
    body: "An alert '${alert.name}' has been fired.",
    useSystemProxy: false,
  }),
  name: 'Webhook',
  enabled: true,
};

export const setupApiManagementAlertFixture = async (): Promise<ApiManagementAlertFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(
    accessToken,
    uniqueName('alert', true),
    'test alerting on domain',
  );

  let alertNotifierId: string | undefined;

  const ensureWebhookNotifierExists = async (): Promise<string> => {
    if (alertNotifierId) return alertNotifierId;
    const api = getAlertsApi(accessToken!);
    const notifier = await api.createAlertNotifier({
      organizationId: process.env.AM_DEF_ORG_ID!,
      environmentId: process.env.AM_DEF_ENV_ID!,
      domain: domain.id,
      newAlertNotifier: WEBHOOK_NOTIFIER_CONFIG,
    });
    alertNotifierId = notifier.id;
    return notifier.id;
  };

  const cleanup = async () => {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken);
    }
  };

  return {
    accessToken,
    domain,
    ensureWebhookNotifierExists,
    cleanUp: cleanup,
  };
};
