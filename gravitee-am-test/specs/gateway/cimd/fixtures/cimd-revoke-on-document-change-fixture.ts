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
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { getDefaultIdp } from '@management-commands/idp-management-commands';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import {
  buildCreateAndTestUser,
  listUserAuthorizationDetailConsents,
  revokeUserAuthorizationDetailConsents,
} from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { introspectToken, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { login } from '@gateway-commands/login-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { Application } from '@management-models/Application';
import { AuthorizationDetailApprovalEntity } from '@management-models/AuthorizationDetailApprovalEntity';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';
import { CIMD_REDIRECT_URI } from './cimd-authorize-fixture';
import { AUTHORIZATION_DETAILS_TEST, toDetailsParam } from '../../authorization-details/fixtures/authorization-details-fixture';
import {
  Step,
  follow,
  isConsentPage,
  mergeCookies,
  openConsentPage,
  submitConsent,
} from '../../authorization-details/fixtures/authorization-details-consent-fixture';

export const CIMD_REVOKE_ON_CHANGE_SCENARIO = 'revoke-on-change';

/** WireMock scenario of the {@link CIMD_REVOKE_ON_CHANGE_SCENARIO} document, which moves to `Changed` on its first fetch. */
export const CIMD_REVOKE_ON_CHANGE_WIREMOCK_SCENARIO = 'cimd-revoke-on-change';

/** WireMock scenario of the document whose `Changed` state drops `account_information` from `authorization_details_types`. */
export const CIMD_AUTHORIZATION_DETAILS_REVOKE_SCENARIO = 'cimd-authorization-details-revoke';

export interface CimdRevokeOnDocumentChangeFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  oidc: DomainOidcConfig;
  templateApplication: Application;
  preRegisteredApplication: Application;
  clientId: string;
  /** Client of {@link CIMD_AUTHORIZATION_DETAILS_REVOKE_SCENARIO}. */
  authorizationDetailsClientId: string;
  redirectUri: string;
  user: { username: string; password: string; id: string };
  introspectToken: (token: string) => Promise<{ active: boolean }>;
  /** Sends an authorization request, which fetches the client's document once the cache has expired. */
  authorize: (clientId: string, params?: Record<string, string>) => Promise<any>;
  setSkipConsent: (skipConsent: boolean) => Promise<void>;
  /** Signs the user in, approves every consent screen and returns the authorization code. */
  approveAuthorizationDetails: (clientId: string, details: unknown[]) => Promise<string>;
  /** Exchanges an authorization code for an access token. */
  exchangeCode: (clientId: string, code: string) => Promise<string>;
  authorizationDetailConsents: (clientId: string) => Promise<AuthorizationDetailApprovalEntity[]>;
  revokeAuthorizationDetailConsents: (clientId: string) => Promise<void>;
}

export const setupCimdRevokeOnDocumentChangeFixture = async (): Promise<CimdRevokeOnDocumentChangeFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(
      accessToken,
      uniqueName('cimd-revoke-doc-change', true),
      'CIMD revoke on document change',
    );
    expect(domain.id).toBeDefined();

    const defaultIdp = await getDefaultIdp(domain.id, accessToken);

    const user = await buildCreateAndTestUser(domain.id, accessToken, 0);
    expect(user.username).toBeDefined();
    expect(user.id).toBeDefined();
    const userPassword = user.password;
    expect(userPassword).toBeDefined();

    const createdTemplate = await createApplication(domain.id, accessToken, {
      name: uniqueName('cimd-template-revoke', true),
      type: 'WEB',
      redirectUris: [CIMD_REDIRECT_URI],
    });

    await createAuthorizationDetailType(domain.id, accessToken, {
      type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
      name: 'Payment initiation',
    });
    await createAuthorizationDetailType(domain.id, accessToken, {
      type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE,
      name: 'Account information',
    });

    const templateApplication = await updateApplication(
      domain.id,
      accessToken,
      {
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
        settings: {
          oauth: {
            redirectUris: [CIMD_REDIRECT_URI],
            grantTypes: ['authorization_code'],
            responseTypes: ['code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
            authorizationDetailTypes: [
              { type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE },
              { type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE },
            ],
          },
          advanced: { skipConsent: true },
        },
      },
      createdTemplate.id,
    );

    await patchApplication(domain.id, accessToken, { template: true }, templateApplication.id);

    const createdPreRegistered = await createApplication(domain.id, accessToken, {
      name: uniqueName('cimd-pre-registered-revoke', true),
      type: 'WEB',
      redirectUris: [CIMD_REDIRECT_URI],
    });

    const preRegisteredApplication = await updateApplication(
      domain.id,
      accessToken,
      {
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
        settings: {
          oauth: {
            redirectUris: [CIMD_REDIRECT_URI],
            grantTypes: ['authorization_code'],
            responseTypes: ['code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
      },
      createdPreRegistered.id,
    );
    preRegisteredApplication.settings.oauth.clientSecret = createdPreRegistered.settings.oauth.clientSecret;

    await patchDomain(domain.id, accessToken, {
      oidc: {
        cimdSettings: {
          enabled: true,
          allowUnsecuredHttpUri: true,
          allowPrivateIpAddress: true,
          allowedDomains: [],
          fetchTimeoutMs: 1500,
          maxResponseSizeKb: 10,
          cacheTtlSeconds: 1,
          cacheMaxEntries: 500,
          revokeOnDocumentChange: true,
          templateId: templateApplication.id,
        },
      },
    });

    await startDomain(domain.id, accessToken);
    const startedDomain = await waitForDomainStart(domain);

    const clientId = `http://wiremock:8080/cimd/ENABLED_BASE/${CIMD_REVOKE_ON_CHANGE_SCENARIO}`;
    const authorizationDetailsClientId = 'http://wiremock:8080/cimd/ENABLED_BASE/authorization-details-revoke';
    const domainId = domain.id;
    const token = accessToken;
    const oidc = startedDomain.oidcConfig;

    const authorize = (requestClientId: string, params: Record<string, string> = {}) => {
      const query = new URLSearchParams({
        response_type: 'code',
        client_id: requestClientId,
        redirect_uri: CIMD_REDIRECT_URI,
        scope: 'openid',
        state: 'cimd-revoke',
        ...params,
      });
      return performGet(`${oidc.authorization_endpoint}?${query.toString()}`);
    };

    const approveAuthorizationDetails = async (requestClientId: string, details: unknown[]): Promise<string> => {
      const authResponse = await authorize(requestClientId, { authorization_details: toDetailsParam(...details) }).expect(302);
      const postLogin = await login(authResponse, user.username, requestClientId, userPassword);

      let step: Step = await follow({ response: postLogin, cookie: mergeCookies(postLogin.headers['set-cookie']) });
      let approvedDetails = false;
      while (!step.response.headers['location'].startsWith(CIMD_REDIRECT_URI)) {
        if (isConsentPage(step)) {
          const page = await openConsentPage(step);
          approvedDetails ||= page.kind === 'authorization-details';
          step = await submitConsent(page, true, page.scopes);
        } else {
          step = await follow(step);
        }
      }
      expect(approvedDetails).toBe(true);
      const code = new URL(step.response.headers['location']).searchParams.get('code');
      expect(code).toBeTruthy();
      return code;
    };

    const exchangeCode = async (requestClientId: string, code: string): Promise<string> => {
      const response = await performPost(
        oidc.token_endpoint,
        '',
        new URLSearchParams({
          grant_type: 'authorization_code',
          code,
          redirect_uri: CIMD_REDIRECT_URI,
          client_id: requestClientId,
        }).toString(),
        { 'Content-Type': 'application/x-www-form-urlencoded' },
      ).expect(200);
      expect(response.body.access_token).toMatch(JWT_FORMAT);
      return response.body.access_token;
    };

    return {
      accessToken,
      domain: startedDomain.domain,
      oidc: startedDomain.oidcConfig,
      templateApplication,
      preRegisteredApplication,
      clientId,
      authorizationDetailsClientId,
      redirectUri: CIMD_REDIRECT_URI,
      user: { username: user.username, password: userPassword as string, id: user.id },
      introspectToken: (token) => introspectToken(oidc.introspection_endpoint, token, applicationBase64Token(preRegisteredApplication)),
      authorize,
      setSkipConsent: async (skipConsent) => {
        await waitForSyncAfter(domainId, () =>
          patchApplication(domainId, token, { settings: { advanced: { skipConsent } } }, templateApplication.id),
        );
      },
      approveAuthorizationDetails,
      exchangeCode,
      revokeAuthorizationDetailConsents: (requestClientId) =>
        revokeUserAuthorizationDetailConsents(domainId, token, user.id, requestClientId),
      authorizationDetailConsents: async (requestClientId) =>
        (await listUserAuthorizationDetailConsents(domainId, token, user.id, { clientId: requestClientId })).data,
      cleanUp: async () => {
        await safeDeleteDomain(domain?.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    throw error;
  }
};
