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
import { Fixture } from '../../../test-fixture';
import { createMfaApplication } from './mfa-flow-helpers';

/** Fixed code configured on the mock MFA resource — used by verifyFactor. */
export const CALL_MOCK_CODE = '123456';

export interface CallFactorFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  callFactor: any;
}

/** Call factor fixture using mock-mfa-am-resource instead of Twilio. */
export const setupCallFactorFixture = async (): Promise<CallFactorFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    domain = await createDomain(accessToken, uniqueName('call-factor', true), 'Call factor test domain');

    const mockMfaResource = await createResource(domain.id, accessToken, {
      type: 'mock-mfa-am-resource',
      name: uniqueName('call-mock-resource', true),
      configuration: JSON.stringify({ code: CALL_MOCK_CODE }),
    });

    const callFactor = await createFactor(domain.id, accessToken, {
      type: 'call-am-factor',
      factorType: 'CALL',
      name: uniqueName('call-factor', true),
      configuration: JSON.stringify({
        graviteeResource: mockMfaResource.id,
        countryCodes: 'fr,us',
      }),
    });

    const application = await createMfaApplication(domain.id, accessToken, callFactor.id, 'call-factor');

    // Resources/factors must exist before startDomain (create-before-start pattern)
    const startedDomain = await startDomain(domain.id, accessToken);
    const domainWithOidc = await waitForDomainStart(startedDomain);

    return {
      domain: domainWithOidc.domain,
      accessToken,
      oidc: domainWithOidc.oidcConfig,
      application,
      callFactor,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
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
