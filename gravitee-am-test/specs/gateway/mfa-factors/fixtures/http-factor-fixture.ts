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
import { Application } from '@management-models/Application';
import {
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  DomainOidcConfig,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createResource } from '@management-commands/resource-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import crossFetch from 'cross-fetch';
import { Fixture } from '../../../test-fixture';
import { createMfaApplication } from './mfa-flow-helpers';

export interface HttpFactorFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  httpFactor: any;
}

/** Sets up HTTP factor with WireMock echo stubs, linked resource, and MFA-enabled app. */
export const setupHttpFactorFixture = async (): Promise<HttpFactorFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    domain = await createDomain(accessToken, uniqueName('http-factor', true), 'HTTP factor test domain');

    await registerWireMockStub('/http-factor/send');
    await registerWireMockStub('/http-factor/verify');

    const httpResource = await createResource(domain.id, accessToken, {
      name: uniqueName('http-factor-resource', true),
      type: 'http-factor-am-resource',
      configuration: JSON.stringify({
        baseURL: process.env.INTERNAL_SFR_URL || 'http://wiremock:8080',
        useSystemProxy: false,
        connectTimeout: 10000,
        maxPoolSize: 100,
        sendVerificationCodeConfiguration: {
          endPoint: '/http-factor/send',
          httpMethod: 'POST',
          headers: [{ name: 'Content-Type', value: 'application/json' }],
          body: '{"phone":"test-phone"}',
          httpResponseErrorConditions: [],
        },
        checkVerificationCodeConfiguration: {
          endPoint: '/http-factor/verify',
          httpMethod: 'POST',
          headers: [{ name: 'Content-Type', value: 'application/json' }],
          body: '{"code":"{context.attributes[\'mfa_challenge\']}"}',
          httpResponseErrorConditions: [
            {
              value: "{#response.status == 400}",
              exception: 'io.gravitee.am.common.exception.mfa.InvalidCodeException',
              message: 'Invalid verification code',
            },
          ],
        },
      }),
    });

    const httpFactor = await createFactor(domain.id, accessToken, {
      type: 'http-am-factor',
      factorType: 'HTTP',
      name: uniqueName('http-factor', true),
      configuration: JSON.stringify({
        graviteeResource: httpResource.id,
      }),
    });

    const application = await createMfaApplication(domain.id, accessToken, httpFactor.id, 'http-factor');

    const startedDomain = await startDomain(domain.id, accessToken);
    const domainWithOidc = await waitForDomainStart(startedDomain);

    return {
      domain: domainWithOidc.domain,
      accessToken,
      oidc: domainWithOidc.oidcConfig,
      application,
      httpFactor,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
        // Clear WireMock request log to avoid cross-test interference
        const wiremockUrl = process.env.SFR_URL || 'http://localhost:8181';
        try {
          await crossFetch(`${wiremockUrl}/__admin/requests`, { method: 'DELETE' });
        } catch {
          // WireMock cleanup is best-effort
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Cleanup failed:', e);
      }
    }
    throw error;
  }
};

/** Registers a WireMock echo stub for the given URL path. */
async function registerWireMockStub(urlPath: string): Promise<void> {
  const wiremockAdminUrl = (process.env.SFR_URL || 'http://localhost:8181') + '/__admin/mappings';
  await crossFetch(wiremockAdminUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      request: {
        method: 'POST',
        url: urlPath,
      },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: '{{{request.body}}}',
        transformers: ['response-template'],
      },
    }),
  });
}
