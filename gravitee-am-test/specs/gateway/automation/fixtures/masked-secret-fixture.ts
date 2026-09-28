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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';
import { AutomationClient } from '../../../management/automation/fixtures/automation-client';
import { buildInlineIdpDef } from '../../../management/automation/fixtures/automation-definitions';
import { SENSITIVE_VALUES_TEST } from '../../../management/automation/fixtures/sensitive-values-fixture';

export const MASKED_SECRET_TEST = {
  USERNAME: 'alice',
  PASSWORD: 'Masked-R0und-Trip!',
  MASK: SENSITIVE_VALUES_TEST.MASK,
  REDIRECT_URI: 'https://example.com/callback',
} as const;

export interface MaskedSecretFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  openIdConfiguration: any;
  application: Application;
  /** Applies the identity provider's own masked GET response back through the Automation API. */
  applyMaskedIdentityProvider: () => Promise<{ status: number; body: any }>;
}

/**
 * A domain created through the Management API, holding an inline identity provider managed through the
 * Automation API (addressed as `id:<domainId>`), and an application that logs in against it.
 */
export const setupMaskedSecretFixture = async (): Promise<MaskedSecretFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain;
  try {
    const setupResult = await setupDomainForTest(uniqueName('masked-secret', true), { accessToken, waitForStart: true });
    domain = setupResult.domain;
    const client = new AutomationClient(accessToken);
    const domainRef = `id:${domain.id}`;
    const idpKey = uniqueName('masked-idp', true).toLowerCase();

    const definition = buildInlineIdpDef({
      key: idpKey,
      users: [{ username: MASKED_SECRET_TEST.USERNAME, password: MASKED_SECRET_TEST.PASSWORD }],
    });
    expect((await client.putIdentity(domainRef, definition)).status).toBe(200);
    const idp = (await getAllIdps(domain.id, accessToken)).find((candidate) => candidate.name === definition.name);
    expect(idp?.id).toEqual(expect.any(String));

    const application = await waitForSyncAfter(domain.id, () =>
      createTestApp(uniqueName('masked-app', true), domain, accessToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: [MASKED_SECRET_TEST.REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: { skipConsent: true },
        },
        identityProviders: new Set([{ identity: idp.id, priority: 0 }]),
      }),
    );

    const applyMaskedIdentityProvider = async () => {
      const { body } = await client.getIdentity(domainRef, idpKey);
      return waitForSyncAfter(domain.id, () =>
        client.putIdentity(domainRef, { key: idpKey, name: body.name, type: body.type, configuration: body.configuration }),
      );
    };

    return {
      accessToken,
      domain,
      openIdConfiguration: setupResult.oidcConfig,
      application,
      applyMaskedIdentityProvider,
      cleanUp: async () => {
        await safeDeleteDomain(domain?.id, accessToken);
      },
    };
  } catch (error) {
    await safeDeleteDomain(domain?.id, accessToken);
    throw error;
  }
};
